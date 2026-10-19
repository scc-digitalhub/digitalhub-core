import json
import os
import shutil
import subprocess
import tempfile

SEVERITIES = ["CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"]


def _run_trivy(image, report, findings):
    if shutil.which("trivy") is None:
        raise RuntimeError("trivy is not installed in the analyzer image")
    result = subprocess.run(
        ["trivy", "image", "--format", "json", "--output", report, "--quiet", "--", image],
        check=False,
    )
    if result.returncode != 0 or not os.path.exists(report):
        raise RuntimeError(f"Trivy failed with exit code {result.returncode}")
    with open(report) as report_file:
        data = json.load(report_file)
    for result in data.get("Results", []) or []:
        for category in ("Vulnerabilities", "Misconfigurations", "Secrets"):
            for finding in result.get(category, []) or []:
                severity = str(finding.get("Severity") or "UNKNOWN").upper()
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
    image = function.spec.image or None
    findings = {severity: 0 for severity in SEVERITIES}
    scanners = []
    errors = []
    output_dir = tempfile.mkdtemp()
    try:
        if not image:
            raise ValueError("no container image available")
        _run_trivy(image, os.path.join(output_dir, "trivy-image.json"), findings)
        scanners.append("trivy-image")
    except Exception as error:
        errors.append(f"image analysis failed: {error}")
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
    with open(os.path.join(output_dir, "summary.json"), "w") as summary_file:
        json.dump(summary, summary_file, indent=2)
    print(json.dumps(summary, indent=2))