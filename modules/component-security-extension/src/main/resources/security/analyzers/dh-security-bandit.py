import base64
import io
import json
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from urllib.parse import urlparse

SEVERITIES = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"]


def _extract(data, dest, name):
    if zipfile.is_zipfile(io.BytesIO(data)):
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            for member in archive.namelist():
                target = os.path.realpath(os.path.join(dest, member))
                if not target.startswith(os.path.realpath(dest) + os.sep):
                    raise ValueError(f"unsafe path in archive: {member}")
            archive.extractall(dest)
    else:
        with open(os.path.join(dest, name), "wb") as output:
            output.write(data)


def _fetch_source(source, dest):
    if not source:
        return False
    if source.get("base64"):
        _extract(base64.b64decode(source["base64"]), dest, "main.py")
        return True
    url = source.get("source")
    if not url:
        return False
    parsed = urlparse(url)
    if parsed.scheme.startswith("git") or url.endswith(".git"):
        if shutil.which("git") is None:
            raise RuntimeError("git is not available for source checkout")
        subprocess.run(["git", "clone", "--depth", "1", url.replace("git+", "", 1), dest], check=True)
        return True
    if parsed.scheme in ("http", "https"):
        with urllib.request.urlopen(url, timeout=60) as response:
            _extract(response.read(), dest, os.path.basename(parsed.path) or "main.py")
        return True
    if parsed.scheme == "s3":
        import boto3

        client = boto3.client(
            "s3",
            endpoint_url=os.environ.get("S3_ENDPOINT_URL") or os.environ.get("AWS_ENDPOINT_URL"),
            aws_access_key_id=os.environ.get("AWS_ACCESS_KEY_ID"),
            aws_secret_access_key=os.environ.get("AWS_SECRET_ACCESS_KEY"),
            aws_session_token=os.environ.get("AWS_SESSION_TOKEN"),
            region_name=os.environ.get("AWS_REGION") or os.environ.get("S3_REGION"),
        )
        buffer = io.BytesIO()
        client.download_fileobj(parsed.netloc, parsed.path.lstrip("/"), buffer)
        _extract(buffer.getvalue(), dest, os.path.basename(parsed.path) or "main.py")
        return True
    raise ValueError(f"unsupported source scheme: {parsed.scheme}")


def _run_bandit(source_path, output_dir, findings):
    report = os.path.join(output_dir, "bandit.json")
    result = subprocess.run(
        [sys.executable, "-m", "bandit", "-r", source_path, "-f", "sarif", "-o", report, "-q"],
        check=False,
    )
    if result.returncode not in (0, 1) or not os.path.exists(report):
        raise RuntimeError(f"Bandit failed with exit code {result.returncode}")
    with open(report) as report_file:
        data = json.load(report_file)
    runs = data.get("runs")
    if not isinstance(runs, list):
        raise RuntimeError("invalid Bandit SARIF report: missing runs")
    for sarif_run in runs:
        errors = []
        for invocation in sarif_run.get("invocations") or []:
            if invocation.get("executionSuccessful") is False:
                errors.append("execution not successful")
            for key in ("toolConfigurationNotifications", "toolExecutionNotifications"):
                for notification in invocation.get(key) or []:
                    if notification.get("level", "error") == "error":
                        message = (notification.get("message") or {}).get("text", "")
                        locations = notification.get("locations") or []
                        uri = ((locations[0].get("physicalLocation") or {}).get("artifactLocation") or {}).get("uri") if locations else None
                        errors.append(f"{uri}: {message}" if uri else message)
        if errors:
            raise RuntimeError(f"Bandit could not analyze all sources: {errors}")
        for issue in sarif_run.get("results") or []:
            severity = str((issue.get("properties") or {}).get("issue_severity") or "UNKNOWN").upper()
            severity = severity if severity in SEVERITIES else "UNKNOWN"
            findings[severity] += 1
    return report


