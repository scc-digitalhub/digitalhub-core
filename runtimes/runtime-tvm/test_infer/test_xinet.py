#!/usr/bin/env python3
"""Inferenza end-to-end per il modello xinet (YOLOv8-pose, output [1,56,1029]) servito da un serve TVM di CORE (plumbing condiviso in _client.py)."""
import argparse, json, os, sys, time, urllib.request
import numpy as np

from _client import NP_DTYPE, discover, port_forward, get_image, infer_rest, infer_grpc

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_IMAGE_URL = "https://ultralytics.com/images/bus.jpg"
DEFAULT_FUNCTION = "xinet-function"


def make_input(meta_input, image_arg, image_url):
    """Costruisce il tensore d'ingresso dai metadata; per un'immagine ([1,C,H,W] float) fa resize PIENO a WxH (stretch, NO letterbox), RGB, /255, CHW, altrimenti un tensore deterministico (seed 0). Ritorna anche l'immagine originale full-res + (W,H) per riproiettare i keypoint (None per input non-immagine)."""
    name = meta_input["name"]
    shape = [int(x) for x in meta_input["shape"]]
    dt = meta_input.get("datatype", "FP32")
    np_dt = NP_DTYPE.get(dt, np.float32)
    is_image = len(shape) == 4 and shape[0] == 1 and shape[1] in (1, 3) and dt.startswith("FP")

    if is_image:
        _, C, H, W = shape
        img_path = get_image(image_arg, image_url)
        from PIL import Image
        orig = Image.open(img_path).convert("L" if C == 1 else "RGB")
        # xinet: resize PIENO a WxH (stretch, NO letterbox) come tvm-edge/plugins/xinet-pose.
        rs = orig.resize((W, H), Image.BILINEAR)
        arr = np.asarray(rs, np.float32) / 255.0            # HxW (C=1) oppure HxWxC (RGB)
        arr = arr[None, ...] if C == 1 else np.transpose(arr, (2, 0, 1))  # -> CHW
        data = arr.reshape(-1).astype(np.float32)
        print(f"input:     immagine {img_path} ({orig.size[0]}x{orig.size[1]}) -> resize {W}x{H}, CHW, /255 (RGB)")
        # orig full-res + size modello servono a riproiettare i keypoint (224 -> full-res).
        return name, shape, dt, data, orig.convert("RGB"), (W, H)

    # input non-immagine: tensore deterministico riproducibile
    n = int(np.prod(shape)) if shape else 1
    rng = np.random.default_rng(0)
    if np.issubdtype(np_dt, np.floating):
        data = rng.random(n, dtype=np.float64).astype(np_dt)
    elif np_dt == np.bool_:
        data = (rng.random(n) > 0.5)
    else:
        data = rng.integers(0, 10, size=n).astype(np_dt)
    print(f"input:     tensore deterministico {shape} {dt} (seed=0)")
    return name, shape, dt, data.reshape(-1), None, None


def validate_and_report(out_flat, out_shape, out_dt, meta_out, out_path):
    exp = [int(x) for x in (meta_out.get("shape") or [])] if meta_out else None
    got = [int(x) for x in out_shape]
    print(f"\noutput:    {got} {out_dt}  (atteso dai metadata: {exp})")
    if exp is not None and got != exp:
        sys.exit(f"ERRORE: shape output {got} != shape dai metadata {exp}")
    if exp is not None and int(np.prod(exp)) != out_flat.size:
        sys.exit(f"ERRORE: {out_flat.size} valori != {int(np.prod(exp))} attesi da {exp}")
    print("OK: shape output coerente con i metadata del modello")

    f = out_flat.astype(np.float64)
    print("statistiche output: "
          f"min={f.min():.4f}  max={f.max():.4f}  mean={f.mean():.4f}  std={f.std():.4f}")
    print("primi valori:      ", np.array2string(out_flat[:8], precision=4, separator=", "))
    finite = np.isfinite(f)
    if not finite.all():
        print(f"ATTENZIONE: {(~finite).sum()} valori non finiti (NaN/Inf) nell'output")

    # Se l'output ha forma d'immagine, lo salviamo per un riscontro visivo.
    if len(got) == 4 and got[0] == 1 and got[1] in (1, 3):
        _, C, H, W = got
        from PIL import Image
        img = out_flat.reshape(C, H, W).astype(np.float32)
        mn, mx = float(img.min()), float(img.max())
        img = (img - mn) / (mx - mn + 1e-9)                 # normalizza in [0,1]
        img = (img * 255).clip(0, 255).astype(np.uint8)
        if C == 1:
            Image.fromarray(img[0], "L").save(out_path)
        else:
            Image.fromarray(np.transpose(img, (1, 2, 0)), "RGB").save(out_path)
        print(f"output a forma d'immagine: salvato in {out_path}")
    else:
        print("output non a forma d'immagine: nessuna immagine salvata (solo statistiche).")


