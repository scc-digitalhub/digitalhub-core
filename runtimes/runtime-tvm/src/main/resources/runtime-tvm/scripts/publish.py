# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Publish TVM artifacts through CORE HTTP APIs and signed storage URLs."""

from __future__ import annotations

import io
import os
import re
import time
import uuid
from pathlib import Path
from typing import Optional
from urllib.parse import quote

import requests

MODEL_OUTPUTS = {"tvm-ir": "ir_module", "tvm-so": "compiled_so"}
RETRYABLE = {408, 409, 412, 429, 500, 502, 503, 504}
PART_SIZE = 64 * 1024 * 1024


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


def core_request(method: str, path: str, *, timeout: int = 30, **kwargs):
    auth, headers = core_auth()
    url = os.environ["DHCORE_ENDPOINT"].rstrip("/") + "/api/v1/-/" + path
    response = requests.request(method, url, auth=auth, headers=headers, timeout=timeout, **kwargs)
    response.raise_for_status()
    return response.json()


def send_file(info: dict, content, filename: str):
    """Never forward CORE credentials or expose a signed URL in an upload error."""
    empty = content.seek(0, os.SEEK_END) == 0
    for attempt in range(3):
        try:
            content.seek(0)
            response = requests.request(info["method"], info["url"], data=b"" if empty else content, timeout=(15, 120))
            response.raise_for_status()
            return response
        except requests.RequestException as error:
            status = error.response.status_code if error.response is not None else None
            transient = isinstance(error, (requests.ConnectionError, requests.Timeout)) or status in RETRYABLE
            if not transient or attempt == 2:
                raise RuntimeError(f"Upload failed for {filename}: HTTP {status}" if status else
                                   f"Upload failed for {filename}: {type(error).__name__}") from None
        time.sleep(0.5 * (attempt + 1))


def upload_file(api: str, source: Path, name: str, filename: str) -> str:
    size = source.stat().st_size
    params = {"name": name, "filename": filename}
    if size <= PART_SIZE:
        info = core_request("POST", api + "/files/upload", params=params)
        with source.open("rb") as content:
            send_file(info, content, filename)
        return info["path"]

    info = core_request("POST", api + "/files/multipart/start", params=params)
    upload = {"path": info["path"], "uploadId": info["uploadId"]}
    etags = []
    part_size = max(PART_SIZE, (size + 9999) // 10000)
    with source.open("rb") as content:
        while chunk := content.read(part_size):
            part = core_request("PUT", api + "/files/multipart/part",
                                params={**upload, "partNumber": len(etags) + 1})
            response = send_file(part, io.BytesIO(chunk), filename)
            etag = response.headers.get("ETag")
            if not etag:
                raise RuntimeError(f"Upload of {filename} returned no ETag")
            etags.append(etag)
    core_request("POST", api + "/files/multipart/complete", data={**upload, "partList": etags})
    return info["path"]


def register_model(collection: str, descriptor: dict) -> dict:
    """Recover a lost POST response using the same model ID, without re-uploading."""
    api = collection + "/" + descriptor["id"]
    for attempt in range(3):
        try:
            try:
                model = core_request("GET", api)
            except requests.HTTPError as error:
                if error.response is None or error.response.status_code != 404:
                    raise
                model = core_request("POST", collection, json=descriptor)
            if any(model.get(field) != descriptor[field] for field in ("id", "kind", "name", "project")):
                raise RuntimeError("CORE returned a different model identity")
            if model.get("spec", {}).get("path") != descriptor["spec"]["path"]:
                raise RuntimeError("CORE did not confirm the model storage path")
            return model
        except requests.HTTPError as error:
            if error.response is None or error.response.status_code not in RETRYABLE or attempt == 2:
                raise
        except (requests.ConnectionError, requests.Timeout):
            if attempt == 2:
                raise
        time.sleep(0.5 * (attempt + 1))


def publish_model(kind: str, name: str, folder: Path, spec: dict, consumes: Optional[str] = None) -> str:
    project = os.environ["PROJECT_NAME"]
    run_id = os.environ.get("RUN_ID")
    if not run_id:
        raise RuntimeError("RUN_ID is missing: the run status cannot be updated")
    output = MODEL_OUTPUTS[kind]
    files = sorted(path for path in folder.rglob("*") if path.is_file())
    if not files:
        raise ValueError(f"No model artifacts in {folder}")

    name = entity_name(name)
    model_id = uuid.uuid4().hex
    collection = quote(project, safe="") + "/models"
    api = collection + "/" + model_id
    base_path = None
    for source in files:
        filename = source.relative_to(folder).as_posix()
        path = upload_file(api, source, name, filename)
        if not path.endswith("/" + filename):
            raise RuntimeError("CORE returned an unexpected upload path")
        root = path[:-len(filename)]
        if base_path is not None and root != base_path:
            raise RuntimeError("CORE returned different storage folders for the same model")
        base_path = root

    descriptor = {
        "id": model_id, "project": project, "name": name, "kind": kind,
        "spec": {**spec, "path": base_path},
        "metadata": {"relationships": [{"type": "CONSUMES", "dest": consumes}]} if consumes else {},
    }
    model = register_model(collection, descriptor)
    key = model["key"]
    print(f"      Model created: {key}")
    try:
        set_run_output(project, run_id, output, key)
    except Exception as error:  # noqa: BLE001
        raise RuntimeError(f"Model {key} was created, but run {run_id} output {output} could not be recorded") from error
    print(f"      run.status.outputs.{output} = {key}")
    return key


def set_run_output(project: str, run_id: str, output: str, value: str) -> None:
    """Merge and verify outputs; CORE's GET/PUT is not an atomic compare-and-swap."""
    path = f"{quote(project, safe='')}/runs/{quote(run_id, safe='')}"

    for attempt in range(3):
        try:
            body = core_request("GET", path, timeout=15)
            status = body.get("status") or {}
            if (status.get("outputs") or {}).get(output) == value:
                return
            status["outputs"] = {**(status.get("outputs") or {}), output: value}
            body["status"] = status
            core_request("PUT", path, json=body, timeout=15)
            confirmed = core_request("GET", path, timeout=15)
            if ((confirmed.get("status") or {}).get("outputs") or {}).get(output) == value:
                return
            if attempt == 2:
                raise RuntimeError(f"CORE did not confirm run {run_id} output {output} = {value}")
        except requests.HTTPError as error:
            if error.response is None or error.response.status_code not in RETRYABLE or attempt == 2:
                raise
        except (requests.ConnectionError, requests.Timeout):
            if attempt == 2:
                raise
        time.sleep(0.5 * (attempt + 1))


def core_auth():
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
