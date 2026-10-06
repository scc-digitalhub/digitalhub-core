#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""End-to-end test of runtime-tvm for any ONNX or TFLite model.

  1. uploads the model file to S3 and creates a Model (kind onnx or tflite) and a tvm function;
  2. runs tvm+build, then tvm+compile for every target;
  3. serves every compiled Model with every serve image: in CORE when the cluster has a node
     of its architecture, otherwise it checks that CORE asks for the right node and runs the
     serve image in Docker (QEMU emulation for ARM);
  4. sends the same random input over REST and gRPC and checks that every output matches the
     first result, so all targets, serve images and protocols must agree;
  5. writes results.json and results.md.

  python3 tvm_e2e.py --model-file ~/models/yolov8n.onnx --build-option simplify=true
  python3 tvm_e2e.py --model-file xinet.onnx --function xinet-e2e --targets cpu,armv7l --runtimes go

Needs kubectl on the CORE cluster, Docker, numpy, requests, boto3 and grpcio-tools. The S3
credentials come from AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY.
"""
import argparse
import json
import os
import socket
import subprocess
import sys
import tempfile
import time
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from pathlib import Path

import numpy as np
import requests

HERE = Path(__file__).resolve().parent
TARGETS = ["cpu", "x86", "x86_v3", "x86_native", "arm64", "arm64_pi5", "armv7l"]
IMAGES = {"go": "ghcr.io/scc-digitalhub/tvm-runtime-go:0.26.0", "rust": "ghcr.io/scc-digitalhub/tvm-runtime-rust:0.26.0"}
PLATFORMS = {"amd64": "linux/amd64", "arm64": "linux/arm64", "arm": "linux/arm/v7"}
DTYPES = {"BOOL": np.bool_, "INT8": np.int8, "INT16": np.int16, "INT32": np.int32, "INT64": np.int64,
          "UINT8": np.uint8, "UINT16": np.uint16, "UINT32": np.uint32, "UINT64": np.uint64,
          "FP32": np.float32, "FP64": np.float64}
GRPC_FIELDS = {"BOOL": "bool_contents", "INT8": "int_contents", "INT16": "int_contents", "INT32": "int_contents",
               "INT64": "int64_contents", "UINT8": "uint_contents", "UINT16": "uint_contents",
               "UINT32": "uint_contents", "UINT64": "uint64_contents", "FP32": "fp32_contents", "FP64": "fp64_contents"}


def log(message):
    print(time.strftime("%H:%M:%S"), message, flush=True)


def key_values(pairs):
    """key=value options as a dict, with true/false and numbers converted."""
    result = {}
    for pair in pairs or []:
        key, _, value = pair.partition("=")
        lowered = value.lower()
        result[key] = True if lowered == "true" else False if lowered == "false" else \
            int(value) if value.lstrip("-").isdigit() else value
    return result


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    model = parser.add_argument_group("model")
    model.add_argument("--model-file", required=True, type=Path, help="local .onnx or .tflite file")
    model.add_argument("--function", help="function name (default: the file name)")
    model.add_argument("--project", default="tvm-e2e", help="CORE project, created when missing (default tvm-e2e)")
    model.add_argument("--build-option", action="append", metavar="KEY=VALUE", help="tvm+build option, e.g. simplify=true")
    model.add_argument("--compile-option", action="append", metavar="KEY=VALUE", help="tvm+compile option, e.g. opt_level=2")
    model.add_argument("--targets", default=",".join(TARGETS), help="targets to compile (default: all)")
    model.add_argument("--runtimes", default="go,rust", help="serve images to test: go, rust (default both)")

    platform = parser.add_argument_group("platform")
    platform.add_argument("--core", default="http://localhost:8080", help="CORE address")
    platform.add_argument("--user", default="admin")
    platform.add_argument("--password", default="admin")
    platform.add_argument("--s3-endpoint", default=os.environ.get("S3_ENDPOINT_URL"), help="S3 endpoint (env S3_ENDPOINT_URL)")
    platform.add_argument("--s3-bucket", default="digitalhub")
    platform.add_argument("--go-image", default=IMAGES["go"])
    platform.add_argument("--rust-image", default=IMAGES["rust"])
    platform.add_argument("--no-docker", action="store_true", help="do not run the serve images in Docker")

    test = parser.add_argument_group("test")
    test.add_argument("--parallel", type=int, default=3, help="compiles at the same time (default 3)")
    test.add_argument("--seed", type=int, default=0, help="seed of the random input (default 0)")
    test.add_argument("--rtol", type=float, default=1e-3, help="relative tolerance of the outputs (default 1e-3)")
    test.add_argument("--atol", type=float, default=1e-4, help="absolute tolerance of the outputs (default 1e-4)")
    test.add_argument("--out", type=Path, help="results folder (default output/e2e-<function>)")
    args = parser.parse_args()
    args.function = args.function or args.model_file.stem.replace("_", "-").lower()
    args.out = args.out or HERE / "output" / f"e2e-{args.function}"
    args.targets = [t.strip() for t in args.targets.split(",") if t.strip()]
    args.runtimes = [r.strip() for r in args.runtimes.split(",") if r.strip()]
    args.images = {"go": args.go_image, "rust": args.rust_image}
    return args


# --------------------------------------------------------------------------- CORE


class Core:
    def __init__(self, args):
        self.url, self.auth, self.project = args.core.rstrip("/") + "/api/v1", (args.user, args.password), args.project

    def call(self, method, path, **kwargs):
        response = requests.request(method, f"{self.url}{path}", auth=self.auth, timeout=60, **kwargs)
        if response.status_code >= 400:
            raise RuntimeError(f"{method} {path} -> {response.status_code}: {response.text[:400]}")
        return response.json() if response.text else None

    def scoped(self, method, path, **kwargs):
        return self.call(method, f"/-/{self.project}{path}", **kwargs)

    def task(self, function_key, kind):
        """Key of the function task of this kind, created when missing. Call it before starting
        runs in parallel: two runs that create the same task at once are refused as duplicates."""
        tasks = self.scoped("GET", "/tasks", params={"function": function_key, "size": 100}).get("content", [])
        task = next((t for t in tasks if t["kind"] == kind), None) or \
            self.scoped("POST", "/tasks", json={"project": self.project, "kind": kind, "spec": {"function": function_key}})
        return f"{kind}://{self.project}/{task['id']}"

    def run(self, function_key, kind, spec):
        body = {"project": self.project, "kind": f"{kind}:run",
                "spec": {"function": function_key, "task": self.task(function_key, kind), "local_execution": False, **spec}}
        return self.scoped("POST", "/runs", json=body)["id"]

    def wait(self, run_id, states, minutes):
        deadline = time.time() + minutes * 60
        while time.time() < deadline:
            run = self.scoped("GET", f"/runs/{run_id}")
            state = (run.get("status") or {}).get("state")
            if state in states:
                return run
            if state in ("ERROR", "STOPPED", "DELETED"):
                raise RuntimeError(f"run {run_id} ended {state}: {(run.get('status') or {}).get('message')}")
            time.sleep(5)
        raise RuntimeError(f"run {run_id} not {states} after {minutes} min")

    def model(self, key):
        return self.scoped("GET", f"/models/{key.rsplit(':', 1)[1]}")


def kubectl_json(*args):
    return json.loads(subprocess.run(["kubectl", *args, "-o", "json"], capture_output=True, text=True, check=True).stdout)


def node_architectures():
    return {n["metadata"]["labels"].get("kubernetes.io/arch") for n in kubectl_json("get", "nodes")["items"]}


def architecture_of(triple):
    arch = (triple or "").split("-")[0]
    return "amd64" if arch == "x86_64" else "arm64" if arch == "aarch64" else "arm" if arch.startswith("arm") else None


def free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


# --------------------------------------------------------------------------- inference


class Client:
    """Open Inference v2 over REST and gRPC, with the same random input for every serve."""

    def __init__(self, rest_url, grpc_target, model_name):
        self.rest_url, self.grpc_target, self.model_name = rest_url, grpc_target, model_name

    def wait_ready(self, minutes):
        deadline = time.time() + minutes * 60
        while time.time() < deadline:
            try:
                if requests.get(f"{self.rest_url}/v2/models/{self.model_name}/ready", timeout=5).status_code == 200:
                    return
            except requests.RequestException:
                pass
            time.sleep(3)
        raise RuntimeError(f"the serve at {self.rest_url} is not ready after {minutes} min")

    def inputs(self, seed):
        metadata = requests.get(f"{self.rest_url}/v2/models/{self.model_name}", timeout=30).json()
        rng = np.random.default_rng(seed)
        tensors = []
        for spec in metadata["inputs"]:
            shape = [d if d > 0 else 1 for d in spec["shape"]]
            dtype = DTYPES[spec["datatype"]]
            if np.issubdtype(dtype, np.floating):
                data = rng.random(shape, dtype=np.float64).astype(dtype)
            elif dtype == np.bool_:
                data = rng.integers(0, 2, shape).astype(dtype)
            else:
                info = np.iinfo(dtype)
                data = rng.integers(max(info.min, -128), min(info.max, 127) + 1, shape).astype(dtype)
            tensors.append((spec["name"], spec["datatype"], shape, data))
        return tensors

    def infer_rest(self, tensors):
        body = {"inputs": [{"name": n, "datatype": t, "shape": s, "data": d.reshape(-1).tolist()} for n, t, s, d in tensors]}
        start = time.perf_counter()
        response = requests.post(f"{self.rest_url}/v2/models/{self.model_name}/infer", json=body, timeout=1800)
        milliseconds = (time.perf_counter() - start) * 1000
        response.raise_for_status()
        return {o["name"]: np.asarray(o["data"], DTYPES.get(o.get("datatype", "FP32"))).reshape(o["shape"])
                for o in response.json()["outputs"]}, milliseconds

    def infer_grpc(self, tensors):
        _, messages, stub = grpc_stub(self.grpc_target)
        request = messages.ModelInferRequest(model_name=self.model_name)
        for name, datatype, shape, data in tensors:
            tensor = request.inputs.add()
            tensor.name, tensor.datatype = name, datatype
            tensor.shape.extend(shape)
            getattr(tensor.contents, GRPC_FIELDS[datatype]).extend(data.reshape(-1).tolist())
        start = time.perf_counter()
        response = stub.ModelInfer(request, timeout=1800)
        milliseconds = (time.perf_counter() - start) * 1000
        outputs = {}
        for index, output in enumerate(response.outputs):
            dtype = DTYPES.get(output.datatype or "FP32")
            data = np.frombuffer(response.raw_output_contents[index], dtype) if response.raw_output_contents \
                else np.asarray(getattr(output.contents, GRPC_FIELDS[output.datatype or "FP32"]), dtype)
            outputs[output.name] = data.reshape(list(output.shape))
        return outputs, milliseconds


_GRPC = {}


def grpc_stub(target):
    """Compiles the bundled KServe v2 proto once, then opens one channel per target."""
    if "modules" not in _GRPC:
        import grpc
        from grpc_tools import protoc

        generated = tempfile.mkdtemp(prefix="grpc_v2_")
        if protoc.main(["protoc", f"-I{HERE}", f"--python_out={generated}", f"--grpc_python_out={generated}",
                        str(HERE / "grpc_predict_v2.proto")]):
            raise RuntimeError("compiling grpc_predict_v2.proto failed")
        sys.path.insert(0, generated)
        import grpc_predict_v2_pb2 as messages
        import grpc_predict_v2_pb2_grpc as services

        _GRPC["modules"] = (grpc, messages, services)
    grpc, messages, services = _GRPC["modules"]
    options = [("grpc.max_send_message_length", 512 * 1024 * 1024), ("grpc.max_receive_message_length", 512 * 1024 * 1024)]
    return grpc, messages, services.GRPCInferenceServiceStub(grpc.insecure_channel(target, options=options))


def compare(reference, outputs, rtol, atol):
    """Largest absolute difference from the reference outputs, and whether they match."""
    if set(reference) != set(outputs):
        return False, f"outputs {sorted(outputs)} instead of {sorted(reference)}"
    worst = 0.0
    for name, expected in reference.items():
        actual = outputs[name]
        if actual.shape != expected.shape:
            return False, f"{name} has shape {list(actual.shape)} instead of {list(expected.shape)}"
        if np.issubdtype(expected.dtype, np.floating):
            worst = max(worst, float(np.max(np.abs(actual.astype(np.float64) - expected))) if expected.size else 0.0)
            if not np.allclose(actual, expected, rtol=rtol, atol=atol):
                return False, f"{name} differs by up to {worst:.3g}"
        elif not np.array_equal(actual, expected):
            return False, f"{name} has different values"
    return True, f"max difference {worst:.3g}"


# --------------------------------------------------------------------------- steps


def setup(core, args):
    try:
        core.call("GET", f"/projects/{args.project}")
    except RuntimeError:
        core.call("POST", "/projects", json={"name": args.project, "kind": "project", "spec": {}})
    import boto3

    if not args.s3_endpoint:
        raise SystemExit("set --s3-endpoint (or S3_ENDPOINT_URL) and the AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY env")
    kind = {".onnx": "onnx", ".tflite": "tflite"}.get(args.model_file.suffix.lower())
    if not kind:
        raise SystemExit("--model-file must be a .onnx or .tflite file")
    s3_key = f"{args.project}/sources/{args.function}/{args.model_file.name}"
    boto3.client("s3", endpoint_url=args.s3_endpoint).upload_file(str(args.model_file), args.s3_bucket, s3_key)
    model = core.scoped("POST", "/models", json={"project": args.project, "name": args.function, "kind": kind,
                                                 "spec": {"path": f"s3://{args.s3_bucket}/{s3_key}"}})
    model_key = f"store://{args.project}/model/{kind}/{args.function}:{model['id']}"
    function = core.scoped("POST", "/functions", json={"project": args.project, "name": args.function, "kind": "tvm",
                                                       "spec": {"model": model_key}})
    log(f"Model {model_key}, function {args.function}")
    return f"tvm://{args.project}/{args.function}:{function['id']}"


def build(core, args, function_key):
    started = time.time()
    run_id = core.run(function_key, "tvm+build", {**key_values(args.build_option), "resources": {"cpu": "2", "mem": "4Gi"}})
    run = core.wait(run_id, ("COMPLETED",), 30)
    ir_key = run["status"]["outputs"]["ir_module"]
    log(f"build: {ir_key} in {time.time() - started:.0f}s")
    return {"run": run_id, "ir_key": ir_key, "kind": core.model(ir_key)["kind"], "seconds": round(time.time() - started)}


def compile_target(core, args, function_key, ir_key, target):
    started = time.time()
    spec = {**key_values(args.compile_option), "model_path": ir_key, "target_architecture": target,
            "tag": target.replace("_", "-"), "resources": {"cpu": "4", "mem": "8Gi"}}
    try:
        run = core.wait(core.run(function_key, "tvm+compile", spec), ("COMPLETED",), 60)
    except RuntimeError as error:
        return {"ok": False, "error": str(error)[:400]}
    key = run["status"]["outputs"]["compiled_so"]
    model = core.model(key)
    manifest = model["spec"].get("manifest") or {}
    return {"ok": True, "key": key, "kind": model["kind"], "seconds": round(time.time() - started),
            "target_triple": manifest.get("target_triple"), "benchmark": manifest.get("benchmark")}


@contextmanager
def core_serve(core, function_key, compiled_key, image):
    """A CORE serve of the Model, reached through kubectl port-forward."""
    run_id = core.run(function_key, "tvm+serve", {"model_path": compiled_key, "image": image, "workers": 1,
                                                  "resources": {"cpu": "2", "mem": "2Gi"}})
    forwards = []
    try:
        run = core.wait(run_id, ("RUNNING",), 15)
        service = run["status"]["service"]["name"]
        ports = {8080: free_port(), 9000: free_port()}
        for remote, local in ports.items():
            forwards.append(subprocess.Popen(["kubectl", "port-forward", "-n", run["status"]["service"].get("namespace", "default"),
                                              f"svc/{service}", f"{local}:{remote}"],
                                             stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        yield f"http://127.0.0.1:{ports[8080]}", f"127.0.0.1:{ports[9000]}"
    finally:
        for forward in forwards:
            forward.terminate()
        try:
            core.scoped("POST", f"/runs/{run_id}/stop")
        except RuntimeError:
            pass


def platform_image(image, platform):
    """The image of one platform, pinned by digest. With a plain tag Docker may reuse a local
    copy pulled earlier for another platform of the same multi-architecture image."""
    raw = subprocess.run(["docker", "buildx", "imagetools", "inspect", "--raw", image], capture_output=True, text=True, check=False)
    if raw.returncode != 0:
        return image
    os_name, architecture, *variant = platform.split("/")
    name, _, tag = image.rpartition(":")
    repository = name if name and "/" not in tag else image
    for manifest in json.loads(raw.stdout).get("manifests", []):
        wanted = manifest.get("platform", {})
        if (wanted.get("os"), wanted.get("architecture")) == (os_name, architecture) and \
                (not variant or wanted.get("variant") == variant[0]):
            return f"{repository}@{manifest['digest']}"
    return image


@contextmanager
def docker_serve(args, core, compiled_key, image, platform):
    """The serve image in Docker, with the compiled Model downloaded from S3."""
    import boto3

    folder = args.out / "models" / compiled_key.rsplit("/", 1)[1].split(":")[0]
    folder.mkdir(parents=True, exist_ok=True)
    bucket, prefix = core.model(compiled_key)["spec"]["path"][len("s3://"):].split("/", 1)
    s3 = boto3.client("s3", endpoint_url=args.s3_endpoint)
    for name in ("model.so", "metadata.json"):
        s3.download_file(bucket, f"{prefix.rstrip('/')}/{name}", str(folder / name))
    rest, grpc_port = free_port(), free_port()
    name = f"tvm-e2e-{args.function}-{int(time.time() * 1000)}"
    subprocess.run(["docker", "run", "-d", "--name", name, "--platform", platform, "-p", f"{rest}:8080",
                    "-p", f"{grpc_port}:9000", "-v", f"{folder}:/shared/model:ro", "-e", f"TVM_MODEL_NAME={args.function}",
                    platform_image(image, platform)], check=True, capture_output=True)
    try:
        yield f"http://127.0.0.1:{rest}", f"127.0.0.1:{grpc_port}"
    finally:
        subprocess.run(["docker", "rm", "-f", name], capture_output=True, check=False)


def placement(core, function_key, compiled_key):
    """Serves the Model in CORE only to read the node selector and the scheduling reason."""
    run_id = core.run(function_key, "tvm+serve", {"model_path": compiled_key, "workers": 1})
    try:
        for _ in range(30):
            time.sleep(3)
            pods = [p for p in kubectl_json("get", "pods", "-n", "default")["items"] if run_id in p["metadata"]["name"]]
            conditions = [c for c in (pods[0]["status"].get("conditions") or []) if c["type"] == "PodScheduled"] if pods else []
            if conditions:
                return {"node_selector": pods[0]["spec"].get("nodeSelector"), "scheduled": conditions[0]["status"],
                        "reason": conditions[0].get("message")}
        return {"error": "no pod after 90s"}
    finally:
        requests.delete(f"{core.url}/-/{core.project}/runs/{run_id}", auth=core.auth, timeout=30)


def serve_and_check(core, args, function_key, target, compiled, nodes, reference):
    arch = architecture_of(compiled["target_triple"])
    rows = []
    where = "core" if arch in nodes else "docker"
    placement_check = None if where == "core" else placement(core, function_key, compiled["key"])
    for runtime in args.runtimes:
        image = args.images[runtime]
        row = {"target": target, "runtime": runtime, "where": where if where == "core" else f"docker {PLATFORMS[arch]}"}
        if where == "docker" and args.no_docker:
            rows.append({**row, "skipped": "no node of this architecture and --no-docker"})
            continue
        try:
            context = core_serve(core, function_key, compiled["key"], image) if where == "core" \
                else docker_serve(args, core, compiled["key"], image, PLATFORMS[arch])
            with context as (rest_url, grpc_target):
                client = Client(rest_url, grpc_target, args.function)
                client.wait_ready(15)
                tensors = client.inputs(args.seed)
                for mode in ("rest", "grpc"):
                    outputs, milliseconds = (client.infer_rest if mode == "rest" else client.infer_grpc)(tensors)
                    if not reference:
                        reference.update(outputs)
                        match, detail = True, "reference"
                    else:
                        match, detail = compare(reference, outputs, args.rtol, args.atol)
                    row[mode] = {"ms": round(milliseconds), "match": match, "detail": detail}
        except Exception as error:  # noqa: BLE001 - the other combinations keep running
            row["error"] = str(error)[:400]
        log(f"serve {target} {runtime} ({row['where']}): {json.dumps({k: v for k, v in row.items() if k not in ('target', 'runtime', 'where')})}")
        rows.append(row)
    return rows, placement_check


def write_report(args, results):
    args.out.mkdir(parents=True, exist_ok=True)
    (args.out / "results.json").write_text(json.dumps(results, indent=2, default=str))
    options = (f"Model file: `{args.model_file.name}`, build options: `{key_values(args.build_option)}`, "
               f"compile options: `{key_values(args.compile_option)}`")
    lines = [f"# runtime-tvm end-to-end: {args.function}", "", options, "",
             "## Compile", "", "| Target | Result | Seconds | target_triple | Benchmark mean |", "| --- | --- | --- | --- | --- |"]
    for target, c in results["compile"].items():
        mean = (c.get("benchmark") or {}).get("mean_ms", "skipped" if c.get("benchmark") else "")
        lines.append(f"| `{target}` | {'✔ ' + c['kind'] if c['ok'] else '✘ ' + c['error'][:80]} | {c.get('seconds', '')} "
                     f"| {c.get('target_triple', '')} | {mean} |")
    lines += ["", "## Serve", "", "| Target | Runtime | Where | REST | gRPC |", "| --- | --- | --- | --- | --- |"]

    def cell(result):
        return f"{'✔' if result['match'] else '✘'} {result['ms']} ms ({result['detail']})" if result else ""

    for row in results["serve"]:
        if "error" in row or "skipped" in row:
            lines.append(f"| `{row['target']}` | {row['runtime']} | {row['where']} | ✘ {row.get('error') or row['skipped']} | |")
        else:
            lines.append(f"| `{row['target']}` | {row['runtime']} | {row['where']} | {cell(row.get('rest'))} | {cell(row.get('grpc'))} |")
    if results["placement"]:
        lines += ["", "## Node placement of the Models without a node in the cluster", "",
                  "| Target | Node selector | Scheduled |", "| --- | --- | --- |"]
        for target, p in results["placement"].items():
            lines.append(f"| `{target}` | `{p.get('node_selector')}` | {p.get('scheduled', p.get('error'))} |")
    (args.out / "results.md").write_text("\n".join(lines) + "\n")


def main():
    args = parse_args()
    core = Core(args)
    results = {"function": args.function, "compile": {}, "serve": [], "placement": {}}
    function_key = setup(core, args)
    results["build"] = build(core, args, function_key)

    core.task(function_key, "tvm+compile")
    with ThreadPoolExecutor(max_workers=args.parallel) as pool:
        futures = {target: pool.submit(compile_target, core, args, function_key, results["build"]["ir_key"], target)
                   for target in args.targets}
        for target, future in futures.items():
            results["compile"][target] = future.result()
            log(f"compile {target}: {json.dumps(results['compile'][target])[:300]}")

    nodes = node_architectures()
    reference = {}
    for target, compiled in results["compile"].items():
        if compiled["ok"]:
            rows, placement_check = serve_and_check(core, args, function_key, target, compiled, nodes, reference)
            results["serve"] += rows
            if placement_check:
                results["placement"][target] = placement_check
            write_report(args, results)
    write_report(args, results)
    failed = [r for r in results["serve"]
              if "skipped" not in r and ("error" in r or not all(r.get(m, {}).get("match") for m in ("rest", "grpc")))]
    failed += [t for t, c in results["compile"].items() if not c["ok"]]
    log(f"results: {args.out / 'results.md'} ({'all passed' if not failed else f'{len(failed)} failures'})")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
