"""Shared helper used by builders and compiler.py to publish a Model entity via
the digitalhub SDK and record its key in run.status.outputs (same pattern as
digitalhub_runtime_python.utils.outputs.build_status).

Credentials for both core (DHCORE_*) and S3 (AWS_*) come from env injected by
the K8s framework; the SDK resolves them automatically.
"""
from __future__ import annotations

import json
import os
import sys
from pathlib import Path
from typing import Any, Dict, Optional

# Pop RUN_ID before importing digitalhub: the SDK's _search_run_ctx would try
# to load a run with kind tvm+*:run, but no Python builder exists for the TVM
# runtime (it's Java-only) so it'd raise BuilderError. We keep RUN_ID locally
# for the REST PATCH on status, which doesn't go through the SDK factory.
_RUN_ID = os.environ.pop("RUN_ID", None)

import digitalhub as dh


# kind -> typed SDK logger (digitalhub >= our patch). Falls back to the generic
# kind="model" when the installed SDK has no typed builder (e.g. base image not
# yet rebuilt), folding the typed fields into `parameters` so nothing is lost.
_TYPED_LOGGERS = {"tvm-ir": "log_tvm_ir", "tvm-so": "log_tvm_so"}


def publish_model_and_register_output(
    *,
    out_dir: Path,
    name: str,
    output_key: str,
    spec: Dict[str, Any],
    kind: str = "model",
    relationship_source: Optional[str] = None,
) -> str:
    """Creates a Model entity (with S3 upload), optionally adds a CONSUMES
    relationship, writes its key to run.status.outputs[output_key], returns
    model.key. `kind` selects the typed model (tvm-ir/tvm-so) when the SDK
    supports it, otherwise a generic kind="model" is logged.
    """
    project = os.environ["PROJECT_NAME"]
    run_id = _RUN_ID
    if not run_id:
        raise RuntimeError("RUN_ID missing (required to PATCH run status)")

    spec = dict(spec or {})
    log_fn = getattr(dh, _TYPED_LOGGERS.get(kind, ""), None)
    if log_fn is not None:
        print(f"  [SDK] {_TYPED_LOGGERS[kind]}(name={name!r}, project={project!r}, source={out_dir})")
        model = log_fn(project=project, name=_sanitize(name), source=str(out_dir), **spec)
    else:
        if kind != "model":
            print(f"  [SDK] no typed builder for kind={kind!r}; falling back to kind='model'", file=sys.stderr)
        
        framework = spec.pop("framework", None)
        algorithm = spec.pop("algorithm", None)
        parameters = dict(spec.pop("parameters", {}) or {})
        parameters.update(spec)
        print(f"  [SDK] dh.log_model(kind='model', name={name!r}, project={project!r}, source={out_dir})")
        model = dh.log_model(
            project=project,
            name=_sanitize(name),
            kind="model",
            source=str(out_dir),
            framework=framework,
            algorithm=algorithm,
            parameters=parameters,
        )
    print(f"  [SDK] model created: {model.key}")

    if relationship_source:
        try:
            from digitalhub.entities._commons.enums import Relationship
            model.add_relationship(relation=Relationship.CONSUMES.value, dest=relationship_source)
            model.save(update=True)
            print(f"  [SDK] relationship CONSUMES -> {relationship_source}")
        except Exception as e:
            print(f"  [SDK] failed to set relationship: {e}", file=sys.stderr)

    try:
        _patch_run_status_outputs(project, run_id, output_key, model.key)
        print(f"  [SDK] run.status.outputs.{output_key} = {model.key}")
    except Exception as e:
        print(f"  [SDK] failed to update run status: {e}", file=sys.stderr)

    return model.key


def _patch_run_status_outputs(project: str, run_id: str, output_key: str, value: str) -> None:
    """PATCH-merge run.status.outputs.<output_key> = value via REST. Auth
    priority: DHCORE_ACCESS_TOKEN (JWT), then DHCORE_USER+PASSWORD (basic).
    """
    import time

    import requests

    endpoint = os.environ["DHCORE_ENDPOINT"].rstrip("/")
    url = f"{endpoint}/api/v1/-/{project}/runs/{run_id}"
    auth, headers = _build_auth_headers()

    last = None
    for attempt in range(3):
        r = requests.get(url, auth=auth, headers=headers, timeout=15)
        r.raise_for_status()
        run = r.json()

        status = run.get("status") or {}
        outputs = dict(status.get("outputs") or {})
        outputs[output_key] = value
        status["outputs"] = outputs
        run["status"] = status

        last = requests.put(
            url, json=run, auth=auth,
            headers={**headers, "Content-Type": "application/json"}, timeout=15,
        )
        if last.status_code < 400:
            return
        if last.status_code not in (409, 412, 500, 502, 503):
            last.raise_for_status()
        time.sleep(0.5 * (attempt + 1))
    if last is not None:
        last.raise_for_status()


def _build_auth_headers():
    token = os.environ.get("DHCORE_ACCESS_TOKEN")
    if token:
        return None, {"Authorization": f"Bearer {token}"}
    user = os.environ.get("DHCORE_USER")
    password = os.environ.get("DHCORE_PASSWORD")
    if user and password:
        return (user, password), {}
    raise RuntimeError(
        "no DHCORE auth: set DHCORE_ACCESS_TOKEN, or DHCORE_USER+DHCORE_PASSWORD"
    )


def _sanitize(name: str) -> str:
    """Make a value safe as an entity name: lowercase, replace any char outside
    [a-zA-Z0-9._+-] with '-', collapse repeated dashes and trim them."""
    import re

    if not name:
        return "tvm-model"
    s = re.sub(r"[^a-zA-Z0-9._+\-]+", "-", name.lower())
    s = re.sub(r"-+", "-", s).strip("-")
    return s or "tvm-model"
