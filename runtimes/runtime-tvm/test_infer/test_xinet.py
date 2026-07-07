#!/usr/bin/env python3
"""
Test di inferenza END-TO-END per il modello "xinet" servito da un serve TVM di CORE.

Gemello di run_infer.py (specifico per YOLOv8-detect): stessa scoperta del serve
dall'API di CORE, stesso port-forward del Service creato da CORE, stessa API
OpenInference v2 (REST su :8080 / gRPC su :9000).

xinet è un modello **YOLOv8-pose** (persona + 17 keypoint COCO): l'output
[1, 56, 1029] è 56 = 4(box) + 1(conf) + 17*3(keypoint x,y,vis) su 1029 anchor
(28²+14²+7² per input 224). Il pre/post-processing segue il client di riferimento
`tvm-edge/plugins/xinet-pose`: input = resize PIENO a 224 (stretch, **no letterbox**),
RGB, /255, NCHW; output decodificato con NMS e disegnato come **scheletro** (19 limb,
un colore per limb) sull'immagine **originale a piena risoluzione** (i keypoint sono
in coord 224 e si riproiettano con w0/224, h0/224). Il box serve solo per l'NMS e non
viene disegnato. Resta anche un sanity check shape/dtype + statistiche; per output
non-pose vale solo quello (con salvataggio se l'output ha forma d'immagine).

I/O del modello (letto a runtime dai metadata /v2/models, qui a titolo di esempio):
  input  : images   FP32  [1, 3, 224, 224]   (immagine, CHW, RGB, /255)
  output : output0  FP32  [1, 56, 1029]       (YOLOv8-pose: 4+1+17*3 x 1029 anchor)

Uso:
  python3 test_xinet.py                          # REST, serve xinet RUNNING auto
  python3 test_xinet.py --mode grpc              # stessa inferenza via gRPC (:9000)
  python3 test_xinet.py --image /path/foto.jpg   # immagine locale
  python3 test_xinet.py --run <run-id> --project tvm-rust --out xinet_out.png

Requisiti: minikube (kubectl), python3 + numpy + Pillow; per --mode grpc anche
grpcio + grpcio-tools (il proto KServe v2 è accanto: grpc_predict_v2.proto).
"""
import argparse, atexit, json, os, subprocess, sys, tempfile, time, urllib.request
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
KUBECTL = ["minikube", "kubectl", "--"]
# Immagine di test di default (qualsiasi immagine va bene per un test di sanità).
DEFAULT_IMAGE_URL = "https://ultralytics.com/images/bus.jpg"
# La function di riferimento: fra i serve RUNNING si preferisce quello di xinet.
DEFAULT_FUNCTION = "xinet-function"

# Mappa datatype OpenInference v2 -> dtype numpy (per costruire/leggere i tensori).
NP_DTYPE = {
    "BOOL": np.bool_, "INT8": np.int8, "INT16": np.int16, "INT32": np.int32,
    "INT64": np.int64, "UINT8": np.uint8, "UINT16": np.uint16, "UINT32": np.uint32,
    "UINT64": np.uint64, "FP16": np.float16, "FP32": np.float32, "FP64": np.float64,
}
# Datatype -> campo "contents" tipizzato del proto InferTensorContents (per gRPC).
GRPC_FIELD = {
    "BOOL": "bool_contents", "INT8": "int_contents", "INT16": "int_contents",
    "INT32": "int_contents", "INT64": "int64_contents", "UINT8": "uint_contents",
    "UINT16": "uint_contents", "UINT32": "uint_contents", "UINT64": "uint64_contents",
    "FP32": "fp32_contents", "FP64": "fp64_contents",
}


# ---------- CORE discovery ----------
def core_get(core, path, user, pw):
    import base64
    req = urllib.request.Request(core.rstrip("/") + path)
    req.add_header("Authorization", "Basic " + base64.b64encode(f"{user}:{pw}".encode()).decode())
    return json.loads(urllib.request.urlopen(req, timeout=30).read())


def _fn_name(run):
    """clean name della function del run (spec.function = tvm://<proj>/<name>:<id>)."""
    fn = (run.get("spec") or {}).get("function", "")
    return fn.split("/")[-1].split(":")[0] if fn else ""


