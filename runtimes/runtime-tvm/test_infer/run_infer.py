#!/usr/bin/env python3
"""
Test di inferenza END-TO-END contro un serve TVM lanciato da CORE.

Cosa fa:
  1. interroga l'API di CORE e trova il run `tvm+serve:run` in stato RUNNING
     (o quello indicato con --run), ricavando il Service creato da CORE e il
     nome del modello servito (spec.served_name o, in mancanza, il clean name
     di spec.function);
  2. scarica un'immagine di test (default: bus.jpg di ultralytics) — o usa
     quella passata con --image;
  3. raggiunge il Service (ClusterIP) via `kubectl port-forward` e chiama
     l'inferenza in **REST** (OpenInference v2 /infer) oppure **gRPC**
     (GRPCInferenceService.ModelInfer);
  4. decodifica l'output YOLOv8, applica NMS e salva l'immagine con i
     bounding box.

Il plumbing (scoperta da CORE, port-forward, trasporto REST/gRPC) è condiviso
con test_xinet.py in `_client.py`, qui accanto.

Uso:
  python3 run_infer.py                          # REST, run RUNNING auto, bus.jpg
  python3 run_infer.py --mode grpc              # via gRPC (:9000)
  python3 run_infer.py --image /path/foto.jpg   # immagine locale
  python3 run_infer.py --run <run-id> --project tvm-rust --conf 0.3

Requisiti: minikube (kubectl), python3 + numpy + Pillow; per --mode grpc anche
grpcio + grpcio-tools (il proto è accanto a questo script).
"""
import argparse, json, os, time, urllib.request
import numpy as np

from _client import discover, port_forward, get_image, infer_rest, infer_grpc

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_IMAGE_URL = "https://ultralytics.com/images/bus.jpg"
COCO = ("person bicycle car motorcycle airplane bus train truck boat trafficlight firehydrant stopsign "
        "parkingmeter bench bird cat dog horse sheep cow elephant bear zebra giraffe backpack umbrella "
        "handbag tie suitcase frisbee skis snowboard sportsball kite baseballbat baseballglove skateboard "
        "surfboard tennisracket bottle wineglass cup fork knife spoon bowl banana apple sandwich orange "
        "broccoli carrot hotdog pizza donut cake chair couch pottedplant bed diningtable toilet tv laptop "
        "mouse remote keyboard cellphone microwave oven toaster sink refrigerator book clock vase scissors "
        "teddybear hairdrier toothbrush").split()


# ---------- preparazione input ----------
def letterbox(img, size):
    from PIL import Image
    W, H = img.size
    r = min(size / W, size / H)
    nw, nh = round(W * r), round(H * r)
    canvas = Image.new("RGB", (size, size), (114, 114, 114))
    dx, dy = (size - nw) // 2, (size - nh) // 2
    canvas.paste(img.resize((nw, nh), Image.BILINEAR), (dx, dy))
    return canvas, r, dx, dy


# ---------- YOLOv8 decode ----------
def decode_and_draw(img_path, out_flat, out_shape, r, dx, dy, conf_thr, out_path):
    from PIL import Image, ImageDraw, ImageFont
    from collections import Counter
    out = out_flat.reshape(out_shape)[0]              # [84, N]
    pred = out.T
    boxes, scores = pred[:, :4], pred[:, 4:]
    cls, conf = scores.argmax(1), scores.max(1)
    m = conf > conf_thr
    boxes, cls, conf = boxes[m], cls[m], conf[m]
    cx, cy, w, h = boxes.T
    xyxy = np.stack([(cx - w / 2 - dx) / r, (cy - h / 2 - dy) / r,
                     (cx + w / 2 - dx) / r, (cy + h / 2 - dy) / r], 1)

    def nms(b, s, thr=0.45):
        idx = s.argsort()[::-1]; keep = []
        while len(idx):
            i = idx[0]; keep.append(i)
            if len(idx) == 1:
                break
            xx1 = np.maximum(b[i, 0], b[idx[1:], 0]); yy1 = np.maximum(b[i, 1], b[idx[1:], 1])
            xx2 = np.minimum(b[i, 2], b[idx[1:], 2]); yy2 = np.minimum(b[i, 3], b[idx[1:], 3])
            inter = np.clip(xx2 - xx1, 0, None) * np.clip(yy2 - yy1, 0, None)
            a = (b[i, 2] - b[i, 0]) * (b[i, 3] - b[i, 1])
            ar = (b[idx[1:], 2] - b[idx[1:], 0]) * (b[idx[1:], 3] - b[idx[1:], 1])
            idx = idx[1:][inter / (a + ar - inter + 1e-9) < thr]
        return keep

    final = []
    for c in np.unique(cls):
        ib = np.where(cls == c)[0]
        final += [ib[k] for k in nms(xyxy[cls == c], conf[cls == c])]

    img = Image.open(img_path).convert("RGB"); draw = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
    except Exception:
        font = ImageFont.load_default()
    summ = Counter()
    print(f"\nrilevamenti (conf>{conf_thr}, post-NMS): {len(final)}")
    for i in sorted(final, key=lambda i: -conf[i]):
        name = COCO[cls[i]] if cls[i] < len(COCO) else str(cls[i]); summ[name] += 1
        x1, y1, x2, y2 = xyxy[i]
        print(f"  {name:12s} conf={conf[i]:.2f}  box=({x1:.0f},{y1:.0f})-({x2:.0f},{y2:.0f})")
        draw.rectangle([x1, y1, x2, y2], outline=(0, 200, 0), width=4)
        draw.text((x1 + 3, max(0, y1 - 24)), f"{name} {conf[i]:.2f}", fill=(0, 200, 0), font=font)
    img.save(out_path, quality=90)
    print("riepilogo:", dict(summ))
    print("immagine annotata:", out_path)


