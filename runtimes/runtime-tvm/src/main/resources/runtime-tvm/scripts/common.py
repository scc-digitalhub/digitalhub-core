# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""What the tvm+build and tvm+compile scripts share.

Every option can be given on the command line or through the TVM_* variable that CORE sets
on the Job, so the pod needs no wrapper translating variables into flags.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from pathlib import Path
from typing import Any, Callable, Optional

HOME_DIR = Path(os.environ.get("TVM_HOME_DIR", "/shared"))
INPUT_DIR = Path(os.environ.get("TVM_INPUT_DIR", HOME_DIR / "input"))
OUTPUT_DIR = Path(os.environ.get("TVM_OUTPUT_DIR", HOME_DIR / "output"))


# --------------------------------------------------------------------------- options


def to_bool(text: str) -> bool:
    """Reads "true", "1", "yes", "on" (any case) as true, anything else as false."""
    return str(text).strip().lower() in ("1", "true", "yes", "y", "on")


def to_list(text: str) -> Optional[list[str]]:
    """Reads a comma-separated list; an empty text gives None."""
    items = [item.strip() for item in str(text).split(",") if item.strip()]
    return items or None


def option(
    parser: argparse._ActionsContainer,
    flag: str,
    env: str,
    *,
    type: Callable[[str], Any] = str,
    default: Any = None,
    choices: Optional[list[str]] = None,
    help: str = "",
) -> None:
    """Adds --flag, whose default comes from the environment variable env when it is set."""
    value = os.environ.get(env)
    parser.add_argument(
        flag,
        type=type,
        default=value if value not in (None, "") else default,
        choices=choices,
        help=f"{help} (env {env}, default {default})".strip(),
    )


# --------------------------------------------------------------------------- messages and files


def step(number: int, total: int, text: str) -> None:
    print(f"[{number}/{total}] {text}", flush=True)


def fail(message: str, code: int = 2) -> None:
    """Stops the Job with a readable error."""
    print(f"ERROR: {message}", file=sys.stderr, flush=True)
    raise SystemExit(code)


def read_json(path: Path) -> dict:
    try:
        value = json.loads(Path(path).read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"invalid JSON file {path}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"expected a JSON object in {path}")
    return value


def write_json(path: Path, value: dict) -> None:
    Path(path).write_text(json.dumps(value, indent=2))


def sha256_of(path: Path) -> str:
    with Path(path).open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def print_signature(inputs: list[dict], outputs: list[dict]) -> None:
    for spec in inputs:
        print(f"      in  '{spec['name']}': {spec['dtype']} {spec['shape']}")
    for spec in outputs:
        print(f"      out '{spec['name']}': {spec['dtype']} {spec['shape']}")


def source_model_file(input_dir: Path, file_name: str, extension: str) -> Path:
    """The source model downloaded by the init container.

    When the source was a folder the expected name may not exist: then the only file with
    the right extension in the folder is used.
    """
    path = Path(input_dir) / file_name
    if path.is_file():
        return path
    candidates = [p for p in Path(input_dir).rglob(f"*{extension}") if p.is_file()]
    if len(candidates) == 1:
        return candidates[0]
    fail(f"source model {path} not found ({len(candidates)} *{extension} files in {input_dir})")


# --------------------------------------------------------------------------- Relax IR


def save_relax_ir(mod, out_dir: Path, weights: Optional[dict] = None) -> None:
    """Writes model.relax.json (read back by compile_model.py), the readable dump
    model.relax.ir and, when the weights are kept apart, params.bin."""
    import tvm

    ir_json = out_dir / "model.relax.json"
    ir_json.write_text(tvm.ir.save_json(mod))
    try:
        # For people only: TVMScript cannot be loaded back when the weights are constants.
        (out_dir / "model.relax.ir").write_text(mod.script())
    except Exception as error:  # noqa: BLE001
        print(f"      no readable IR dump: {error}")
    print(f"      written: {ir_json}")

    if weights:
        from tvm.runtime import save_param_dict

        # save_param_dict wants a flat dict: each weight is keyed "<function>.<position>",
        # the positional form compile_model.py binds back in order.
        flat = {f"{function}.{i}": value for function, values in weights.items() for i, value in enumerate(values)}
        (out_dir / "params.bin").write_bytes(save_param_dict(flat))
        print(f"      written: {out_dir / 'params.bin'} ({len(flat)} weights)")
