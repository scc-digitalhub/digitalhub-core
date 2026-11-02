# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Shared build/compile helpers, with CLI flags defaulting to CORE's TVM_* variables."""

from __future__ import annotations

import hashlib
import json
import os
import sys
from pathlib import Path
from typing import Any, Callable, Optional

HOME_DIR = Path(os.environ.get("TVM_HOME_DIR", "/shared"))
INPUT_DIR = Path(os.environ.get("TVM_INPUT_DIR", HOME_DIR / "input"))
OUTPUT_DIR = Path(os.environ.get("TVM_OUTPUT_DIR", HOME_DIR / "output"))


def to_bool(text: str) -> bool:
    return str(text).strip().lower() in ("1", "true", "yes", "y", "on")


def to_list(text: str) -> Optional[list[str]]:
    items = [item.strip() for item in str(text).split(",") if item.strip()]
    return items or None


def option(
    parser,
    flag: str,
    env: str,
    *,
    type: Callable[[str], Any] = str,
    default: Any = None,
    choices: Optional[list[str]] = None,
    help: str = "",
    active: bool = True,
) -> None:
    """Use a nonempty environment value as the CLI default."""
    value = os.environ.get(env) if active else None
    parser.add_argument(
        flag,
        type=type if active else (lambda _: None),
        default=(value if value not in (None, "") else default) if active else None,
        choices=choices if active else None,
        help=f"{help} (env {env}, default {default})".strip(),
    )


def step(number: int, total: int, text: str) -> None:
    print(f"[{number}/{total}] {text}", flush=True)


def fail(message: str, code: int = 2) -> None:
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


def target_triple(target) -> Optional[str]:
    """Return the effective LLVM triple, or None for non-LLVM targets."""
    import tvm

    if target.kind.name != "llvm":
        return None
    return str(target.attrs.get("mtriple") or tvm.target.codegen.llvm_get_system_triple())


def print_signature(inputs: list[dict], outputs: list[dict]) -> None:
    for spec in inputs:
        print(f"      in  '{spec['name']}': {spec['dtype']} {spec['shape']}")
    for spec in outputs:
        print(f"      out '{spec['name']}': {spec['dtype']} {spec['shape']}")


def source_model_file(input_dir: Path, file_name: str, extension: str) -> Path:
    """Find the named source, falling back to a unique file with the requested extension."""
    path = Path(input_dir) / file_name
    if path.is_file():
        return path
    candidates = [p for p in Path(input_dir).rglob(f"*{extension}") if p.is_file()]
    if len(candidates) == 1:
        return candidates[0]
    fail(f"source model {path} not found ({len(candidates)} *{extension} files in {input_dir})")


def save_relax_ir(mod, out_dir: Path, weights: Optional[dict] = None) -> None:
    """Save round-trip JSON, a readable IR dump and optional detached weights."""
    import tvm

    ir_json = out_dir / "model.relax.json"
    ir_json.write_text(tvm.ir.save_json(mod))
    try:
        # TVMScript is diagnostic only: constant weights cannot be loaded back.
        (out_dir / "model.relax.ir").write_text(mod.script())
    except Exception as error:  # noqa: BLE001
        print(f"      no readable IR dump: {error}")
    print(f"      written: {ir_json}")

    if weights:
        from tvm.runtime import save_param_dict

        # Keep positional keys for compile_model.py to restore parameter order.
        flat = {f"{function}.{i}": value for function, values in weights.items() for i, value in enumerate(values)}
        (out_dir / "params.bin").write_bytes(save_param_dict(flat))
        print(f"      written: {out_dir / 'params.bin'} ({len(flat)} weights)")
