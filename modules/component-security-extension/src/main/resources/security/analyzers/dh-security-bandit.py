import base64
import hashlib
import io
import json
import os
import platform
import shutil
import subprocess
import sys
import tarfile
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

DHCLI_REPOSITORY = "scc-digitalhub/digitalhub-cli"


def _http_get(url):
    request = urllib.request.Request(url, headers={"User-Agent": "dh-security-bandit"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return response.read()


def _install_dhcli(install_dir):
    system = platform.system()
    os_name = {"Linux": "linux", "Darwin": "darwin"}.get(system)
    if not os_name:
        raise RuntimeError(f"unsupported operating system for dhcli: {system}")
    machine = platform.machine().lower()
    arch = {"x86_64": "amd64", "amd64": "amd64", "aarch64": "arm64", "arm64": "arm64"}.get(machine)
    if not arch:
        raise RuntimeError(f"unsupported architecture for dhcli: {machine}")

    tag = os.environ.get("DHCLI_VERSION")
    if not tag:
        release = json.loads(_http_get(f"https://api.github.com/repos/{DHCLI_REPOSITORY}/releases/latest"))
        tag = release.get("tag_name")
        if not tag:
            raise RuntimeError("could not determine the latest dhcli release version")
    release_url = f"https://github.com/{DHCLI_REPOSITORY}/releases/download/{tag}"
    archive_name = f"dhcli-{os_name}-{arch}.tar.gz"

    archive = _http_get(f"{release_url}/{archive_name}")
    checksums = _http_get(f"{release_url}/dhcli_{tag}_checksums.txt").decode()
    expected = next(
        (parts[0] for parts in (line.split() for line in checksums.splitlines())
         if len(parts) >= 2 and parts[1] == archive_name),
        None,
    )
    if not expected:
        raise RuntimeError(f"no checksum found for {archive_name}")
    if hashlib.sha256(archive).hexdigest() != expected.lower():
        raise RuntimeError(f"checksum verification failed for {archive_name}")

    with tarfile.open(fileobj=io.BytesIO(archive), mode="r:gz") as tar:
        member = tar.getmember("dhcli")
        if not member.isfile():
            raise RuntimeError("dhcli archive does not contain a regular dhcli file")
        binary = tar.extractfile(member).read()

    os.makedirs(install_dir, exist_ok=True)
    target = os.path.join(install_dir, "dhcli")
    fd, tmp_path = tempfile.mkstemp(dir=install_dir)
    try:
        with os.fdopen(fd, "wb") as output:
            output.write(binary)
        os.chmod(tmp_path, 0o755)
        os.replace(tmp_path, target)
    except Exception:
        if os.path.exists(tmp_path):
            os.unlink(tmp_path)
        raise
    print(f"Installed dhcli {tag} to {target}")


def _ensure_dhcli():
    install_dir = os.environ.get("DHCLI_INSTALL_DIR") or os.path.join(os.path.expanduser("~"), ".local", "bin")
    os.environ["PATH"] = os.pathsep.join(filter(None, [os.environ.get("PATH"), install_dir]))
    dhcli = shutil.which("dhcli")
    if dhcli:
        return dhcli
    _install_dhcli(install_dir)
    dhcli = shutil.which("dhcli")
    if not dhcli:
        raise RuntimeError(f"dhcli installation failed: not found in {install_dir}")
    return dhcli


def _write_extension(path, extension_id, function, report):
    run = report.get("run")
    run = getattr(run, "id", run)
    lines = [
        f"id: {json.dumps(extension_id)}",
        "kind: security-report",
        f"name: {json.dumps('security-report-' + function.name)}",
        "spec:",
        f"  analyzer: {json.dumps(report['analyzer'])}",
        f"  format: {json.dumps(report['format'].upper())}",
        f"  function: {json.dumps(report['target'])}",
    ]
    if run is not None:
        lines.append(f"  run: {json.dumps(str(run))}")
    lines.append(f"  assessment: {json.dumps(report['assessment'])}")
    lines.append(f"  findings: {json.dumps(report['findings'])}")
    with open(path, "w") as output:
        output.write("\n".join(lines) + "\n")


def _upload_report(project, report, function, report_file):
    output_dir = os.path.dirname(report_file)
    extension_id = os.urandom(16).hex()
    extension_file = os.path.join(output_dir, "security-report.yaml")
    _write_extension(extension_file, extension_id, function, report)

    dhcli = _ensure_dhcli()
    subprocess.run(
        [dhcli, "extension", "create", "-p", project.name, "-f", extension_file, "function", function.id],
        check=True,
    )
    subprocess.run(
        [dhcli, "upload", "-p", project.name, "-f", report_file, "extension", extension_id],
        check=True,
    )

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
        "format": "sarif"
    }

    _upload_report(project, report, function, report_file)
