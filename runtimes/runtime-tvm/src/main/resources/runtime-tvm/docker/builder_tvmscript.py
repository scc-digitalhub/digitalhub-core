#!/usr/bin/env python3
"""TVMScript -> Relax IR builder.

User file must expose either:
  - `Module` decorated with @I.ir_module, or
  - `get_module()` returning a tvm.ir.IRModule
First match wins.
"""

import argparse
import importlib.util
import json
import sys
from pathlib import Path

import tvm  # noqa: F401
from tvm.ir import IRModule


def load_user_module(path: Path):
    spec = importlib.util.spec_from_file_location("user_tvm_module", str(path))
    if spec is None or spec.loader is None:
        sys.exit(f"ERROR: cannot import {path}")
    mod = importlib.util.module_from_spec(spec)
    sys.modules["user_tvm_module"] = mod
    spec.loader.exec_module(mod)
    return mod


def extract_irmodule(user_mod) -> IRModule:
    if hasattr(user_mod, "Module"):
        candidate = getattr(user_mod, "Module")
        if isinstance(candidate, IRModule):
            return candidate
        # some versions wrap @I.ir_module; try the ir_module() accessor
        if hasattr(candidate, "ir_module") and callable(candidate.ir_module):
            return candidate.ir_module()

    if hasattr(user_mod, "get_module") and callable(user_mod.get_module):
        result = user_mod.get_module()
        if isinstance(result, IRModule):
            return result
        sys.exit("ERROR: get_module() must return a tvm.ir.IRModule")

    sys.exit(
        "ERROR: user module must expose either 'Module' (decorated with @I.ir_module) "
        "or 'get_module() -> IRModule'"
    )


def discover_signature(mod: IRModule):
    """Best-effort: extract main()'s signature from the Relax IR."""
    inputs = []
    outputs = []
    try:
        from tvm import relax
        for gv, fn in mod.functions_items():
            if gv.name_hint != "main":
                continue
            if not isinstance(fn, relax.Function):
                continue
            for p in fn.params:
                struct = getattr(p, "struct_info", None)
                if struct is None:
                    continue
                shape = []
                if hasattr(struct, "shape") and struct.shape is not None:
                    for v in struct.shape.values:
                        try:
                            shape.append(int(v))
                        except Exception:  # noqa: BLE001
                            shape.append(-1)
                dtype = str(getattr(struct, "dtype", "float32"))
                inputs.append({"name": p.name_hint, "shape": shape, "dtype": dtype})
            ret = getattr(fn, "ret_struct_info", None)
            if ret is not None:
                # A tuple return means multiple outputs: emit one entry per element
                # (output0, output1, ...) with each element's own shape/dtype,
                # mirroring builder_pytorch.
                rets = list(ret.fields) if isinstance(ret, relax.TupleStructInfo) else [ret]
                for i, si in enumerate(rets):
                    shape = []
                    if hasattr(si, "shape") and si.shape is not None:
                        for v in si.shape.values:
                            try:
                                shape.append(int(v))
                            except Exception:  # noqa: BLE001
                                shape.append(-1)
                    dtype = str(getattr(si, "dtype", "float32"))
                    outputs.append({"name": f"output{i}", "shape": shape, "dtype": dtype})
            break
    except Exception as e:  # noqa: BLE001
        print(f"WARN: signature discovery failed: {e}", file=sys.stderr)
    return inputs, outputs


def main():
    ap = argparse.ArgumentParser(description="TVMScript -> Relax IR + metadata.json")
    ap.add_argument("--input", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--name", default="model")
    args = ap.parse_args()

    in_path = Path(args.input).resolve()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    if not in_path.exists():
        print(f"ERROR: {in_path} not found", file=sys.stderr)
        sys.exit(2)

    print(f"[1/4] Importing user module from {in_path}")
    user_mod = load_user_module(in_path)
    mod = extract_irmodule(user_mod)
    print(f"      IRModule loaded: {type(mod).__name__}")

    # The whole downstream chain (metadata 'entry', compiler.py, the serve VMs)
    # expects a Relax function named 'main': fail here with a clear message
    # instead of producing an IR that breaks later at compile/serve time.
    from tvm import relax as _relax
    has_main = any(
        gv.name_hint == "main" and isinstance(fn, _relax.Function)
        for gv, fn in mod.functions_items()
    )
    if not has_main:
        names = [gv.name_hint for gv, _ in mod.functions_items()]
        sys.exit(
            f"ERROR: the IRModule has no Relax function named 'main' (found: {names}). "
            "Rename your entry function to 'main' (@R.function def main(...))."
        )

    print("[2/4] Writing IR")
    ir_json = out_dir / "model.relax.json"
    ir_text = out_dir / "model.relax.ir"
    ir_json.write_text(tvm.ir.save_json(mod))
    try:
        ir_text.write_text(mod.script())
    except Exception as e:  # noqa: BLE001
        print(f"      (skipping mod.script() debug dump: {e})")
    print(f"      IR written: {ir_json}")

    print("[3/4] Extracting metadata")
    inputs, outputs = discover_signature(mod)
    meta = {
        "entry": "main",
        "source_format": "tvmscript",
        "model_name": args.name,
        "inputs": inputs,
        "outputs": outputs,
    }
    (out_dir / "metadata.json").write_text(json.dumps(meta, indent=2))
    for i in inputs:
        print(f"      in  '{i['name']}': {i['dtype']} {i['shape']}")
    for o in outputs:
        print(f"      out '{o['name']}': {o['dtype']} {o['shape']}")

    print("[4/4] publishing Model entity via digitalhub SDK")
    sys.path.insert(0, str(Path(__file__).parent))
    from _dh_publish import publish_model_and_register_output

    publish_model_and_register_output(
        out_dir=out_dir,
        name=f"{args.name}-ir",
        output_key="ir_module",
        kind="tvm-ir",
        spec={
            "framework": "tvm",
            "algorithm": "tvm-relax-ir",
            "entry": meta["entry"],
            "inputs": inputs,
            "outputs": outputs,
            "source_format": meta["source_format"],
            "parameters": {
                "model_name": meta["model_name"],
            },
        },
    )

    print("Done")


if __name__ == "__main__":
    main()
