#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""tvm+build for ONNX models: converts the model into Relax IR and publishes it as a
Model of kind tvm-ir.

1. load the ONNX file;
2. optionally convert it to another opset and simplify it with onnxsim;
3. run the ONNX shape inference;
4. convert it into Relax IR (weights inside as constants, or apart in params.bin); when some
   outputs come out without a shape, which TVM cannot compile, convert again after
   simplifying the graph with onnxsim;
5. write model.relax.json and metadata.json;
6. publish the folder as a tvm-ir Model.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from common import (
    INPUT_DIR,
    OUTPUT_DIR,
    fail,
    option,
    print_signature,
    save_relax_ir,
    sha256_of,
    source_model_file,
    step,
    to_bool,
    write_json,
)

STEPS = 6

# ONNX TensorProto.DataType -> dtype name
ONNX_DTYPES = {
    1: "float32", 2: "uint8", 3: "int8", 4: "uint16", 5: "int16", 6: "int32",
    7: "int64", 9: "bool", 10: "float16", 11: "float64", 12: "uint32", 13: "uint64",
}


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="ONNX -> Relax IR (Model tvm-ir)")
    option(parser, "--input", "TVM_INPUT_FILE", default="model.onnx", help="ONNX file, a path or a name in the input folder")
    option(parser, "--output", "TVM_OUTPUT_DIR", default=str(OUTPUT_DIR), help="folder that receives the IR")
    option(parser, "--name", "TVM_FUNCTION_NAME", default="model", help="function name; the Model is <name>-ir")

    conversion = parser.add_argument_group("ONNX conversion")
    option(conversion, "--target-opset", "TVM_TARGET_OPSET", type=int, help="convert the model to this opset first")
    option(conversion, "--simplify", "TVM_SIMPLIFY", type=to_bool, default=False, help="simplify the graph with onnxsim")
    option(conversion, "--strict-shape-inference", "TVM_STRICT_SHAPE_INFER", type=to_bool, default=False,
           help="strict mode of the ONNX shape inference")
    option(conversion, "--data-prop", "TVM_DATA_PROP", type=to_bool, default=False,
           help="data propagation in the ONNX shape inference")
    option(conversion, "--opset", "TVM_OPSET_OVERRIDE", type=int, help="opset the TVM importer assumes")
    option(conversion, "--keep-params-in-input", "TVM_KEEP_PARAMS_IN_INPUT", type=to_bool, default=False,
           help="keep the weights apart in params.bin")
    option(conversion, "--sanitize-input-names", "TVM_SANITIZE_INPUT_NAMES", type=to_bool, default=True,
           help="rewrite the input names into valid identifiers")
    return parser.parse_args(argv)


# --------------------------------------------------------------------------- tensor signatures


def tensor_spec(value_info) -> dict:
    """name, shape and dtype of a graph input or output; unknown dims become -1."""
    tensor_type = value_info.type.tensor_type
    return {
        "name": value_info.name,
        "shape": [dim.dim_value if dim.dim_value > 0 else -1 for dim in tensor_type.shape.dim],
        "dtype": ONNX_DTYPES.get(tensor_type.elem_type, "float32"),
    }


def quantization(model, tensor_name: str, is_output: bool) -> dict:
    """scale, zero_point and axis of a quantized boundary tensor, read from its QDQ node.

    ONNX keeps them on the node next to the tensor: a DequantizeLinear right after an int8
    input, a QuantizeLinear right before an int8 output, with inputs 1 and 2 as scale and
    zero point. TFLite keeps the same values on the tensor itself.
    """
    from onnx import numpy_helper

    op_type = "QuantizeLinear" if is_output else "DequantizeLinear"
    initializers = {init.name: init for init in model.graph.initializer}
    for node in model.graph.node:
        edge = node.output[0] if is_output else (node.input[0] if node.input else None)
        if node.op_type != op_type or edge != tensor_name or len(node.input) < 2 or node.input[1] not in initializers:
            continue
        params = {"scale": [float(v) for v in numpy_helper.to_array(initializers[node.input[1]]).reshape(-1)]}
        if len(node.input) > 2 and node.input[2] in initializers:
            params["zero_point"] = [int(v) for v in numpy_helper.to_array(initializers[node.input[2]]).reshape(-1)]
        axis = next((attr.i for attr in node.attribute if attr.name == "axis"), 0)
        if axis:
            params["quantized_dimension"] = int(axis)
        return params
    return {}


def signature(model) -> tuple[list, list]:
    """Inputs and outputs of the model, with the quantization of int8/uint8 tensors.

    Some exporters list the weights among the graph inputs: they are left out. Relax needs
    static shapes, so unknown input dims become 1.
    """
    weights = {init.name for init in model.graph.initializer}
    inputs = []
    for graph_input in model.graph.input:
        if graph_input.name in weights:
            continue
        spec = tensor_spec(graph_input)
        spec["shape"] = [dim if dim > 0 else 1 for dim in spec["shape"]]
        inputs.append(spec)
    outputs = [tensor_spec(output) for output in model.graph.output]
    for specs, is_output in ((inputs, False), (outputs, True)):
        for spec in specs:
            if spec["dtype"] in ("int8", "uint8"):
                spec.update(quantization(model, spec["name"], is_output))
    return inputs, outputs


# --------------------------------------------------------------------------- steps