def main():
    ap = argparse.ArgumentParser(description="Inference E2E su un serve TVM lanciato da CORE")
    ap.add_argument("--core", default="http://localhost:8080")
    ap.add_argument("--project", default="tvm-rust")
    ap.add_argument("--user", default="admin"); ap.add_argument("--password", default="admin")
    ap.add_argument("--run", help="run id specifico (default: il tvm+serve RUNNING)")
    ap.add_argument("--mode", choices=["rest", "grpc"], default="rest")
    ap.add_argument("--image", help="path locale o URL (default: bus.jpg)")
    ap.add_argument("--image-url", default=DEFAULT_IMAGE_URL)
    ap.add_argument("--ns", default="default")
    ap.add_argument("--input-name", default="images", help="usato se il serve non espone i metadata")
    ap.add_argument("--input-shape", default="1,3,640,640", help="usato se il serve non espone i metadata")
    ap.add_argument("--conf", type=float, default=0.25)
    ap.add_argument("--out", default=os.path.join(HERE, "detections.jpg"))
    a = ap.parse_args()

    svc, model = discover(a.core, a.project, a.user, a.password, run_id=a.run)

    # metadata via REST (:8080) per shape input/output; entrambi i backend (rust
    # e Go) li espongono via /v2/models leggendoli da metadata.json — il fallback
    # --input-name/--input-shape resta per modelli pubblicati senza metadata.
    lp_rest = port_forward(a.ns, svc, 8080, http_probe="/v2/health/ready")
    meta = json.loads(urllib.request.urlopen(f"http://127.0.0.1:{lp_rest}/v2/models/{model}", timeout=30).read())
    if meta.get("inputs"):
        inp = meta["inputs"][0]
        input_name, input_shape = inp["name"], inp["shape"]
        out_meta = meta.get("outputs", [{}])[0].get("shape")
        print(f"input:     {input_name} {inp.get('datatype')} {input_shape}  ->  output {out_meta}")
    else:
        input_name = a.input_name
        input_shape = [int(x) for x in a.input_shape.split(",")]
        print(f"input:     (serve platform '{meta.get('platform')}' senza metadata) "
              f"uso default {input_name} {input_shape}")
    size = input_shape[-1]

    img_path = get_image(a.image, a.image_url)
    from PIL import Image
    img = Image.open(img_path).convert("RGB")
    canvas, r, dx, dy = letterbox(img, size)
    data = np.transpose(np.asarray(canvas, np.float32) / 255.0, (2, 0, 1)).flatten()

    print(f"\n== inferenza via {a.mode.upper()} ==")
    t = time.time()
    if a.mode == "rest":
        out_flat, out_shape, _, params = infer_rest(lp_rest, model, input_name, input_shape, "FP32", data)
    else:
        lp_grpc = port_forward(a.ns, svc, 9000)
        out_flat, out_shape, _, params = infer_grpc(lp_grpc, model, input_name, input_shape, "FP32", data)
    print(f"infer: {(time.time()-t)*1000:.0f} ms roundtrip ({params})  output shape {out_shape}")

    if len(out_shape) == 3 and out_shape[1] == 84:
        decode_and_draw(img_path, out_flat, out_shape, r, dx, dy, a.conf, a.out)
    else:
        print(f"output non-YOLOv8 (shape {out_shape}); primi valori:", out_flat[:5])


if __name__ == "__main__":
    main()
