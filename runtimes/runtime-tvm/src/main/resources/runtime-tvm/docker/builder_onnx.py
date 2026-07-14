#!/usr/bin/env python3
"""ONNX -> Relax IR builder. Exposes from_onnx params + ONNX preprocessing
(shape_inference, version_converter, onnxsim.simplify) as CLI args.

Outputs in --output: model.relax.ir, metadata.json, params.bin (if
--keep-params-in-input).
"""

import argparse
import json
import sys
from pathlib import Path
from typing import Any, Dict, Optional

import onnx
from onnx import shape_inference
import tvm  # noqa: F401  (side-effect: register ops)
from tvm import relax
from tvm.relax.frontend.onnx import from_onnx


# ONNX TensorProto.DataType -> dtype name
_DTYPE_MAP = {
    1: "float32", 2: "uint8", 3: "int8", 4: "uint16", 5: "int16",
    6: "int32", 7: "int64", 9: "bool", 10: "float16", 11: "float64",
    12: "uint32", 13: "uint64",
}


def _tensor_spec(value_info):
    shape = []
    for dim in value_info.type.tensor_type.shape.dim:
        # dim_value == 0 means symbolic (e.g. "batch"); mark -1.
        shape.append(dim.dim_value if dim.dim_value > 0 else -1)
    dtype = _DTYPE_MAP.get(value_info.type.tensor_type.elem_type, "float32")
    return {"name": value_info.name, "shape": shape, "dtype": dtype}


def extract_input_specs(model):
    """Real inputs (excludes weights that ONNX also lists in graph.input).
    Relax requires static shapes at build time, so unknown dims default to 1.
    """
    weight_names = {init.name for init in model.graph.initializer}
    inputs = []
    for inp in model.graph.input:
        if inp.name in weight_names:
            continue
        spec = _tensor_spec(inp)
        spec["shape"] = [d if d > 0 else 1 for d in spec["shape"]]
        inputs.append(spec)
    return inputs


def extract_output_specs(model):
    return [_tensor_spec(out) for out in model.graph.output]


def parse_bool(value: str, default: bool) -> bool:
    if value is None:
        return default
    return value.lower() in ("1", "true", "yes", "y", "on")


