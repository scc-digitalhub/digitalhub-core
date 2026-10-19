"""
Predefined security analysis function.

Analyzes a target function by:
- running bandit (static analysis) on python sources, when available
- running trivy on the sources and on the container image, when the trivy binary is available

Report files and a summary.json (assessment + findings by severity) are written to `output_path`.
"""

import base64
import io
import json
import os
import shutil
import subprocess
import tempfile
import urllib.request
import zipfile
from urllib.parse import urlparse

SEVERITIES = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"]
EXTENSIONS = {"python": "py", "sql": "sql", "java": "java", "javascript": "js", "typescript": "ts"}


def _count(findings, severity):
    sev = str(severity or "UNKNOWN").upper()
    sev = sev if sev in SEVERITIES else "UNKNOWN"
    findings[sev] = findings.get(sev, 0) + 1


def _extract(data, dest, name):
    if zipfile.is_zipfile(io.BytesIO(data)):
        with zipfile.ZipFile(io.BytesIO(data)) as zf:
            for member in zf.namelist():
                target = os.path.realpath(os.path.join(dest, member))
                if not target.startswith(os.path.realpath(dest) + os.sep):
                    raise ValueError(f"unsafe path in archive: {member}")
            zf.extractall(dest)
    else:
        with open(os.path.join(dest, name), "wb") as f:
            f.write(data)


def _fetch_source(source, dest):
    """Materialize the function source into dest. Returns True if any source is available."""
    if not source:
        return False

    lang = source.get("lang") or "python"
    name = f"main.{EXTENSIONS.get(str(lang).lower(), 'txt')}"

    if source.get("base64"):
        _extract(base64.b64decode(source["base64"]), dest, name)
        return True

    url = source.get("source")
    if not url:
        return False

    parsed = urlparse(url)
    if parsed.scheme.startswith("git") or url.endswith(".git"):
        if shutil.which("git") is None:
            print("git not available, skip source checkout")
            return False
        repo = url.replace("git+", "", 1)
        subprocess.run(["git", "clone", "--depth", "1", repo, dest], check=True)
        return True

    if parsed.scheme in ("http", "https"):
        with urllib.request.urlopen(url, timeout=60) as resp:
            _extract(resp.read(), dest, os.path.basename(parsed.path) or name)
        return True

    if parsed.scheme == "s3":
        buf = io.BytesIO()
        _s3_client().download_fileobj(parsed.netloc, parsed.path.lstrip("/"), buf)
        _extract(buf.getvalue(), dest, os.path.basename(parsed.path) or name)
        return True

    print(f"unsupported source scheme {parsed.scheme}, skip source analysis")
    return False


def _run_bandit(src, out_dir, findings):
    print("Running bandit at", src)
    report = os.path.join(out_dir, "bandit.json")
    # bandit exits with 1 when issues are found
    subprocess.run(["python", "-m", "bandit", "-r", src, "-f", "json", "-o", report, "-q"], check=False)
    if not os.path.exists(report):
        print("Bandit report not found at", report)
        return False
    with open(report) as f:
        for issue in json.load(f).get("results", []):
            _count(findings, issue.get("issue_severity"))
    return True


def _run_trivy(args, report, findings):
    res = subprocess.run(["trivy", *args, "--format", "json", "--output", report, "--quiet"], check=False)
    if res.returncode != 0 or not os.path.exists(report):
        return False
    with open(report) as f:
        data = json.load(f)
    for result in data.get("Results", []) or []:
        for key in ("Vulnerabilities", "Misconfigurations", "Secrets"):
            for item in result.get(key, []) or []:
                _count(findings, item.get("Severity"))
    return True


def _s3_client():
    import boto3

    return boto3.client(
        "s3",
        endpoint_url=os.environ.get("S3_ENDPOINT_URL") or os.environ.get("AWS_ENDPOINT_URL"),
        aws_access_key_id=os.environ.get("AWS_ACCESS_KEY_ID"),
        aws_secret_access_key=os.environ.get("AWS_SECRET_ACCESS_KEY"),
        aws_session_token=os.environ.get("AWS_SESSION_TOKEN"),
        region_name=os.environ.get("AWS_REGION") or os.environ.get("S3_REGION"),
    )


def _upload(out_dir, output_path):
    parsed = urlparse(output_path)
    if parsed.scheme != "s3":
        print(f"unsupported output path {output_path}, reports kept locally")
        return
    client = _s3_client()
    prefix = parsed.path.lstrip("/")
    if prefix and not prefix.endswith("/"):
        prefix += "/"
    for fname in os.listdir(out_dir):
        client.upload_file(os.path.join(out_dir, fname), parsed.netloc, prefix + fname)


def _assess(findings, scanners):
    if not scanners:
        return "UNKNOWN"
    if findings.get("CRITICAL", 0) or findings.get("HIGH", 0):
        return "FAILED"
    if findings.get("MEDIUM", 0) or findings.get("LOW", 0) or findings.get("UNKNOWN", 0):
        return "WARNING"
    return "PASSED"


def main(
    project,
    run,
    function_key: str,
    report_key: str = None,
):
    print(f"security analysis of {function_key}")

    function = project.get_function(function_key)
    if not function:
        print(f"function {function_key} not found in project")
        _error_report(function_key, f"function {function_key} not found in project")
        return

    source = function.spec.source or {}
    image = function.spec.image or None
    
    findings = {s: 0 for s in SEVERITIES}
    scanners = []
    errors = []

    work = tempfile.mkdtemp()
    src = os.path.join(work, "src")
    out = os.path.join(work, "out")
    os.makedirs(src)
    os.makedirs(out)

    has_trivy = shutil.which("trivy") is not None
    if not has_trivy:
        print("trivy not available, skip vulnerability scan")

    try:
        if _fetch_source(source, src):
            lang = str((source or {}).get("lang") or "").lower()
            if lang in ("", "python") and _run_bandit(src, out, findings):
                scanners.append("bandit")
            if has_trivy and _run_trivy(["fs", src], os.path.join(out, "trivy-fs.json"), findings):
                scanners.append("trivy-fs")
    except Exception as e:
        errors.append(f"source analysis failed: {e}")

    if image and has_trivy:
        try:
            if _run_trivy(["image", image], os.path.join(out, "trivy-image.json"), findings):
                scanners.append("trivy-image")
        except Exception as e:
            errors.append(f"image analysis failed: {e}")

    summary = {
        "target": function_key,
        "kind": function.kind,
        "name": function.name,
        "report_key": report_key,
        "image": image,
        "scanners": scanners,
        "findings": findings,
        "assessment": _assess(findings, scanners),
        "errors": errors,
    }
    with open(os.path.join(out, "summary.json"), "w") as f:
        json.dump(summary, f, indent=2)

    print(json.dumps(summary, indent=2))

def _error_report(report, message = None):
    if message:
        print(f"error report {report}: {message}")
    else:
        print(f"error report {report}: no message provided")