def _assess(findings):
    if findings["CRITICAL"] or findings["HIGH"]:
        return "FAILED"
    if findings["MEDIUM"] or findings["LOW"] or findings["UNKNOWN"]:
        return "WARNING"
    return "PASSED"

EXTENSION_KIND = "security-report"


def _execute(client, operation, target, payload=None):
    from digitalhub.stores.client.common.enums import ApiType
    from digitalhub.stores.client.compiler.operation import ClientOp

    return client.execute(ClientOp(ApiType.CONTEXT, operation, target, payload=payload))


def _build_extension(project, function, report, extension_id, path):
    run = report.get("run")
    run = getattr(run, "id", run)
    spec = {
        "path": path,
        "analyzer": report["analyzer"],
        "format": report["format"].upper(),
        "function": report["target"],
        "assessment": report["assessment"],
        "findings": report["findings"],
    }
    if run is not None:
        spec["run"] = str(run)
    return {
        "id": extension_id,
        "kind": EXTENSION_KIND,
        "name": f"{EXTENSION_KIND}-{function.name}",
        "project": project.name,
        # enforced server side from the function, but required by request validation
        "parent": f"function://{project.name}/{function.name}:{function.id}",
        "spec": spec,
        "status": {"state": "CREATED"},
    }


def _upload_report(project, report, function, report_file):
    from digitalhub.entities._commons.utils import build_log_path_as_partition
    from digitalhub.entities._constructors.uuid import build_uuid
    from digitalhub.stores.client import get_client
    from digitalhub.stores.client.common.enums import BackendOp
    from digitalhub.stores.client.compiler.targets import ContextCollectionTarget, ContextEntityTarget
    from digitalhub.stores.data.api import get_store

    client = get_client()
    extension_id = build_uuid()
    name = f"{EXTENSION_KIND}-{function.name}"
    path =f"{build_log_path_as_partition(project.name, "function", function.name, function.id)}extensions/{name}/{extension_id}/"

    # 1. create the extension on the function: POST /-/{project}/functions/{id}/extensions
    #    (no dedicated route in the SDK, the collection target renders "{entity_type}s")
    extension = _execute(
        client,
        BackendOp.CREATE,
        ContextCollectionTarget(project.name, f"functions/{function.id}/extension"),
        _build_extension(project, function, report, extension_id, None),
    )
    target = ContextEntityTarget(project.name, "extension", extension["id"])

    def update_status(**status):
        extension["status"] = {**(extension.get("status") or {}), **status}
        return _execute(client, BackendOp.UPDATE, target, extension)

    # 2. upload the SARIF report with the SDK store, using the client's store credentials
    update_status(state="UPLOADING")
    try:
        store = get_store(path)
        paths = store.upload(report_file, path)
        files = store.get_file_info(path, paths)
    except Exception:
        update_status(state="ERROR")
        raise

    # 3. mark the extension as ready with the uploaded files info
    update_status(state="READY", files=files)


def main(project, run, function_key: str):
    function = project.get_function(function_key)
    if not function:
        raise ValueError(f"function {function_key} not found in project")
    findings = {severity: 0 for severity in SEVERITIES}
    work = tempfile.mkdtemp()
    source_path = os.path.join(work, "src")
    output_dir = os.path.join(work, "out")
    os.makedirs(source_path)
    os.makedirs(output_dir)
    source = function.spec.source or {}
    if str(source.get("lang") or "python").lower() != "python":
        raise ValueError("Bandit requires Python source")
    if not _fetch_source(source, source_path):
        raise ValueError("no Python source available")
    report_file = _run_bandit(source_path, output_dir, findings)

    report = {
        "analyzer": "bandit",
        "target": function_key,
        "run": run if run is not None else os.environ.get("RUN_ID"),
        "findings": findings,
        "assessment": _assess(findings),
        "format": "sarif",
        "report_name": os.path.basename(report_file)
    }

    _upload_report(project, report, function, report_file)