def main():
    ap = argparse.ArgumentParser(description="ONNX -> Relax IR + metadata.json")
    ap.add_argument("--input", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--name", default="model")

    # from_onnx parameters
    ap.add_argument("--opset", type=int, default=None,
                    help="opset override forwarded to from_onnx")
    ap.add_argument("--keep-params-in-input", type=str, default="false",
                    help="if true, weights stay as variable inputs (need --params-file at compile)")
    ap.add_argument("--sanitize-input-names", type=str, default="true")

    # ONNX preprocessing (runs before from_onnx)
    ap.add_argument("--target-opset", type=int, default=None,
                    help="convert to this opset via onnx.version_converter before from_onnx")
    ap.add_argument("--simplify", type=str, default="false",
                    help="run onnxsim.simplify (requires onnxsim)")
    ap.add_argument("--strict-shape-inference", type=str, default="false")
    ap.add_argument("--data-prop", type=str, default="false")

    args = ap.parse_args()

    in_path = Path(args.input).resolve()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    if not in_path.exists():
        print(f"ERROR: {in_path} not found", file=sys.stderr)
        sys.exit(2)

    keep_params = parse_bool(args.keep_params_in_input, False)
    sanitize_names = parse_bool(args.sanitize_input_names, True)
    do_simplify = parse_bool(args.simplify, False)
    strict_shape = parse_bool(args.strict_shape_inference, False)
    data_prop = parse_bool(args.data_prop, False)

    print(f"[1/7] Loading {in_path}")
    model = onnx.load(str(in_path))
    print(f"      nodes={len(model.graph.node)} opset={model.opset_import[0].version}")

    # opset conversion runs before shape inference (which is opset-aware)
    if args.target_opset is not None:
        try:
            from onnx import version_converter
            print(f"[2/7] Converting opset → {args.target_opset}")
            model = version_converter.convert_version(model, args.target_opset)
        except Exception as e:  # noqa: BLE001
            print(f"      version_converter failed: {e}", file=sys.stderr)
            sys.exit(3)
    else:
        print("[2/7] (no opset conversion requested)")

    if do_simplify:
        try:
            from onnxsim import simplify as onnx_simplify
            print("[3/7] Running onnxsim.simplify")
            model_simp, ok = onnx_simplify(model)
            if not ok:
                print("      simplify validation failed, using original", file=sys.stderr)
            else:
                model = model_simp
        except ImportError:
            print("      onnxsim not installed, skipping simplify", file=sys.stderr)
    else:
        print("[3/7] (simplify disabled)")

    # populates intermediate shapes (required by from_onnx)
    print(f"[4/7] shape_inference (strict={strict_shape}, data_prop={data_prop})")
    try:
        model = shape_inference.infer_shapes(
            model,
            check_type=False,
            strict_mode=strict_shape,
            data_prop=data_prop,
        )
    except Exception as e:  # noqa: BLE001
        print(f"      shape_inference skipped: {e}", file=sys.stderr)

    print(f"[5/7] ONNX -> Relax IR (opset={args.opset}, "
          f"keep_params_in_input={keep_params}, sanitize_input_names={sanitize_names})")
    mod: Any = from_onnx(
        model,
        shape_dict=None,
        dtype_dict="float32",
        opset=args.opset,
        keep_params_in_input=keep_params,
        sanitize_input_names=sanitize_names,
    )

    # with keep_params_in_input=True, params live inside the module; detach them
    params: Optional[Dict[str, Any]] = None
    if keep_params:
        try:
            mod, params_dict = relax.frontend.detach_params(mod)
            params = params_dict
        except Exception as e:  # noqa: BLE001
            print(f"      detach_params failed: {e}", file=sys.stderr)
            params = None

    # model.relax.json is round-trip safe (preserves constant params); the .ir
    # TVMScript dump is debug-only and cannot be reloaded via from_source when
    # constant params are present (it references internal metadata).
    ir_json = out_dir / "model.relax.json"
    ir_text = out_dir / "model.relax.ir"
    ir_json.write_text(tvm.ir.save_json(mod))
    try:
        ir_text.write_text(mod.script())
    except Exception as e:  # noqa: BLE001
        print(f"      (skipping mod.script() debug dump: {e})")
    print(f"      IR written: {ir_json}")

    if params is not None:
        try:
            from tvm.runtime import save_param_dict
            params_bin = out_dir / "params.bin"
            # save_param_dict is per-function; flatten entries
            flat = {}
            for fn_name, lst in params.items():
                for i, v in enumerate(lst):
                    flat[f"{fn_name}.{i}"] = v
            params_bin.write_bytes(save_param_dict(flat))
            print(f"      params written: {params_bin}")
        except Exception as e:  # noqa: BLE001
            print(f"      save_param_dict failed: {e}", file=sys.stderr)

    print("[6/7] Extracting metadata")
    inputs = extract_input_specs(model)
    outputs = extract_output_specs(model)
    meta = {
        "entry": "main",
        "source_format": "onnx",
        "opset": model.opset_import[0].version,
        "model_name": args.name,
        "keep_params_in_input": keep_params,
        "sanitize_input_names": sanitize_names,
        "inputs": inputs,
        "outputs": outputs,
    }
    (out_dir / "metadata.json").write_text(json.dumps(meta, indent=2))
    for i in inputs:
        print(f"      in  '{i['name']}': {i['dtype']} {i['shape']}")
    for o in outputs:
        print(f"      out '{o['name']}': {o['dtype']} {o['shape']}")

    print("[7/7] publishing Model entity via digitalhub SDK")
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
            "keep_params_in_input": keep_params,
            "sanitize_input_names": sanitize_names,
            "parameters": {
                "opset": meta["opset"],
                "model_name": meta["model_name"],
            },
        },
    )

    print("Done")


if __name__ == "__main__":
    main()
