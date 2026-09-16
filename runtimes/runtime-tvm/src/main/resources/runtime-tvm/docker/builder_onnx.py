#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""tvm+build for ONNX models: ONNX -> Relax IR.

The steps, in order:
1. load the ONNX file;
2. optionally convert it to another opset and simplify it with onnxsim;
3. run ONNX shape inference, which from_onnx needs for the intermediate shapes;
4. convert it to Relax IR with from_onnx;
5. save model.relax.json (plus params.bin when the weights stay separate) and metadata.json;
6. publish the folder as a tvm-ir Model.
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any, Dict, Optional

import onnx
from onnx import shape_inference
import tvm  # noqa: F401  (importing registers the Relax operators)
from tvm import relax
from tvm.relax.frontend.onnx import from_onnx


# ONNX TensorProto.DataType -> dtype name
_DTYPE_MAP = {
    1: "float32", 2: "uint8", 3: "int8", 4: "uint16", 5: "int16",
    6: "int32", 7: "int64", 9: "bool", 10: "float16", 11: "float64",
    12: "uint32", 13: "uint64",
}


def parse_bool(value: Optional[str], default: bool) -> bool:
    """Reads the true/false strings forwarded by entrypoint.sh ("true", "1", "yes", ...)."""
    if value is None:
        return default
    return value.lower() in ("1", "true", "yes", "y", "on")


