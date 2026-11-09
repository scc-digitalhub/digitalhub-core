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
from typing import Any, BinaryIO
from urllib.parse import quote

import requests

MODEL_OUTPUTS = {"tvm-ir": "ir_module", "tvm-so": "compiled_so"}
RETRYABLE = {408, 409, 412, 429, 500, 502, 503, 504}
PART_SIZE = 64 * 1024 * 1024
MAX_FILES_IN_STATUS = 100


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


def publish_compiled_model(out_dir: Path, name: str, metadata: dict, source_ir_key: str | None) -> str:
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


def core_error_message(response: requests.Response) -> str:
    """Keep CORE's reason bounded and free of credentials or signed URLs."""
    try:
        body = response.json()
    except ValueError:
        return ""
    message = body.get("message") if isinstance(body, dict) else None
    if not isinstance(message, str):
        return ""
    for name in ("DHCORE_ACCESS_TOKEN", "DHCORE_PASSWORD", "AWS_SECRET_ACCESS_KEY", "AWS_SESSION_TOKEN"):
        if secret := os.environ.get(name):
            message = message.replace(secret, "<redacted>")
    message = re.sub(r"(?:https?|s3)://[^\s\"'<>]+", "<url>", message)
    return " ".join(message.split())[:1000]


def core_request(method: str, path: str, *, timeout: int = 30, **kwargs) -> Any:
    auth, headers = core_auth()
    url = os.environ["DHCORE_ENDPOINT"].rstrip("/") + "/api/v1/-/" + path
    response = requests.request(method, url, auth=auth, headers=headers, timeout=timeout, **kwargs)
    try:
        response.raise_for_status()
    except requests.HTTPError:
        message = core_error_message(response)
        detail = f": {message}" if message else ""
        raise requests.HTTPError(
            f"CORE {method} {path}: HTTP {response.status_code}{detail}",
            response=response, request=response.request,
        ) from None
    return response.json() if response.content else None


def send_file(info: dict, content: BinaryIO, filename: str) -> requests.Response:
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


def find_or_create_model(collection: str, descriptor: dict) -> dict:
    """Recover a lost POST response using the same ID."""
    api = collection + "/" + descriptor["id"]
    try:
        return core_request("GET", api)
    except requests.HTTPError as error:
        if error.response is None or error.response.status_code != 404:
            raise

    try:
        return core_request("POST", collection, json=descriptor)
    except requests.HTTPError as error:
        if error.response is None or error.response.status_code != 400:
            raise
        # CORE also reports duplicate IDs as HTTP 400 after a lost POST response.
        try:
            return core_request("GET", api)
        except requests.RequestException:
            raise error from None


def register_model(collection: str, descriptor: dict) -> dict:
    """Confirm identity and storage, retrying transient failures with the same ID."""
    for attempt in range(3):
        try:
            model = find_or_create_model(collection, descriptor)
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


def update_model_status(api: str, model: dict, state: str, **fields) -> dict:
    model = {**model, "status": {**(model.get("status") or {}), **fields, "state": state}}
    return core_request("PUT", api, json=model)


def publish_model(kind: str, name: str, folder: Path, spec: dict, consumes: str | None = None) -> str:
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
    base_path = core_request("POST", api + "/files/upload", params={"name": name})["path"]
    descriptor = {
        "id": model_id, "project": project, "name": name, "kind": kind,
        "spec": {**spec, "path": base_path},
        "metadata": {"relationships": [{"type": "CONSUMES", "dest": consumes}]} if consumes else {},
    }
    model = register_model(collection, descriptor)
    key = model["key"]
    print(f"      Model created: {key}")
    model = update_model_status(api, model, "UPLOADING")
    try:
        file_info = []
        for source in files:
            filename = source.relative_to(folder).as_posix()
            path = upload_file(api, source, name, filename)
            if path != base_path.rstrip("/") + "/" + filename:
                raise RuntimeError("CORE returned an unexpected upload path")
            info = core_request("GET", quote(project, safe="") + "/files/info", params={"path": path})["content"]
            if len(info) != 1:
                raise RuntimeError(f"CORE did not confirm uploaded file {filename}")
            file_info.append({**info[0], "path": filename})

        if len(file_info) > MAX_FILES_IN_STATUS:
            model = update_model_status(api, model, "UPLOADING", files=[])
            core_request("PUT", api + "/files/info", json=file_info)
            file_info = []
    except Exception:  # noqa: BLE001
        try:
            update_model_status(api, model, "ERROR", message="Upload failed; see the run logs.")
        except Exception as status_error:  # noqa: BLE001
            print(f"      Could not mark Model {key} as ERROR: {type(status_error).__name__}")
        raise

    model = update_model_status(api, model, "READY", files=file_info, message=None)
    if model.get("status", {}).get("state") != "READY" or "files" not in model.get("status", {}):
        raise RuntimeError(f"CORE did not confirm Model {key} readiness and file information")
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
            response = error.response
            # The monitor can advance the run between GET and PUT.
            stale_state = (
                response is not None and response.status_code == 400
                and core_error_message(response) == "Invalid transition from state RUNNING to state PENDING."
            )
            if response is None or (response.status_code not in RETRYABLE and not stale_state) or attempt == 2:
                raise
        except (requests.ConnectionError, requests.Timeout):
            if attempt == 2:
                raise
        time.sleep(0.5 * (attempt + 1))


def core_auth() -> tuple[tuple[str, str] | None, dict[str, str]]:
    token = os.environ.get("DHCORE_ACCESS_TOKEN")
    if token:
        return None, {"Authorization": f"Bearer {token}"}
    user, password = os.environ.get("DHCORE_USER"), os.environ.get("DHCORE_PASSWORD")
    if user and password:
        return (user, password), {}
    return None, {}


def entity_name(name: str) -> str:
    clean = re.sub(r"-+", "-", re.sub(r"[^a-z0-9._+\-]+", "-", (name or "").lower())).strip("-")
    clean = re.sub(r"_{2,}", "_", clean)
    return clean if clean and clean not in (".", "..") else "tvm-model"