def discover(core, project, user, pw, run_id, want_fn):
    """Trova il run tvm+serve da testare e ne ricava Service + nome modello servito.

    Con --run si usa quel run; altrimenti fra i tvm+serve:run RUNNING si preferisce
    quello della function `want_fn` (xinet), con fallback al primo RUNNING trovato.
    """
    runs = core_get(core, f"/api/v1/-/{project}/runs?size=100", user, pw).get("content", [])
    serves = [r for r in runs if r.get("kind") == "tvm+serve:run"]
    if run_id:
        run = next((r for r in serves if r.get("id") == run_id), None)
        if not run:
            sys.exit(f"run {run_id} non trovato tra i tvm+serve:run del progetto {project}")
    else:
        running = [r for r in serves if (r.get("status") or {}).get("state") == "RUNNING"]
        run = next((r for r in running if _fn_name(r) == want_fn), None) or (running[0] if running else None)
        if not run:
            sys.exit("nessun tvm+serve:run in stato RUNNING (lancia un xinet serve, poi riprova)")
    svc = (run.get("status") or {}).get("service") or {}
    if not svc.get("name"):
        sys.exit(f"il run {run.get('id')} non ha ancora un Service (stato {(run.get('status') or {}).get('state')})")
    # nome modello servito: spec.served_name se presente, altrimenti clean name della function
    model = (run.get("spec") or {}).get("served_name") or _fn_name(run) or "model"
    ports = {p.get("port"): p for p in svc.get("ports", [])}
    print(f"CORE run:  {run.get('id')}  (function {_fn_name(run)}, state {(run.get('status') or {}).get('state')})")
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
    # Il forward è "pronto" solo quando risponde davvero: per HTTP facciamo un probe
    # applicativo (il TCP connect a kubectl passa prima che il tunnel al pod sia su,
    # causando 'Remote end closed connection' sulla prima richiesta).
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


# ---------- preparazione input ----------
def get_image(image_arg, url):
    if image_arg and os.path.isfile(image_arg):
        return image_arg
    src = image_arg if (image_arg and image_arg.startswith("http")) else url
    dst = os.path.join(HERE, "input.jpg")
    print(f"scarico immagine: {src}")
    urllib.request.urlretrieve(src, dst)
    return dst


def make_input(meta_input, image_arg, image_url):
    """Costruisce il tensore d'ingresso a partire dai metadata del modello.

    Se l'input è un'immagine ([1,C,H,W] float, C in {1,3}) fa un resize PIENO a WxH
    (stretch, NO letterbox) come il client di riferimento xinet-pose, RGB, /255,
    layout CHW; altrimenti genera un tensore deterministico (seed fisso). Ritorna
    (name, shape, datatype, data_flat, img_originale_full_res, (W,H)): l'immagine
    originale e la size del modello servono a riproiettare e disegnare i keypoint a
    piena risoluzione. Per input non-immagine gli ultimi due sono None.
    """
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
        # xinet: resize PIENO a WxH (stretch, NO letterbox), RGB, /255, CHW — è così
        # che il modello è stato allenato/servito (vedi tvm-edge/plugins/xinet-pose).
        rs = orig.resize((W, H), Image.BILINEAR)
        arr = np.asarray(rs, np.float32) / 255.0            # HxW (C=1) oppure HxWxC (RGB)
        arr = arr[None, ...] if C == 1 else np.transpose(arr, (2, 0, 1))  # -> CHW
        data = arr.reshape(-1).astype(np.float32)
        print(f"input:     immagine {img_path} ({orig.size[0]}x{orig.size[1]}) -> resize {W}x{H}, CHW, /255 (RGB)")
        # ritorniamo l'immagine ORIGINALE full-res + la size del modello (per riproiettare
        # i keypoint dal 224 al full-res e disegnarci sopra a piena risoluzione).
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


# ---------- transport (OpenInference v2) ----------
def infer_rest(local, model, name, shape, datatype, data):
    body = json.dumps({"inputs": [{"name": name, "shape": [int(s) for s in shape],
                                   "datatype": datatype, "data": data.tolist()}]}).encode()
    req = urllib.request.Request(f"http://127.0.0.1:{local}/v2/models/{model}/infer",
                                 data=body, headers={"Content-Type": "application/json"})
    res = json.loads(urllib.request.urlopen(req, timeout=300).read())
    o = res["outputs"][0]
    np_dt = NP_DTYPE.get(o.get("datatype", "FP32"), np.float32)
    return np.array(o["data"], np_dt), o["shape"], o.get("datatype"), res.get("parameters")


