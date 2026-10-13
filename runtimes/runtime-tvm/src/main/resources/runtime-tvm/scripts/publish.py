# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Publish tvm-ir/tvm-so Models and record outputs for CORE's function references.

CORE and S3 credentials come from the Job's DHCORE_* and AWS_* variables.
"""

from __future__ import annotations

import os
import re
import time
from pathlib import Path
from typing import Any, Optional

# Prevent SDK auto-discovery of unsupported TVM runs; retain the ID for REST updates.
RUN_ID = os.environ.pop("RUN_ID", None)

import digitalhub as dh  # noqa: E402

# Extra fields must match CORE's TvmIrModelSpec and TvmSoModelSpec.
MODEL_KINDS = {
    "tvm-ir": {
        "output": "ir_module",
        "fields": ("entry", "inputs", "outputs", "source_format", "keep_params_in_input", "sanitize_input_names"),
    },
    "tvm-so": {
        "output": "compiled_so",
        "fields": ("entry", "inputs", "outputs", "target", "opt_level", "manifest"),
    },
}


def publish_ir_model(out_dir: Path, name: str, metadata: dict, parameters: dict) -> str:
    return publish_model(
        kind="tvm-ir",
        name=f"{name}-ir",
        folder=out_dir,
        spec={
            "framework": "tvm",
            "algorithm": "tvm-relax-ir",
            "entry": metadata["entry"],
            "inputs": metadata["inputs"],
            "outputs": metadata["outputs"],
            "source_format": metadata["source_format"],
            "keep_params_in_input": metadata.get("keep_params_in_input"),
            "sanitize_input_names": metadata.get("sanitize_input_names"),
            "parameters": parameters,
        },
    )


def publish_compiled_model(out_dir: Path, name: str, metadata: dict, source_ir_key: Optional[str]) -> str:
    """Publish model.so with a link to its source IR Model."""
    return publish_model(
        kind="tvm-so",
        name=name,
        folder=out_dir,
        spec={
            "framework": "tvm",
            "algorithm": "tvm-compiled-so",
            "entry": metadata.get("entry", "main"),
            "inputs": metadata.get("inputs"),
            "outputs": metadata.get("outputs"),
            "target": metadata["target"],
            "opt_level": metadata["opt_level"],
            "manifest": metadata,
        },
        consumes=source_ir_key,
    )


def publish_model(kind: str, name: str, folder: Path, spec: dict, consumes: Optional[str] = None) -> str:
    """Upload a Model, record its run output and return its key."""
    project = os.environ["PROJECT_NAME"]
    if not RUN_ID:
        raise RuntimeError("RUN_ID is missing: the run status cannot be updated")

    enable_model_kind(kind)
    from digitalhub.entities.model._base.crud import log_base_model

    name = entity_name(name)
    print(f"      logging Model {name!r} of kind {kind} from {folder}")
    model = log_base_model(project=project, name=name, kind=kind, source=str(folder), **spec)
    print(f"      Model created: {model.key}")

    if consumes:
        link_consumed_model(model, consumes)

    output = MODEL_KINDS[kind]["output"]
    try:
        set_run_output(project, RUN_ID, output, model.key)
        print(f"      run.status.outputs.{output} = {model.key}")
    except Exception as error:  # noqa: BLE001
        # Fail the run, retaining the uploaded Model key for recovery.
        raise RuntimeError(
            f"Model {model.key} was created, but run {RUN_ID} output {output} could not be recorded"
        ) from error
    return model.key


def enable_model_kind(kind: str) -> None:
    """Register typed TVM Model fields when the installed SDK lacks their builders."""
    from digitalhub.factory.registry import registry

    try:
        registry.get_entity_builder(kind)
        return
    except Exception:  # noqa: BLE001
        pass

    from pydantic import create_model

    from digitalhub.entities.model._base.builder import ModelBuilder
    from digitalhub.entities.model._base.entity import Model
    from digitalhub.entities.model._base.spec import ModelSpec, ModelValidator
    from digitalhub.entities.model._base.status import ModelStatus

    fields = MODEL_KINDS[kind]["fields"]

    class KindSpec(ModelSpec):
        def __init__(self, path, framework=None, algorithm=None, parameters=None, **values):
            super().__init__(path, framework, algorithm, parameters)
            for field in fields:
                setattr(self, field, values.get(field))

    suffix = kind.replace("-", "_")
    builder = type(
        f"ModelBuilder_{suffix}",
        (ModelBuilder,),
        {
            "ENTITY_CLASS": Model,
            "ENTITY_SPEC_CLASS": KindSpec,
            "ENTITY_SPEC_VALIDATOR": create_model(
                f"ModelValidator_{suffix}", __base__=ModelValidator, **{field: (Optional[Any], None) for field in fields}
            ),
            "ENTITY_STATUS_CLASS": ModelStatus,
            "ENTITY_KIND": kind,
        },
    )
    registry.add_entity_builder(kind, builder)
    print(f"      Model kind {kind} registered in the SDK (not in this SDK release)")


def link_consumed_model(model, source_key: str) -> None:
    try:
        from digitalhub.entities._commons.enums import Relationship

        model.add_relationship(relation=Relationship.CONSUMES.value, dest=source_key)
        model.save(update=True)
        print(f"      relationship CONSUMES -> {source_key}")
    except Exception as error:  # noqa: BLE001
        print(f"WARN: cannot link the source Model: {error}")


def set_run_output(project: str, run_id: str, output: str, value: str) -> None:
    """Merge and verify run outputs, retrying transient failures without re-upload.

    CORE has no run PATCH; read/modify/write is not an atomic compare-and-swap.
    """
    import requests

    url = f"{os.environ['DHCORE_ENDPOINT'].rstrip('/')}/api/v1/-/{project}/runs/{run_id}"
    auth, headers = core_auth()
    retryable = {408, 409, 412, 429, 500, 502, 503, 504}
    for attempt in range(3):
        try:
            run = requests.get(url, auth=auth, headers=headers, timeout=15)
            run.raise_for_status()
            body = run.json()
            status = body.get("status") or {}
            if (status.get("outputs") or {}).get(output) == value:
                return  # A previous PUT may have committed before its response was lost.
            status["outputs"] = {**(status.get("outputs") or {}), output: value}
            body["status"] = status

            response = requests.put(
                url, json=body, auth=auth,
                headers={**headers, "Content-Type": "application/json"}, timeout=15,
            )
            response.raise_for_status()
            confirmed = requests.get(url, auth=auth, headers=headers, timeout=15)
            confirmed.raise_for_status()
            if ((confirmed.json().get("status") or {}).get("outputs") or {}).get(output) == value:
                return
            if attempt == 2:
                raise RuntimeError(f"CORE did not confirm run {run_id} output {output} = {value}")
        except requests.HTTPError as error:
            if error.response is None or error.response.status_code not in retryable or attempt == 2:
                raise
        except (requests.ConnectionError, requests.Timeout):
            if attempt == 2:
                raise
        time.sleep(0.5 * (attempt + 1))


def core_auth():
    """Credentials for CORE: a bearer token, else user and password, else none (local CORE)."""
    token = os.environ.get("DHCORE_ACCESS_TOKEN")
    if token:
        return None, {"Authorization": f"Bearer {token}"}
    user, password = os.environ.get("DHCORE_USER"), os.environ.get("DHCORE_PASSWORD")
    if user and password:
        return (user, password), {}
    return None, {}


def entity_name(name: str) -> str:
    clean = re.sub(r"-+", "-", re.sub(r"[^a-z0-9._+\-]+", "-", (name or "").lower())).strip("-")
    return clean or "tvm-model"