# scheletro COCO-17: 19 limb, come xinet-pose (tvm-edge/plugins/xinet-pose/pose_postprocess.py).
SKELETON = [(15, 13), (13, 11), (16, 14), (14, 12), (11, 12), (5, 11), (6, 12),
            (5, 6), (5, 7), (6, 8), (7, 9), (8, 10), (1, 2), (0, 1), (0, 2),
            (1, 3), (2, 4), (3, 5), (4, 6)]
# un colore per limb (palette "jet", RGB; il riferimento è in BGR).
LIMB_COLORS = [(0, 0, 128), (0, 0, 180), (0, 0, 230), (0, 40, 255), (0, 100, 255),
               (0, 160, 255), (0, 220, 220), (0, 255, 160), (40, 255, 80), (120, 255, 0),
               (200, 255, 0), (255, 220, 0), (255, 160, 0), (255, 100, 0), (255, 40, 0),
               (255, 0, 40), (220, 0, 120), (160, 0, 180), (100, 0, 220)]


def _sigmoid(x):
    return 1.0 / (1.0 + np.exp(-np.clip(x, -30, 30)))


def _nms(xyxy, scores, iou_th=0.45):
    """NMS greedy: ritorna gli indici tenuti, in ordine di confidenza."""
    order = scores.argsort()[::-1]
    keep = []
    while order.size:
        i = order[0]; keep.append(int(i))
        if order.size == 1:
            break
        rest = order[1:]
        xx1 = np.maximum(xyxy[i, 0], xyxy[rest, 0]); yy1 = np.maximum(xyxy[i, 1], xyxy[rest, 1])
        xx2 = np.minimum(xyxy[i, 2], xyxy[rest, 2]); yy2 = np.minimum(xyxy[i, 3], xyxy[rest, 3])
        inter = np.clip(xx2 - xx1, 0, None) * np.clip(yy2 - yy1, 0, None)
        area_i = (xyxy[i, 2] - xyxy[i, 0]) * (xyxy[i, 3] - xyxy[i, 1])
        area_r = (xyxy[rest, 2] - xyxy[rest, 0]) * (xyxy[rest, 3] - xyxy[rest, 1])
        iou = inter / (area_i + area_r - inter + 1e-9)
        order = rest[iou <= iou_th]
    return keep


def decode_pose(out_flat, out_shape, orig_img, in_wh, conf_th, kp_th, out_path):
    """Decodifica l'output YOLOv8-pose [1,4+1+K*3,N] e disegna gli scheletri sull'immagine full-res. Il box serve solo per l'NMS (non si disegna); i keypoint escono in coord WxH del modello e si riproiettano con (w0/W, h0/H). conf/vis in [0,1] (sigmoide difensiva se logit)."""
    from PIL import ImageDraw
    _, C, N = out_shape
    K = (C - 5) // 3
    m = out_flat.reshape(C, N).T.astype(np.float32)          # [N, C]
    box, conf = m[:, :4], m[:, 4]
    kpts = m[:, 5:5 + K * 3].reshape(N, K, 3).copy()
    if conf.min() < 0.0 or conf.max() > 1.0:
        conf = _sigmoid(conf)
        kpts[:, :, 2] = _sigmoid(kpts[:, :, 2])

    keep = conf > conf_th
    box, conf, kpts = box[keep], conf[keep], kpts[keep]
    if len(box) == 0:
        print(f"\npose: nessuna persona sopra conf>{conf_th} (prova --conf più basso)")
        return
    # box (cx,cy,w,h)->(x1,y1,x2,y2) SOLO per NMS in spazio 224 (IoU è scale-invariant)
    xyxy = np.stack([box[:, 0] - box[:, 2] / 2, box[:, 1] - box[:, 3] / 2,
                     box[:, 0] + box[:, 2] / 2, box[:, 1] + box[:, 3] / 2], axis=1)
    idx = _nms(xyxy, conf, 0.45)
    conf, kpts = conf[idx], kpts[idx]

    # riproiezione keypoint dal 224 (in_wh) al full-res dell'immagine originale
    W, H = in_wh
    w0, h0 = orig_img.size
    kpts[:, :, 0] *= w0 / W
    kpts[:, :, 1] *= h0 / H
    line_thk = max(2, int(min(h0, w0) / 200))        # spessori scalati sull'immagine grande
    dot_r = max(3, int(min(h0, w0) / 150))

    print(f"\npose (YOLOv8-pose, K={K} keypoint COCO): {len(conf)} persone "
          f"(conf>{conf_th}, post-NMS) — scheletri su immagine {w0}x{h0}")
    im = orig_img.copy()
    d = ImageDraw.Draw(im)
    for i in range(len(conf)):
        kp = kpts[i]
        # segmenti (membra): linea colorata per limb se entrambi i giunti superano kp_th
        seg = 0
        for li, (a_, b_) in enumerate(SKELETON):
            if kp[a_, 2] > kp_th and kp[b_, 2] > kp_th:
                d.line([kp[a_, 0], kp[a_, 1], kp[b_, 0], kp[b_, 1]],
                       fill=LIMB_COLORS[li % len(LIMB_COLORS)], width=line_thk)
                seg += 1
        # giunti: pallino nero su ogni keypoint valido (come il riferimento)
        pts = 0
        for j in range(K):
            if kp[j, 2] > kp_th:
                d.ellipse([kp[j, 0] - dot_r, kp[j, 1] - dot_r, kp[j, 0] + dot_r, kp[j, 1] + dot_r],
                          fill=(0, 0, 0))
                pts += 1
        print(f"  persona {i}: conf={conf[i]:.2f}  segmenti={seg}/{len(SKELETON)}  giunti={pts}/{K}")
    im.save(out_path)
    print(f"immagine annotata full-res (scheletri colorati, senza box): {out_path}")


