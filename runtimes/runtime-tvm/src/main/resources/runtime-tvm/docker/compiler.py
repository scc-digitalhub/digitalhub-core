#!/usr/bin/env python3
"""Relax IR -> model.so. --target is forwarded as-is to tvm.target.Target; external libs (cuDNN/cuBLAS) go inline in the target string."""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any, Optional

import tvm
from tvm import relax


def parse_bool(value: str, default: bool) -> bool:
    if value is None:
        return default
    return value.lower() in ("1", "true", "yes", "y", "on")


def load_ir(ir_dir: Path):
    """Loads model.relax.json (round-trip safe); the .ir TVMScript dump is debug-only (can't reload with const params)."""
    json_path = ir_dir / "model.relax.json"
    if json_path.exists():
        return tvm.ir.load_json(json_path.read_text())
    print(f"ERROR: model.relax.json not found in {ir_dir}", file=sys.stderr)
    sys.exit(2)


def _load_param_file(path: Path) -> dict:
    """Loads a params.bin (save_param_dict format) into {name: Tensor}."""
    from tvm.runtime import load_param_dict
    flat = load_param_dict(path.read_bytes())
    # TVM 0.25: params must be a flat Map<str, Tensor>; the old grouped {fn: [v0,v1,...]} makes VMLink reject each value as an ffi.Array.
    return dict(flat.items())


def load_params(ir_dir: Path) -> Optional[dict]:
    """Loads params from params.bin if present."""
    params_path = ir_dir / "params.bin"
    if not params_path.exists():
        return None
    return _load_param_file(params_path)


def _bind_params(mod, flat):
    """Bind detached weights into the entry fn as constants. Keys are either the fn's param names or positional ("<fn>.<i>"); positional keys map onto the trailing params (after the real inputs) by order."""
    entry, func = "main", None
    for gv, f in mod.functions_items():
        if isinstance(f, relax.Function):
            if gv.name_hint == "main":
                entry, func = "main", f
                break
            if func is None:
                entry, func = gv.name_hint, f
    fparams = list(func.params)
    pnames = {v.name_hint for v in fparams}
    if set(flat).issubset(pnames):
        named = dict(flat)                      # keys already are parameter names
    else:                                       # positional keys -> map by order
        num_input = len(fparams) - len(flat)    # leading args are the real inputs
        if num_input < 0:
            raise ValueError(
                f"{len(flat)} saved params > {len(fparams)} function params")
        ordered = [flat[k] for k in sorted(flat, key=lambda k: int(k.rpartition(".")[2]))]
        named = {v.name_hint: t for v, t in zip(fparams[num_input:], ordered)}
    return entry, len(named), relax.transform.BindParams(entry, named)(mod)


def main():
    ap = argparse.ArgumentParser(description="TVM Relax IR -> model.so")
    ap.add_argument("--ir-dir", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--target", required=True,
                    help='TVM target string: "llvm" or JSON dict form, e.g. '
                         '{"kind":"llvm","mcpu":"x86-64-v2"} (TVM 0.24+ dropped '
                         'the CLI form "llvm -mcpu=...")')
    ap.add_argument("--opt-level", type=int, default=3)
    ap.add_argument("--cross-cc", default=None,
                    help="cross C++ compiler (e.g. aarch64-linux-gnu-g++, arm-linux-gnueabihf-g++)")
    ap.add_argument("--exec-mode", default="bytecode",
                    choices=["bytecode", "compiled"])
    ap.add_argument("--relax-pipeline", default="default")
    ap.add_argument("--tir-pipeline", default="default")
    ap.add_argument("--system-lib", default="false")
    ap.add_argument("--params-file", default=None,
                    help="params.bin (if IR was built with keep_params_in_input=true)")
    ap.add_argument("--tag", default=None, help="recorded in metadata")
    args = ap.parse_args()

    ir_dir = Path(args.ir_dir).resolve()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)
    system_lib = parse_bool(args.system_lib, False)

    print(f"[1/4] Loading IR from {ir_dir}")
    mod = load_ir(ir_dir)

    params = None
    if args.params_file:
        pf = Path(args.params_file)
        if not pf.exists():
            print(f"ERROR: --params-file {pf} not found", file=sys.stderr)
            sys.exit(3)
        try:
            params = _load_param_file(pf)
        except Exception as e:  # noqa: BLE001
            print(f"WARN: failed to load params: {e}", file=sys.stderr)
    else:
        # auto-detect a sibling params.bin
        params = load_params(ir_dir)
        if params:
            print("      auto-detected params.bin in ir-dir")

    print(f"[2/4] Building (target='{args.target}', opt_level={args.opt_level}, "
          f"exec_mode={args.exec_mode}, relax_pipeline={args.relax_pipeline})")
    target = tvm.target.Target(args.target)

    build_kwargs: Any = {
        "target": target,
        "relax_pipeline": args.relax_pipeline,
        "tir_pipeline": args.tir_pipeline,
        "exec_mode": args.exec_mode,
    }
    if system_lib:
        build_kwargs["system_lib"] = True

    # relax.build does NOT fold params (TVM 0.25); the explicit BindParams below replaces the weight Vars with constants so the served model takes only the real inputs.
    if params:
        entry, n_bound, mod = _bind_params(mod, params)
        print(f"      bound {n_bound} params into '{entry}()'")

    pass_ctx = tvm.transform.PassContext(opt_level=args.opt_level)
    with pass_ctx:
        ex = relax.build(mod, **build_kwargs)

    print("[3/4] Exporting library -> model.so")
    so_path = out_dir / "model.so"
    export_kwargs: dict = {}
    if args.cross_cc:
        export_kwargs["cc"] = args.cross_cc
        print(f"      cross-cc: {args.cross_cc}")
    ex.export_library(str(so_path), **export_kwargs)
    size_mb = so_path.stat().st_size / 1e6
    print(f"      written: {so_path} ({size_mb:.1f} MB)")

    print("[4/4] Updating metadata.json")
    meta_in = ir_dir / "metadata.json"
    meta_out = out_dir / "metadata.json"
    if meta_in.exists():
        meta = json.loads(meta_in.read_text())
    else:
        meta = {"entry": "main"}
    meta["target"] = args.target
    meta["opt_level"] = args.opt_level
    meta["exec_mode"] = args.exec_mode
    meta["relax_pipeline"] = args.relax_pipeline
    meta["tir_pipeline"] = args.tir_pipeline
    if system_lib:
        meta["system_lib"] = True
    if args.tag:
        meta["tag"] = args.tag
    meta_out.write_text(json.dumps(meta, indent=2))
    print(f"      written: {meta_out}")

    print("[5/5] publishing Model entity via digitalhub SDK")
    sys.path.insert(0, str(Path(__file__).parent))
    from _dh_publish import publish_model_and_register_output

    func_name = os.environ.get("TVM_FUNCTION_NAME", "model")
    # compiled Model name suffix: "so" by default, overridable via task `tag` -> "<name>-<tag>".
    tag = args.tag or "so"
    source_ir = os.environ.get("TVM_SOURCE_IR_KEY") or None

    publish_model_and_register_output(
        out_dir=out_dir,
        name=f"{func_name}-{tag}",
        output_key="compiled_so",
        kind="tvm-so",
        spec={
            "framework": "tvm",
            "algorithm": "tvm-compiled-so",
            "entry": meta.get("entry", "main"),
            "inputs": meta.get("inputs"),
            "outputs": meta.get("outputs"),
            "target": args.target,
            "opt_level": args.opt_level,
            "manifest": meta,
        },
        relationship_source=source_ir,
    )

    print("Done")


if __name__ == "__main__":
    main()