class SimplifyError(Exception):
    """onnxsim is missing or could not simplify the graph."""


def simplify_graph(model):
    """The graph simplified by onnxsim, which also computes in advance every value that
    depends only on the shapes."""
    try:
        from onnxsim import simplify
    except ImportError as error:
        raise SimplifyError("simplify needs onnxsim in the builder image") from error
    try:
        simplified, valid = simplify(model)
    except Exception as error:  # noqa: BLE001
        raise SimplifyError(f"ONNX simplification failed: {error}") from error
    if not valid:
        raise SimplifyError("onnxsim could not validate the simplified graph")
    return simplified


def prepare(model, args: argparse.Namespace, simplify: bool):
    """Opset conversion, simplification when asked, then shape inference. The opset comes
    first because shape inference depends on it. Raises SimplifyError."""
    from onnx import shape_inference, version_converter

    if args.target_opset is not None:
        print(f"      converting opset {model.opset_import[0].version} -> {args.target_opset}")
        try:
            model = version_converter.convert_version(model, args.target_opset)
        except Exception as error:  # noqa: BLE001
            fail(f"opset conversion to {args.target_opset} failed: {error}", code=3)

    if simplify:
        print("      simplifying the graph with onnxsim")
        model = simplify_graph(model)
        print(f"      simplified graph: {len(model.graph.node)} nodes")

    print(f"      shape inference (strict={args.strict_shape_inference}, data_prop={args.data_prop})")
    try:
        model = shape_inference.infer_shapes(
            model, check_type=False, strict_mode=args.strict_shape_inference, data_prop=args.data_prop
        )
    except Exception as error:  # noqa: BLE001
        # The importer can often work without the inferred shapes: go on and let it decide.
        print(f"WARN: shape inference skipped: {error}")
    return model


def convert(model, args: argparse.Namespace):
    """ONNX -> Relax IR. Returns (module, weights): weights is None unless they are kept
    apart with keep_params_in_input."""
    from tvm import relax
    from tvm.relax.frontend.onnx import from_onnx

    mod = from_onnx(
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
        print(f"WARN: cannot detach the weights, they stay in the IR: {error}")
        return mod, None


def outputs_without_shape(mod) -> list[int]:
    """Positions of the IR outputs that have only a rank and no shape. TVM cannot generate
    code for the operations that produce them, so tvm+compile would fail on them."""
    from tvm import relax

    returned = mod["main"].ret_ty
    outputs = list(returned.fields) if isinstance(returned, relax.TupleType) else [returned]
    return [index for index, output in enumerate(outputs) if isinstance(output, relax.TensorType) and output.shape is None]


def main(argv=None) -> None:
    args = parse_args(argv)
    import onnx
    import tvm

    source = Path(args.input) if Path(args.input).is_file() else source_model_file(INPUT_DIR, args.input, ".onnx")
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    step(1, STEPS, f"loading {source}")
    model = onnx.load(str(source))
    print(f"      {len(model.graph.node)} nodes, opset {model.opset_import[0].version}")

    step(2, STEPS, "preparing the ONNX graph")
    try:
        prepared = prepare(model, args, simplify=args.simplify)
    except SimplifyError as error:
        fail(str(error), code=4)

    step(3, STEPS, f"converting to Relax IR (opset={args.opset}, keep_params_in_input={args.keep_params_in_input}, "
                   f"sanitize_input_names={args.sanitize_input_names})")
    mod, weights = convert(prepared, args)
    simplified = args.simplify
    if not simplified and outputs_without_shape(mod):
        # TVM 0.26 imports some shape arithmetic, such as the box decoding of YOLOv8, as slices
        # sized at run time: the outputs lose their shape and the compile fails. onnxsim
        # computes those values in advance, so the graph is simplified and converted again.
        print("      some outputs have no shape: simplifying the graph with onnxsim and converting again")
        try:
            prepared = prepare(onnx.load(str(source)), args, simplify=True)
        except SimplifyError as error:
            print(f"WARN: {error}; keeping the first conversion")
        else:
            mod, weights = convert(prepared, args)
            simplified = True
    missing = outputs_without_shape(mod)
    if missing:
        print(f"WARN: outputs {missing} still have no shape: tvm+compile will probably fail on them")

    step(4, STEPS, "writing the IR")
    save_relax_ir(mod, out_dir, weights)

    step(5, STEPS, "writing metadata.json")
    inputs, outputs = signature(prepared)
    metadata = {
        "entry": "main",
        "source_format": "onnx",
        "source_sha256": sha256_of(source),
        "tvm_version": tvm.__version__,
        "opset": prepared.opset_import[0].version,
        "model_name": args.name,
        "keep_params_in_input": args.keep_params_in_input,
        "sanitize_input_names": args.sanitize_input_names,
        "simplify": simplified,
        "simplified_automatically": simplified and not args.simplify,
        "inputs": inputs,
        "outputs": outputs,
    }
    write_json(out_dir / "metadata.json", metadata)
    print_signature(inputs, outputs)

    step(6, STEPS, "publishing the Model (kind tvm-ir)")
    from publish import publish_ir_model

    publish_ir_model(out_dir, args.name, metadata,
                     parameters={"opset": metadata["opset"], "model_name": args.name, "simplify": simplified})
    print("Done")


if __name__ == "__main__":
    main()
