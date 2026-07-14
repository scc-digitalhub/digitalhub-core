#!/usr/bin/env python3
"""
Plumbing condiviso da `run_infer.py` e `test_xinet.py`: scoperta del serve
dall'API di CORE, port-forward del Service creato da CORE, download
dell'immagine di test e trasporto OpenInference v2 (REST su :8080 / gRPC su
:9000, con compilazione al volo del proto KServe v2 `grpc_predict_v2.proto`
qui accanto via grpcio-tools).
"""
import atexit, json, os, subprocess, sys, tempfile, time, urllib.request
import numpy as np

HERE = os.path.dirname(os.path.abspath(__file__))
KUBECTL = ["minikube", "kubectl", "--"]

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


def discover(core, project, user, pw, run_id=None, want_fn=None):
    """Trova il run tvm+serve da testare e ne ricava Service + nome modello servito.

    Con run_id si usa quel run; altrimenti fra i tvm+serve:run RUNNING si preferisce
    quello della function `want_fn` (se indicata), con fallback al primo RUNNING.
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
            sys.exit("nessun tvm+serve:run in stato RUNNING (lancialo dalla console, poi riprova)")
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


# ---------- immagine di test ----------
def get_image(image_arg, url):
    if image_arg and os.path.isfile(image_arg):
        return image_arg
    src = image_arg if (image_arg and image_arg.startswith("http")) else url
    dst = os.path.join(HERE, "input.jpg")
    print(f"scarico immagine: {src}")
    urllib.request.urlretrieve(src, dst)
    return dst


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
    # Alziamo i limiti client dei messaggi: i tensori v2 superano il default gRPC
    # di 4MB (una richiesta 1x3x640x640 FP32 è da sola ~4.9MB).
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