def infer_grpc(local, model, name, shape, datatype, data):
    import grpc
    from grpc_tools import protoc
    out = tempfile.mkdtemp()
    if protoc.main(["", f"-I{HERE}", f"--python_out={out}", f"--grpc_python_out={out}",
                    "grpc_predict_v2.proto"]) != 0:
        sys.exit("compilazione proto (grpc_tools) fallita")
    sys.path.insert(0, out)
    import grpc_predict_v2_pb2 as pb
    import grpc_predict_v2_pb2_grpc as pbg
    # Alziamo i limiti dei messaggi: i tensori v2 superano il default gRPC di 4MB
    # (un input 1x3x224x224 FP32 è ~600KB, ma manteniamo margine come in run_infer).
    ch = grpc.insecure_channel(f"127.0.0.1:{local}", options=[
        ("grpc.max_send_message_length", 512 * 1024 * 1024),
        ("grpc.max_receive_message_length", 512 * 1024 * 1024),
    ])
    stub = pbg.GRPCInferenceServiceStub(ch)
    req = pb.ModelInferRequest(model_name=model)
    t = req.inputs.add(); t.name = name; t.datatype = datatype
    t.shape.extend([int(s) for s in shape])
    field = GRPC_FIELD.get(datatype, "fp32_contents")
    getattr(t.contents, field).extend(data.tolist())
    resp = stub.ModelInfer(req, timeout=300)
    o = resp.outputs[0]
    out_dt = o.datatype or "FP32"
    np_dt = NP_DTYPE.get(out_dt, np.float32)
    if resp.raw_output_contents:
        arr = np.frombuffer(resp.raw_output_contents[0], np_dt)
    else:
        arr = np.array(getattr(o.contents, GRPC_FIELD.get(out_dt, "fp32_contents")), np_dt)
    return arr, list(o.shape), out_dt, None


# ---------- validazione / post-processing (sanity, NON task-specific) ----------
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


# ---------- decode YOLOv8-pose (xinet: [1, 4+1+K*3, N]) ----------
# Scheletro COCO-17: 19 limb (coppie di keypoint) — identico al client di
# riferimento xinet-pose (tvm-edge/plugins/xinet-pose/pose_postprocess.py).
SKELETON = [(15, 13), (13, 11), (16, 14), (14, 12), (11, 12), (5, 11), (6, 12),
            (5, 6), (5, 7), (6, 8), (7, 9), (8, 10), (1, 2), (0, 1), (0, 2),
            (1, 3), (2, 4), (3, 5), (4, 6)]
# Un colore per limb (palette "jet", RGB) — come il riferimento (lì in BGR).
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
    """Decodifica l'output YOLOv8-pose [1, 4+1+K*3, N] e disegna gli scheletri
    sull'immagine ORIGINALE a piena risoluzione (come il client xinet-pose).

    Il box serve solo per l'NMS (dedup degli anchor della stessa persona), NON si
    disegna. I keypoint escono in coordinate 224 (WxH del modello): si riproiettano
    al full-res con (w0/W, h0/H) e si tracciano i 19 segmenti (un colore per limb) +
    i giunti. `conf`/`vis` sono già probabilità in [0,1] (sigmoide difensiva se, per
    altri export, arrivassero come logit).
    """
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

    svc, model = discover(a.core, a.project, a.user, a.password, a.run, a.function)

    # metadata via REST (:8080) per shape/dtype di input e output
    lp_rest = port_forward(a.ns, svc, 8080, http_probe="/v2/health/ready")
    meta = json.loads(urllib.request.urlopen(f"http://127.0.0.1:{lp_rest}/v2/models/{model}", timeout=30).read())
    if not meta.get("inputs"):
        sys.exit(f"il serve (platform '{meta.get('platform')}') non espone i metadata input/output: "
                 "questo test richiede il backend rust (che li espone via /v2/models).")
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

    # xinet ha output in formato YOLOv8-pose ([1, 4+1+K*3, N]): decodifica le persone
    # + i keypoint e disegna box/scheletri sull'immagine (riscontro visivo reale).
    got = [int(x) for x in out_shape]
    if orig_img is not None and len(got) == 3 and got[0] == 1 and got[1] >= 6 and (got[1] - 5) % 3 == 0:
        decode_pose(out_flat, got, orig_img, in_wh, a.conf, a.kp_conf, a.out)


if __name__ == "__main__":
    main()
