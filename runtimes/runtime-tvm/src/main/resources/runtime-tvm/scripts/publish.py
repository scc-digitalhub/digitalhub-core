# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Publishes the result of a Job as a typed Model and tells CORE about it.

tvm+build publishes a Model of kind tvm-ir, tvm+compile one of kind tvm-so: the kind
always matches what the folder contains. The Model key is then written into
run.status.outputs, where CORE reads it to fill the function fields ir_model and so_model.

Credentials for CORE (DHCORE_*) and S3 (AWS_*) come from the variables set on the Job.
"""

from __future__ import annotations

import os
import re
import time
from pathlib import Path
from typing import Any, Optional

# The SDK looks for the current run when it sees RUN_ID, and it has no Python class for the
# TVM runs: the id is taken out before importing it and kept for the status update.
RUN_ID = os.environ.pop("RUN_ID", None)

import digitalhub as dh  # noqa: E402

# Model kind -> the run output CORE reads and the spec fields of the kind in CORE
# (TvmIrModelSpec, TvmSoModelSpec). path, framework, algorithm and parameters are common.
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
    """Publishes the Relax IR written by tvm+build as the Model <name>-ir (kind tvm-ir)."""
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
    """Publishes model.so written by tvm+compile as the Model <name> (kind tvm-so), linked to
    the IR Model it was compiled from."""
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
    """Uploads folder as a Model of the given kind, records it as the run output and
    returns its key."""
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
        # The Model exists: only the automatic chaining to the next task is lost.
        print(f"WARN: cannot update the run status: {error}")
    return model.key


def enable_model_kind(kind: str) -> None:
    """Makes sure the installed SDK can log this Model kind.

    The kinds tvm-ir and tvm-so are in the SDK source but not in every release. When the
    SDK lacks one, it is registered here as the generic model builder with the extra spec
    fields of the kind. If even that is impossible the Job fails: a wrong kind would break
    the chain of tasks and hide the model type.
    """
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
    """Records that the new Model was produced from source_key (CONSUMES relationship)."""
    try:
        from digitalhub.entities._commons.enums import Relationship

        model.add_relationship(relation=Relationship.CONSUMES.value, dest=source_key)
        model.save(update=True)
        print(f"      relationship CONSUMES -> {source_key}")
    except Exception as error:  # noqa: BLE001
        print(f"WARN: cannot link the source Model: {error}")


def set_run_output(project: str, run_id: str, output: str, value: str) -> None:
    """Sets run.status.outputs.<output> through the REST API.

    CORE has no PATCH for runs: the run is read, changed and written back, trying again
    when another update gets in between.
    """
    import requests

    url = f"{os.environ['DHCORE_ENDPOINT'].rstrip('/')}/api/v1/-/{project}/runs/{run_id}"
    auth, headers = core_auth()
    response = None
    for attempt in range(3):
        run = requests.get(url, auth=auth, headers=headers, timeout=15)
        run.raise_for_status()
        body = run.json()
        status = body.get("status") or {}
        status["outputs"] = {**(status.get("outputs") or {}), output: value}
        body["status"] = status

        response = requests.put(url, json=body, auth=auth, headers={**headers, "Content-Type": "application/json"}, timeout=15)
        if response.status_code < 400:
            return
        if response.status_code not in (409, 412, 500, 502, 503):
            break
        time.sleep(0.5 * (attempt + 1))
    response.raise_for_status()


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
    """A valid entity name: lowercase, anything outside [a-z0-9._+-] becomes '-'."""
    clean = re.sub(r"-+", "-", re.sub(r"[^a-z0-9._+\-]+", "-", (name or "").lower())).strip("-")
    return clean or "tvm-model"