def main():
    ap = argparse.ArgumentParser(description="Inference E2E su un serve TVM 'xinet' (YOLOv8-pose) di CORE")
    ap.add_argument("--core", default="http://localhost:8080")
    ap.add_argument("--project", default="tvm-rust")
    ap.add_argument("--user", default="admin"); ap.add_argument("--password", default="admin")
    ap.add_argument("--run", help="run id specifico (default: il tvm+serve xinet RUNNING)")
    ap.add_argument("--function", default=DEFAULT_FUNCTION, help="function da preferire fra i serve RUNNING")
    ap.add_argument("--mode", choices=["rest", "grpc"], default="rest")
    ap.add_argument("--image", help="path locale o URL dell'immagine di test (default: bus.jpg)")
    ap.add_argument("--image-url", default=DEFAULT_IMAGE_URL)
    ap.add_argument("--ns", default="default")
    ap.add_argument("--conf", type=float, default=0.2, help="soglia confidenza persona (pose)")
    ap.add_argument("--kp-conf", dest="kp_conf", type=float, default=0.2,
                    help="soglia visibilità keypoint per disegnare segmento/giunto (pose)")
    ap.add_argument("--out", default=os.path.join(HERE, "xinet_out.png"),
                    help="immagine annotata (pose) o output SE ha forma d'immagine")
    a = ap.parse_args()

    svc, model = discover(a.core, a.project, a.user, a.password, run_id=a.run, want_fn=a.function)

    # shape/dtype input e output dai metadata /v2/models (questo test li richiede).
    lp_rest = port_forward(a.ns, svc, 8080, http_probe="/v2/health/ready")
    meta = json.loads(urllib.request.urlopen(f"http://127.0.0.1:{lp_rest}/v2/models/{model}", timeout=30).read())
    if not meta.get("inputs"):
        sys.exit(f"il serve (platform '{meta.get('platform')}') non espone i metadata input/output "
                 "via /v2/models: probabilmente il modello è stato pubblicato senza metadata.json "
                 "(questo test li richiede; run_infer.py ha un fallback --input-name/--input-shape).")
    meta_in = meta["inputs"][0]
    meta_out = (meta.get("outputs") or [{}])[0]
    print(f"metadata:  in {meta_in['name']} {meta_in.get('datatype')} {meta_in['shape']}  ->  "
          f"out {meta_out.get('name')} {meta_out.get('datatype')} {meta_out.get('shape')}")

    name, shape, dt, data, orig_img, in_wh = make_input(meta_in, a.image, a.image_url)

    print(f"\n== inferenza via {a.mode.upper()} ==")
    t = time.time()
    if a.mode == "rest":
        out_flat, out_shape, out_dt, params = infer_rest(lp_rest, model, name, shape, dt, data)
    else:
        lp_grpc = port_forward(a.ns, svc, 9000)
        out_flat, out_shape, out_dt, params = infer_grpc(lp_grpc, model, name, shape, dt, data)
    print(f"infer: {(time.time()-t)*1000:.0f} ms roundtrip ({params})")

    validate_and_report(out_flat, out_shape, out_dt, meta_out, a.out)

    # output YOLOv8-pose [1, 4+1+K*3, N]: decodifica persone + keypoint e disegna gli scheletri.
    got = [int(x) for x in out_shape]
    if orig_img is not None and len(got) == 3 and got[0] == 1 and got[1] >= 6 and (got[1] - 5) % 3 == 0:
        decode_pose(out_flat, got, orig_img, in_wh, a.conf, a.kp_conf, a.out)


if __name__ == "__main__":
    main()