def parse_args() -> argparse.Namespace:
    ap = argparse.ArgumentParser(description="ONNX -> Relax IR + metadata.json")
    ap.add_argument("--input", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--name", default="model")

    # from_onnx options
    ap.add_argument("--opset", type=int, default=None, help="opset override forwarded to from_onnx")
    ap.add_argument("--keep-params-in-input", type=str, default="false",
                    help="if true, weights stay as separate inputs saved in params.bin")
    ap.add_argument("--sanitize-input-names", type=str, default="true")

    # ONNX preprocessing, applied before from_onnx
    ap.add_argument("--target-opset", type=int, default=None,
                    help="convert to this opset with onnx.version_converter first")
    ap.add_argument("--simplify", type=str, default="false", help="run onnxsim.simplify")
    ap.add_argument("--strict-shape-inference", type=str, default="false")
    ap.add_argument("--data-prop", type=str, default="false")

    args = ap.parse_args()
    args.keep_params_in_input = parse_bool(args.keep_params_in_input, False)
    args.sanitize_input_names = parse_bool(args.sanitize_input_names, True)
    args.simplify = parse_bool(args.simplify, False)
    args.strict_shape_inference = parse_bool(args.strict_shape_inference, False)
    args.data_prop = parse_bool(args.data_prop, False)
    return args


# --------------------------------------------------------------------------- tensor signatures


def _tensor_spec(value_info) -> dict:
    """name, shape and dtype of an ONNX graph input or output. Symbolic dims become -1."""
    shape = [dim.dim_value if dim.dim_value > 0 else -1 for dim in value_info.type.tensor_type.shape.dim]
    dtype = _DTYPE_MAP.get(value_info.type.tensor_type.elem_type, "float32")
    return {"name": value_info.name, "shape": shape, "dtype": dtype}


def _quant_params(model, tensor_name: str, is_output: bool) -> dict:
    """Affine quantization params of a quantized boundary tensor, read from its QDQ node.

    Quantization and source format are independent: ONNX carries the same information as
    TFLite, only in a different place. TFLite keeps the params on the tensor; ONNX QDQ keeps
    them on the node. An int8 graph input feeds a DequantizeLinear, an int8 graph output
    comes out of a QuantizeLinear, and inputs 1 and 2 of both are scale and zero_point.
    """
    from onnx import numpy_helper

    op = "QuantizeLinear" if is_output else "DequantizeLinear"
    initializers = {init.name: init for init in model.graph.initializer}
    for node in model.graph.node:
        edge = node.output[0] if is_output else (node.input[0] if node.input else None)
        if node.op_type != op or edge != tensor_name or len(node.input) < 2:
            continue
        scale = initializers.get(node.input[1])
        if scale is None:
            continue
        params = {"scale": [float(v) for v in numpy_helper.to_array(scale).reshape(-1)]}
        zero_point = initializers.get(node.input[2]) if len(node.input) > 2 else None
        if zero_point is not None:
            params["zero_point"] = [int(v) for v in numpy_helper.to_array(zero_point).reshape(-1)]
        axis = next((attr.i for attr in node.attribute if attr.name == "axis"), 0)
        if axis:
            params["quantized_dimension"] = int(axis)
        return params
    return {}


def _with_quant(model, spec: dict, is_output: bool) -> dict:
    """Adds the quantization params when the boundary tensor itself is quantized."""
    if spec.get("dtype") in ("int8", "uint8"):
        spec.update(_quant_params(model, spec["name"], is_output))
    return spec


def extract_input_specs(model) -> list:
    """The real inputs, without the weights some exporters also list in graph.input.

    Relax needs static shapes at build time, so unknown dims default to 1.
    """
    weight_names = {init.name for init in model.graph.initializer}
    inputs = []
    for graph_input in model.graph.input:
        if graph_input.name in weight_names:
            continue
        spec = _tensor_spec(graph_input)
        spec["shape"] = [dim if dim > 0 else 1 for dim in spec["shape"]]
        inputs.append(_with_quant(model, spec, is_output=False))
    return inputs


def extract_output_specs(model) -> list:
    return [_with_quant(model, _tensor_spec(output), is_output=True) for output in model.graph.output]


# --------------------------------------------------------------------------- steps


def preprocess(model, args: argparse.Namespace):
    """Opset conversion, onnxsim simplification and shape inference, each only when asked
    (shape inference always runs). The opset conversion comes first because shape
    inference depends on the opset."""
    if args.target_opset is not None:
        try:
            from onnx import version_converter

            print(f"[2/7] Converting opset -> {args.target_opset}")
            model = version_converter.convert_version(model, args.target_opset)
        except Exception as error:  # noqa: BLE001
            print(f"      version_converter failed: {error}", file=sys.stderr)
            sys.exit(3)
    else:
        print("[2/7] (no opset conversion requested)")

    if args.simplify:
        try:
            from onnxsim import simplify as onnx_simplify
        except ImportError as error:
            print("ERROR: --simplify=true requires onnxsim in the builder image", file=sys.stderr)
            raise SystemExit(4) from error
        print("[3/7] Running onnxsim.simplify")
        try:
            simplified, valid = onnx_simplify(model)
        except Exception as error:  # noqa: BLE001
            print(f"ERROR: ONNX simplification failed: {error}", file=sys.stderr)
            raise SystemExit(4) from error
        if not valid:
            print("ERROR: onnxsim validation failed", file=sys.stderr)
            sys.exit(4)
        model = simplified
        print(f"      simplified graph: {len(model.graph.node)} nodes")
    else:
        print("[3/7] (simplify disabled)")

    print(f"[4/7] shape_inference (strict={args.strict_shape_inference}, data_prop={args.data_prop})")
    try:
        model = shape_inference.infer_shapes(
            model, check_type=False, strict_mode=args.strict_shape_inference, data_prop=args.data_prop
        )
    except Exception as error:  # noqa: BLE001
        print(f"      shape_inference skipped: {error}", file=sys.stderr)
    return model


def convert(model, args: argparse.Namespace):
    """ONNX -> Relax IR. Returns (module, weights or None).

    With keep_params_in_input the weights are detached from the module and returned, so
    they can be saved in params.bin; otherwise they stay inside as constants.
    """
    print(f"[5/7] ONNX -> Relax IR (opset={args.opset}, keep_params_in_input={args.keep_params_in_input}, "
          f"sanitize_input_names={args.sanitize_input_names})")
    mod: Any = from_onnx(
        model,
        shape_dict=None,
        dtype_dict="float32",
        opset=args.opset,
        keep_params_in_input=args.keep_params_in_input,
        sanitize_input_names=args.sanitize_input_names,
    )
    if not args.keep_params_in_input:
        return mod, None
    try:
        return relax.frontend.detach_params(mod)
    except Exception as error:  # noqa: BLE001
        print(f"      detach_params failed: {error}", file=sys.stderr)
        return mod, None


def save_ir(mod, params: Optional[Dict[str, Any]], out_dir: Path) -> None:
    """Writes model.relax.json, the debug dump model.relax.ir and, when given, params.bin."""
    ir_json = out_dir / "model.relax.json"
    ir_json.write_text(tvm.ir.save_json(mod))
    try:
        # For people only: the TVMScript text cannot be loaded back with constant weights.
        (out_dir / "model.relax.ir").write_text(mod.script())
    except Exception as error:  # noqa: BLE001
        print(f"      (skipping mod.script() debug dump: {error})")
    print(f"      IR written: {ir_json}")

    if params is None:
        return
    try:
        from tvm.runtime import save_param_dict

        # save_param_dict wants a flat dict: key each weight as "<function>.<position>",
        # the positional form compiler.py binds back in order.
        flat = {f"{fn_name}.{i}": value for fn_name, values in params.items() for i, value in enumerate(values)}
        params_bin = out_dir / "params.bin"
        params_bin.write_bytes(save_param_dict(flat))
        print(f"      params written: {params_bin}")
    except Exception as error:  # noqa: BLE001
        print(f"      save_param_dict failed: {error}", file=sys.stderr)


def main() -> None:
    args = parse_args()
    in_path = Path(args.input).resolve()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)
    if not in_path.exists():
        print(f"ERROR: {in_path} not found", file=sys.stderr)
        sys.exit(2)

    print(f"[1/7] Loading {in_path}")
    model = onnx.load(str(in_path))
    print(f"      nodes={len(model.graph.node)} opset={model.opset_import[0].version}")

    model = preprocess(model, args)
    mod, params = convert(model, args)
    save_ir(mod, params, out_dir)

    print("[6/7] Extracting metadata")
    inputs = extract_input_specs(model)
    outputs = extract_output_specs(model)
    with in_path.open("rb") as source:
        source_sha256 = hashlib.file_digest(source, "sha256").hexdigest()
    meta = {
        "entry": "main",
        "source_format": "onnx",
        "source_sha256": source_sha256,
        "tvm_version": tvm.__version__,
        "opset": model.opset_import[0].version,
        "model_name": args.name,
        "keep_params_in_input": args.keep_params_in_input,
        "sanitize_input_names": args.sanitize_input_names,
        "simplify": args.simplify,
        "inputs": inputs,
        "outputs": outputs,
    }
    (out_dir / "metadata.json").write_text(json.dumps(meta, indent=2))
    for spec in inputs:
        print(f"      in  '{spec['name']}': {spec['dtype']} {spec['shape']}")
    for spec in outputs:
        print(f"      out '{spec['name']}': {spec['dtype']} {spec['shape']}")

    print("[7/7] Publishing the tvm-ir Model via the digitalhub SDK")
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
            "keep_params_in_input": args.keep_params_in_input,
            "sanitize_input_names": args.sanitize_input_names,
            "parameters": {
                "opset": meta["opset"],
                "model_name": meta["model_name"],
                "simplify": meta["simplify"],
            },
        },
    )
    print("Done")


if __name__ == "__main__":
    main()
