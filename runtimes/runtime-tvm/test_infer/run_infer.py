#!/usr/bin/env python3
"""
Test di inferenza END-TO-END contro un serve TVM lanciato da CORE.

Cosa fa:
  1. interroga l'API di CORE e trova il run `tvm+serve:run` in stato RUNNING
     (o quello indicato con --run), ricavando il Service creato da CORE e il
     nome del modello servito (da spec.function);
  2. scarica un'immagine di test (default: bus.jpg di ultralytics) — o usa
     quella passata con --image;
  3. raggiunge il Service (ClusterIP) via `kubectl port-forward` e chiama
     l'inferenza in **REST** (OpenInference v2 /infer) oppure **gRPC**
     (GRPCInferenceService.ModelInfer);
  4. decodifica l'output YOLOv8, applica NMS e salva l'immagine con i
     bounding box.

Uso:
  python3 run_infer.py                          # REST, run RUNNING auto, bus.jpg
  python3 run_infer.py --mode grpc              # via gRPC (:9000)
  python3 run_infer.py --image /path/foto.jpg   # immagine locale
  python3 run_infer.py --run <run-id> --project tvm-rust --conf 0.3

Requisiti: minikube (kubectl), python3 + numpy + Pillow; per --mode grpc anche
grpcio + grpcio-tools (il proto è accanto a questo script).
"""
import argparse, atexit, json, os, subprocess, sys, tempfile, time, urllib.request
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
KUBECTL = ["minikube", "kubectl", "--"]
DEFAULT_IMAGE_URL = "https://ultralytics.com/images/bus.jpg"
COCO = ("person bicycle car motorcycle airplane bus train truck boat trafficlight firehydrant stopsign "
        "parkingmeter bench bird cat dog horse sheep cow elephant bear zebra giraffe backpack umbrella "
        "handbag tie suitcase frisbee skis snowboard sportsball kite baseballbat baseballglove skateboard "
        "surfboard tennisracket bottle wineglass cup fork knife spoon bowl banana apple sandwich orange "
        "broccoli carrot hotdog pizza donut cake chair couch pottedplant bed diningtable toilet tv laptop "
        "mouse remote keyboard cellphone microwave oven toaster sink refrigerator book clock vase scissors "
        "teddybear hairdrier toothbrush").split()


# ---------- CORE discovery ----------
def core_get(core, path, user, pw):
    import base64
    req = urllib.request.Request(core.rstrip("/") + path)
    req.add_header("Authorization", "Basic " + base64.b64encode(f"{user}:{pw}".encode()).decode())
    return json.loads(urllib.request.urlopen(req, timeout=30).read())


def discover(core, project, user, pw, run_id):
    runs = core_get(core, f"/api/v1/-/{project}/runs?size=100", user, pw).get("content", [])
    serves = [r for r in runs if r.get("kind") == "tvm+serve:run"]
    if run_id:
        run = next((r for r in serves if r.get("id") == run_id), None)
        if not run:
            sys.exit(f"run {run_id} non trovato tra i tvm+serve:run del progetto {project}")
    else:
        run = next((r for r in serves if (r.get("status") or {}).get("state") == "RUNNING"), None)
        if not run:
            sys.exit("nessun tvm+serve:run in stato RUNNING (lancialo dalla console, poi riprova)")
    svc = (run.get("status") or {}).get("service") or {}
    if not svc.get("name"):
        sys.exit(f"il run {run.get('id')} non ha ancora un Service (stato {(run.get('status') or {}).get('state')})")
    # modello servito = clean name della function (spec.function = tvm://<proj>/<name>:<id>)
    fn = (run.get("spec") or {}).get("function", "")
    model = fn.split("/")[-1].split(":")[0] if fn else "model"
    ports = {p.get("port"): p for p in svc.get("ports", [])}
    print(f"CORE run:  {run.get('id')}  (state RUNNING)")
    print(f"service:   {svc['name']}  ({svc.get('type')})  porte {list(ports)}")
    print(f"modello:   {model}")
    return svc["name"], model


