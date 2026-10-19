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
        [sys.executable, "-m", "bandit", "-r", source_path, "-f", "json", "-o", report, "-q"],
        check=False,
    )
    if result.returncode not in (0, 1) or not os.path.exists(report):
        raise RuntimeError(f"Bandit failed with exit code {result.returncode}")
    with open(report) as report_file:
        data = json.load(report_file)
    if data.get("errors"):
        raise RuntimeError(f"Bandit could not analyze all sources: {data['errors']}")
    for issue in data.get("results", []):
        severity = str(issue.get("issue_severity") or "UNKNOWN").upper()
        severity = severity if severity in SEVERITIES else "UNKNOWN"
        findings[severity] += 1


def _assess(findings, scanners):
    if not scanners:
        return "UNKNOWN"
    if findings["CRITICAL"] or findings["HIGH"]:
        return "FAILED"
    if findings["MEDIUM"] or findings["LOW"] or findings["UNKNOWN"]:
        return "WARNING"
    return "PASSED"


def main(project, run, function_key: str, report_key: str = None):
    function = project.get_function(function_key)
    if not function:
        raise ValueError(f"function {function_key} not found in project")
    findings = {severity: 0 for severity in SEVERITIES}
    scanners = []
    errors = []
    work = tempfile.mkdtemp()
    source_path = os.path.join(work, "src")
    output_dir = os.path.join(work, "out")
    os.makedirs(source_path)
    os.makedirs(output_dir)
    try:
        source = function.spec.source or {}
        if str(source.get("lang") or "python").lower() != "python":
            raise ValueError("Bandit requires Python source")
        if not _fetch_source(source, source_path):
            raise ValueError("no Python source available")
        _run_bandit(source_path, output_dir, findings)
        scanners.append("bandit")
    except Exception as error:
        errors.append(f"source analysis failed: {error}")
    summary = {
        "target": function_key,
        "kind": function.kind,
        "name": function.name,
        "report_key": report_key,
        "scanners": scanners,
        "findings": findings,
        "assessment": _assess(findings, scanners),
        "errors": errors,
    }
    with open(os.path.join(output_dir, "summary.json"), "w") as summary_file:
        json.dump(summary, summary_file, indent=2)
    print(json.dumps(summary, indent=2))