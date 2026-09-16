# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Publishing helper shared by the builder scripts and compiler.py.

It uploads the output folder as a Model entity through the digitalhub SDK and records the
Model key in run.status.outputs, where TvmRuntime picks it up to chain the next task (same
pattern as digitalhub_runtime_python.utils.outputs.build_status).

Credentials for CORE (DHCORE_*) and S3 (AWS_*) come from the env injected by the K8s
framework; the SDK resolves them by itself.
"""

from __future__ import annotations

import os
import re
import sys
import time
from pathlib import Path
from typing import Any, Dict, Optional

# RUN_ID is removed before importing digitalhub: the SDK would try to load the current run
# (kind tvm+*:run), and there is no Python builder for this Java-only runtime, so it would
# fail. The id is kept here for the REST update of the run status.
_RUN_ID = os.environ.pop("RUN_ID", None)

import digitalhub as dh  # noqa: E402

# Spec fields of the TVM model kinds, as declared by TvmIrModelSpec and TvmSoModelSpec in
# CORE. The generic spec fields (path, framework, algorithm, parameters) are always there.
_TYPED_MODEL_FIELDS = {
    "tvm-ir": ("entry", "inputs", "outputs", "source_format", "keep_params_in_input", "sanitize_input_names"),
    "tvm-so": ("entry", "inputs", "outputs", "target", "opt_level", "manifest"),
}


def publish_model_and_register_output(
    *,
    out_dir: Path,
    name: str,
    output_key: str,
    spec: Dict[str, Any],
    kind: str = "model",
    relationship_source: Optional[str] = None,
) -> str:
    """Creates the Model entity (uploading out_dir), optionally links it with a CONSUMES
    relationship, writes its key to run.status.outputs[output_key] and returns the key.

    `kind` selects the typed model (tvm-ir, tvm-so). When the installed SDK cannot log that
    kind, the model is logged with the generic kind "model" and the typed fields move into
    `parameters`, so nothing is lost.
    """
    project = os.environ["PROJECT_NAME"]
    if not _RUN_ID:
        raise RuntimeError("RUN_ID missing (required to update the run status)")

    model = _log_model(project, _sanitize(name), kind, out_dir, dict(spec or {}))
    print(f"  [SDK] model created: {model.key}")

    if relationship_source:
        try:
            from digitalhub.entities._commons.enums import Relationship

            model.add_relationship(relation=Relationship.CONSUMES.value, dest=relationship_source)
            model.save(update=True)
            print(f"  [SDK] relationship CONSUMES -> {relationship_source}")
        except Exception as error:  # noqa: BLE001
            print(f"  [SDK] failed to set relationship: {error}", file=sys.stderr)

    try:
        _patch_run_status_outputs(project, _RUN_ID, output_key, model.key)
        print(f"  [SDK] run.status.outputs.{output_key} = {model.key}")
    except Exception as error:  # noqa: BLE001
        # The Model exists already: only the automatic chaining to the next task is lost.
        print(f"  [SDK] failed to update run status: {error}", file=sys.stderr)

    return model.key


def _log_model(project: str, name: str, kind: str, out_dir: Path, spec: Dict[str, Any]):
    """Logs the model with its typed kind when possible, else as a generic model."""
    if kind in _TYPED_MODEL_FIELDS and _ensure_typed_kind(kind):
        from digitalhub.entities.model._base.crud import log_base_model

        print(f"  [SDK] log {kind} model (name={name!r}, project={project!r}, source={out_dir})")
        return log_base_model(project=project, name=name, kind=kind, source=str(out_dir), **spec)

    if kind != "model":
        print(f"  [SDK] kind {kind!r} not available in this SDK; logging kind 'model'", file=sys.stderr)
    framework = spec.pop("framework", None)
    algorithm = spec.pop("algorithm", None)
    parameters = dict(spec.pop("parameters", {}) or {})
    parameters.update(spec)
    print(f"  [SDK] dh.log_model(kind='model', name={name!r}, project={project!r}, source={out_dir})")
    return dh.log_model(
        project=project,
        name=name,
        kind="model",
        source=str(out_dir),
        framework=framework,
        algorithm=algorithm,
        parameters=parameters,
    )


def _ensure_typed_kind(kind: str) -> bool:
    """Makes the SDK able to log a TVM model kind.

    SDK releases without the TVM kinds only know model, mlflow, sklearn and huggingface.
    The missing kind is registered here as a copy of the generic model builder whose spec
    also accepts the TVM fields. The SDK internals change between releases, so any failure
    returns False and the caller falls back to the generic kind.
    """
    try:
        from digitalhub.factory.registry import registry

        try:
            registry.get_entity_builder(kind)
            return True  # the SDK supports this kind natively
        except Exception:  # noqa: BLE001
            pass

        from pydantic import create_model

        from digitalhub.entities.model._base.builder import ModelBuilder
        from digitalhub.entities.model._base.entity import Model
        from digitalhub.entities.model._base.spec import ModelSpec, ModelValidator
        from digitalhub.entities.model._base.status import ModelStatus

        fields = _TYPED_MODEL_FIELDS[kind]

        class TypedModelSpec(ModelSpec):
            def __init__(self, path, framework=None, algorithm=None, parameters=None, **typed):
                super().__init__(path, framework, algorithm, parameters)
                for field in fields:
                    setattr(self, field, typed.get(field))

        validator = create_model(
            f"ModelValidator_{kind.replace('-', '_')}",
            __base__=ModelValidator,
            **{field: (Optional[Any], None) for field in fields},
        )
        builder = type(
            f"ModelBuilder_{kind.replace('-', '_')}",
            (ModelBuilder,),
            {
                "ENTITY_CLASS": Model,
                "ENTITY_SPEC_CLASS": TypedModelSpec,
                "ENTITY_SPEC_VALIDATOR": validator,
                "ENTITY_STATUS_CLASS": ModelStatus,
                "ENTITY_KIND": kind,
            },
        )
        registry.add_entity_builder(kind, builder)
        return True
    except Exception as error:  # noqa: BLE001
        print(f"  [SDK] cannot register model kind {kind!r}: {error}", file=sys.stderr)
        return False


def _patch_run_status_outputs(project: str, run_id: str, output_key: str, value: str) -> None:
    """Sets run.status.outputs.<output_key> = value through the REST API.

    CORE has no PATCH for runs, so the run is read, changed and written back, retrying a
    few times when another update gets in between.
    """
    import requests

    endpoint = os.environ["DHCORE_ENDPOINT"].rstrip("/")
    url = f"{endpoint}/api/v1/-/{project}/runs/{run_id}"
    auth, headers = _build_auth_headers()

    last = None
    for attempt in range(3):
        response = requests.get(url, auth=auth, headers=headers, timeout=15)
        response.raise_for_status()
        run = response.json()

        status = run.get("status") or {}
        outputs = dict(status.get("outputs") or {})
        outputs[output_key] = value
        status["outputs"] = outputs
        run["status"] = status

        last = requests.put(
            url,
            json=run,
            auth=auth,
            headers={**headers, "Content-Type": "application/json"},
            timeout=15,
        )
        if last.status_code < 400:
            return
        if last.status_code not in (409, 412, 500, 502, 503):
            last.raise_for_status()
        time.sleep(0.5 * (attempt + 1))
    if last is not None:
        last.raise_for_status()


def _build_auth_headers():
    """Auth for CORE: a bearer token, else basic user/password, else none (local CORE)."""
    token = os.environ.get("DHCORE_ACCESS_TOKEN")
    if token:
        return None, {"Authorization": f"Bearer {token}"}
    user = os.environ.get("DHCORE_USER")
    password = os.environ.get("DHCORE_PASSWORD")
    if user and password:
        return (user, password), {}
    return None, {}


def _sanitize(name: str) -> str:
    """Makes a value safe as an entity name: lowercase, every character outside
    [a-zA-Z0-9._+-] replaced by '-', repeated dashes collapsed and trimmed."""
    if not name:
        return "tvm-model"
    clean = re.sub(r"[^a-zA-Z0-9._+\-]+", "-", name.lower())
    clean = re.sub(r"-+", "-", clean).strip("-")
    return clean or "tvm-model"
