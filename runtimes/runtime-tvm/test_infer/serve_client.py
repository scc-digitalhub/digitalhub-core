#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Talks to a TVM serve started by CORE.

It finds the tvm+serve run through the CORE API, opens a kubectl port-forward to the
Service CORE created for it, and calls the Open Inference Protocol v2 over REST or gRPC.
With an address that already reaches the serve (for example a dhcli port-forward to a
remote CORE) it skips CORE and kubectl and calls that address directly.
"""

import atexit
import base64
import json
import shutil
import socket
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
HTTP_PORT = 8080
GRPC_PORT = 9000

# Open Inference v2 datatype -> numpy dtype.
NUMPY_DTYPES = {
    "BOOL": np.bool_, "INT8": np.int8, "INT16": np.int16, "INT32": np.int32, "INT64": np.int64,
    "UINT8": np.uint8, "UINT16": np.uint16, "UINT32": np.uint32, "UINT64": np.uint64,
    "FP16": np.float16, "FP32": np.float32, "FP64": np.float64,
}
# Datatype -> typed field of InferTensorContents in the gRPC proto.
GRPC_FIELDS = {
    "BOOL": "bool_contents", "INT8": "int_contents", "INT16": "int_contents",
    "INT32": "int_contents", "INT64": "int64_contents", "UINT8": "uint_contents",
    "UINT16": "uint_contents", "UINT32": "uint_contents", "UINT64": "uint64_contents",
    "FP32": "fp32_contents", "FP64": "fp64_contents",
}


def fail(message):
    """Stops the test with a readable message and exit code 1."""
    print(f"ERROR: {message}", file=sys.stderr)
    sys.exit(1)


@dataclass
class Tensor:
    """An Open Inference v2 tensor; data is flat, in row-major order."""

    name: str
    datatype: str
    shape: list
    data: np.ndarray


@dataclass
class Serve:
    """A tvm+serve run and the Service that reaches it."""

    project: str
    run_id: str
    function: str
    model: str
    service: str
    namespace: str


class Core:
    """Read-only client of the CORE REST API, with basic auth."""

    def __init__(self, url, user, password):
        self.url = url.rstrip("/")
        token = base64.b64encode(f"{user}:{password}".encode()).decode()
        self.headers = {"Authorization": f"Basic {token}"}

    def get(self, path):
        request = urllib.request.Request(self.url + path, headers=self.headers)
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                return json.loads(response.read())
        except OSError as error:
            fail(f"CORE does not answer at {self.url}{path}: {error}")

    def projects(self):
        return [project["name"] for project in self.get("/api/v1/projects?size=500").get("content", [])]

    def serve_runs(self, project):
        runs = self.get(f"/api/v1/-/{project}/runs?size=500").get("content", [])
        return [run for run in runs if run.get("kind") == "tvm+serve:run"]


def _function_name(run):
    # spec.function is tvm://<project>/<name>:<id>
    key = (run.get("spec") or {}).get("function", "")
    return key.rsplit("/", 1)[-1].split(":")[0]


def find_serve(core, project=None, function=None, run_id=None):
    """The serve to test: the run with run_id, else the only RUNNING serve that matches
    the optional project and function. With several matches it lists them and stops,
    instead of picking one at random."""
    runs = [(name, run) for name in ([project] if project else core.projects()) for run in core.serve_runs(name)]
    if run_id:
        matches = [(name, run) for name, run in runs if run.get("id") == run_id]
    else:
        matches = [
            (name, run)
            for name, run in runs
            if (run.get("status") or {}).get("state") == "RUNNING" and function in (None, _function_name(run))
        ]

    where = f" in project {project}" if project else ""
    if not matches:
        what = f"run {run_id}" if run_id else "a RUNNING tvm+serve" + (f" of function {function}" if function else "")
        fail(f"cannot find {what}{where}: start the serve from CORE and try again")
    if len(matches) > 1:
        choices = "\n".join(f"  --project {name} --function {_function_name(run)}   (run {run['id']})" for name, run in matches)
        fail(f"there are {len(matches)} RUNNING serves{where}, pick one:\n{choices}")

    project_name, run = matches[0]
    status = run.get("status") or {}
    if status.get("state") != "RUNNING":
        fail(f"run {run['id']} is in state {status.get('state')}, not RUNNING")
    service = status.get("service") or {}
    if not service.get("name"):
        fail(f"run {run['id']} has no Service yet")

    function_name = _function_name(run)
    serve = Serve(
        project=project_name,
        run_id=run["id"],
        function=function_name,
        # The model is served as spec.served_name, or as the function name when unset.
        model=(run.get("spec") or {}).get("served_name") or function_name or "model",
        service=service["name"],
        namespace=service.get("namespace") or "default",
    )
    print(f"serve:     {serve.project}/{serve.function}  model '{serve.model}'  run {serve.run_id}")
    return serve


def direct_serve(url, model):
    """A serve reached at a known address, without looking it up in CORE."""
    serve = Serve(project=None, run_id=None, function=model, model=model, service=None, namespace=None)
    print(f"serve:     {url}  model '{model}'")
    return serve


def _kubectl():
    return ["kubectl"] if shutil.which("kubectl") else ["minikube", "kubectl", "--"]


def _free_port():
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def port_forward(serve, remote_port, ready_path=None):
    """Forwards a free local port to the serve's Service and waits until it answers.

    For HTTP it waits for ready_path to return 200: the TCP connection to kubectl opens
    before the tunnel to the pod is really up.
    """
    local_port = _free_port()
    command = _kubectl() + ["port-forward", "-n", serve.namespace, f"svc/{serve.service}", f"{local_port}:{remote_port}"]
    process = subprocess.Popen(command, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    atexit.register(process.terminate)
    for _ in range(60):
        try:
            if ready_path:
                with urllib.request.urlopen(f"http://127.0.0.1:{local_port}{ready_path}", timeout=2) as response:
                    if response.status == 200:
                        return local_port
            else:
                with socket.create_connection(("127.0.0.1", local_port), timeout=1):
                    time.sleep(0.5)
                    return local_port
        except OSError:
            time.sleep(0.5)
    fail(f"the port-forward to svc/{serve.service}:{remote_port} does not answer")


class ServeClient:
    """Model metadata and inference for one serve, over REST or gRPC."""

    def __init__(self, serve, mode="rest", url=None, grpc_url=None):
        self.serve = serve
        self.mode = mode
        self._grpc = None
        if url:
            # The serve is already reachable, e.g. through a dhcli port-forward.
            self.rest_url = url.rstrip("/")
            self.grpc_target = grpc_url or f"{urllib.parse.urlparse(self.rest_url).hostname}:{GRPC_PORT}"
        else:
            self.rest_url = f"http://127.0.0.1:{port_forward(serve, HTTP_PORT, ready_path=f'/v2/models/{serve.model}')}"
            self.grpc_target = f"127.0.0.1:{port_forward(serve, GRPC_PORT)}" if mode == "grpc" else None

    def metadata(self):
        """The model signature from /v2/models/<model>: name, datatype, shape and, for
        quantized tensors, scale and zero_point under parameters. It is always read over
        REST, because the gRPC metadata message has no parameters."""
        url = f"{self.rest_url}/v2/models/{self.serve.model}"
        try:
            with urllib.request.urlopen(url, timeout=30) as response:
                metadata = json.loads(response.read())
        except OSError as error:
            fail(f"the serve does not answer at {url}: {error}")
        if not metadata.get("inputs"):
            fail(f"the serve does not expose the model signature at /v2/models/{self.serve.model}")
        return metadata

    def infer(self, inputs):
        """Runs one inference. Returns the output tensors and the round trip in milliseconds."""
        start = time.perf_counter()
        outputs = self._infer_rest(inputs) if self.mode == "rest" else self._infer_grpc(inputs)
        return outputs, (time.perf_counter() - start) * 1000

    def _infer_rest(self, inputs):
        body = {
            "inputs": [
                {"name": t.name, "datatype": t.datatype, "shape": list(t.shape), "data": t.data.tolist()}
                for t in inputs
            ]
        }
        request = urllib.request.Request(
            f"{self.rest_url}/v2/models/{self.serve.model}/infer",
            data=json.dumps(body).encode(),
            headers={"Content-Type": "application/json"},
        )
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                result = json.loads(response.read())
        except urllib.error.HTTPError as error:
            fail(f"REST inference failed ({error.code}): {error.read().decode(errors='replace')[:500]}")
        outputs = []
        for output in result["outputs"]:
            datatype = output.get("datatype", "FP32")
            data = np.asarray(output["data"], NUMPY_DTYPES.get(datatype, np.float32)).reshape(-1)
            outputs.append(Tensor(output["name"], datatype, [int(d) for d in output["shape"]], data))
        return outputs

    def _grpc_stub(self):
        """Compiles the bundled KServe v2 proto once and opens the channel."""
        if self._grpc is None:
            import grpc
            from grpc_tools import protoc

            generated = tempfile.mkdtemp(prefix="grpc_v2_")
            proto = HERE / "grpc_predict_v2.proto"
            if protoc.main(["protoc", f"-I{HERE}", f"--python_out={generated}", f"--grpc_python_out={generated}", str(proto)]):
                fail("compiling grpc_predict_v2.proto failed")
            sys.path.insert(0, generated)
            import grpc_predict_v2_pb2 as messages
            import grpc_predict_v2_pb2_grpc as services

            # A v2 tensor easily exceeds the 4 MB gRPC default (1x3x640x640 FP32 is ~4.9 MB).
            channel = grpc.insecure_channel(
                self.grpc_target,
                options=[
                    ("grpc.max_send_message_length", 512 * 1024 * 1024),
                    ("grpc.max_receive_message_length", 512 * 1024 * 1024),
                ],
            )
            self._grpc = (grpc, messages, services.GRPCInferenceServiceStub(channel))
        return self._grpc

    def _infer_grpc(self, inputs):
        grpc, messages, stub = self._grpc_stub()
        request = messages.ModelInferRequest(model_name=self.serve.model)
        for t in inputs:
            field = GRPC_FIELDS.get(t.datatype) or fail(f"datatype {t.datatype} is not supported over gRPC")
            tensor = request.inputs.add()
            tensor.name, tensor.datatype = t.name, t.datatype
            tensor.shape.extend(int(d) for d in t.shape)
            getattr(tensor.contents, field).extend(t.data.tolist())
        try:
            response = stub.ModelInfer(request, timeout=300)
        except grpc.RpcError as error:
            fail(f"gRPC inference failed: {error.code().name} {error.details()}")
        outputs = []
        for index, output in enumerate(response.outputs):
            datatype = output.datatype or "FP32"
            dtype = NUMPY_DTYPES.get(datatype, np.float32)
            if response.raw_output_contents:
                data = np.frombuffer(response.raw_output_contents[index], dtype)
            else:
                data = np.asarray(getattr(output.contents, GRPC_FIELDS[datatype]), dtype)
            outputs.append(Tensor(output.name, datatype, [int(d) for d in output.shape], data))
        return outputs