# ---------- port-forward del Service creato da CORE ----------
def port_forward(ns, svc, remote_port, http_probe=None):
    import socket
    local = _free_port()
    proc = subprocess.Popen(KUBECTL + ["port-forward", "-n", ns, f"svc/{svc}", f"{local}:{remote_port}"],
                            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    atexit.register(lambda: proc.terminate())
    # Il forward è "pronto" solo quando risponde davvero: per HTTP facciamo un
    # probe applicativo (il TCP connect a kubectl passa prima che il tunnel al pod
    # sia stabilito, causando 'Remote end closed connection' sulla prima richiesta).
    for _ in range(60):
        try:
            if http_probe:
                if urllib.request.urlopen(f"http://127.0.0.1:{local}{http_probe}", timeout=2).status == 200:
                    return local
            else:
                with socket.create_connection(("127.0.0.1", local), timeout=1):
                    time.sleep(0.5)
                    return local
        except Exception:
            time.sleep(0.5)
    sys.exit(f"port-forward su svc/{svc}:{remote_port} non pronto")


def _free_port():
    import socket
    s = socket.socket(); s.bind(("", 0)); p = s.getsockname()[1]; s.close(); return p


# ---------- immagine ----------
def get_image(image_arg, url):
    if image_arg and os.path.isfile(image_arg):
        return image_arg
    src = image_arg if (image_arg and image_arg.startswith("http")) else url
    dst = os.path.join(HERE, "input.jpg")
    print(f"scarico immagine: {src}")
    urllib.request.urlretrieve(src, dst)
    return dst


def letterbox(img, size):
    from PIL import Image
    W, H = img.size
    r = min(size / W, size / H)
    nw, nh = round(W * r), round(H * r)
    canvas = Image.new("RGB", (size, size), (114, 114, 114))
    dx, dy = (size - nw) // 2, (size - nh) // 2
    canvas.paste(img.resize((nw, nh), Image.BILINEAR), (dx, dy))
    return canvas, r, dx, dy


# ---------- transport ----------
def infer_rest(local, model, input_name, shape, data):
    body = json.dumps({"inputs": [{"name": input_name, "shape": shape, "datatype": "FP32",
                                   "data": data.tolist()}]}).encode()
    req = urllib.request.Request(f"http://127.0.0.1:{local}/v2/models/{model}/infer",
                                 data=body, headers={"Content-Type": "application/json"})
    res = json.loads(urllib.request.urlopen(req, timeout=300).read())
    o = res["outputs"][0]
    return np.array(o["data"], np.float32), o["shape"], res.get("parameters")


def infer_grpc(local, model, input_name, shape, data):
    import grpc
    from grpc_tools import protoc
    out = tempfile.mkdtemp()
    if protoc.main(["", f"-I{HERE}", f"--python_out={out}", f"--grpc_python_out={out}",
                    "grpc_predict_v2.proto"]) != 0:
        sys.exit("compilazione proto (grpc_tools) fallita")
    sys.path.insert(0, out)
    import grpc_predict_v2_pb2 as pb
    import grpc_predict_v2_pb2_grpc as pbg
    # Raise the client-side message limits: v2 tensors exceed the 4MB gRPC default
    # (the request alone is ~4.9MB for a 1x3x640x640 FP32 input).
    ch = grpc.insecure_channel(f"127.0.0.1:{local}", options=[
        ("grpc.max_send_message_length", 512 * 1024 * 1024),
        ("grpc.max_receive_message_length", 512 * 1024 * 1024),
    ])
    stub = pbg.GRPCInferenceServiceStub(ch)
    req = pb.ModelInferRequest(model_name=model)
    t = req.inputs.add(); t.name = input_name; t.datatype = "FP32"
    t.shape.extend([int(s) for s in shape]); t.contents.fp32_contents.extend(data.tolist())
    resp = stub.ModelInfer(req, timeout=300)
    o = resp.outputs[0]
    if resp.raw_output_contents:
        arr = np.frombuffer(resp.raw_output_contents[0], np.float32)
    else:
        arr = np.array(o.contents.fp32_contents, np.float32)
    return arr, list(o.shape), None


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

    svc, model = discover(a.core, a.project, a.user, a.password, a.run)

    # metadata via REST (:8080) per shape input/output; alcuni backend (es. Go)
    # non espongono inputs/outputs -> fallback su --input-name/--input-shape.
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
        out_flat, out_shape, params = infer_rest(lp_rest, model, input_name, input_shape, data)
    else:
        lp_grpc = port_forward(a.ns, svc, 9000)
        out_flat, out_shape, params = infer_grpc(lp_grpc, model, input_name, input_shape, data)
    print(f"infer: {(time.time()-t)*1000:.0f} ms roundtrip ({params})  output shape {out_shape}")

    if len(out_shape) == 3 and out_shape[1] == 84:
        decode_and_draw(img_path, out_flat, out_shape, r, dx, dy, a.conf, a.out)
    else:
        print(f"output non-YOLOv8 (shape {out_shape}); primi valori:", out_flat[:5])


if __name__ == "__main__":
    main()
