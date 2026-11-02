#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Convert TFLite to Relax IR and publish a tvm-ir Model.

ONNX conversion options are ignored.
"""

from __future__ import annotations

import argparse
from pathlib import Path

from common import INPUT_DIR, OUTPUT_DIR, option, print_signature, save_relax_ir, sha256_of, source_model_file, step, write_json

STEPS = 4


def parse_args(argv=None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="TFLite -> Relax IR (Model tvm-ir)")
    option(parser, "--input", "TVM_INPUT_FILE", default="model.tflite", help="TFLite file, a path or a name in the input folder")
    option(parser, "--output", "TVM_OUTPUT_DIR", default=str(OUTPUT_DIR), help="folder that receives the IR")
    option(parser, "--name", "TVM_FUNCTION_NAME", default="model", help="function name; the Model is <name>-ir")
    return parser.parse_args(argv)


def dtype_name(code: int) -> str:
    """TFLite TensorType code -> dtype name (FLOAT32 -> "float32")."""
    from tflite.TensorType import TensorType

    return next((name.lower() for name, value in vars(TensorType).items() if not name.startswith("_") and value == code),
                "float32")


def tensor_spec(tensor) -> dict:
    """Include quantization so clients can decode real = (q - zero_point) * scale."""
    spec = {
        "name": tensor.Name().decode("utf-8") if tensor.Name() else "unknown",
        # TFLite marks dynamic dims as -1; the served model needs a concrete shape.
        "shape": [dim if dim > 0 else 1 for dim in (tensor.Shape(i) for i in range(tensor.ShapeLength()))],
        "dtype": dtype_name(tensor.Type()),
    }
    quant = tensor.Quantization()
    if quant is not None and quant.ScaleLength() > 0:
        spec["scale"] = [float(quant.Scale(i)) for i in range(quant.ScaleLength())]
        spec["zero_point"] = [int(quant.ZeroPoint(i)) for i in range(quant.ZeroPointLength())]
        if quant.QuantizedDimension():
            spec["quantized_dimension"] = int(quant.QuantizedDimension())
    return spec


def signature(model) -> tuple[list, list]:
    graph = model.Subgraphs(0)
    inputs = [tensor_spec(graph.Tensors(graph.Inputs(i))) for i in range(graph.InputsLength())]
    outputs = [tensor_spec(graph.Tensors(graph.Outputs(i))) for i in range(graph.OutputsLength())]
    return inputs, outputs


def main(argv=None) -> None:
    args = parse_args(argv)
    import tflite
    import tvm
    from tvm import relax
    from tvm.relax.frontend.tflite import from_tflite

    source = Path(args.input) if Path(args.input).is_file() else source_model_file(INPUT_DIR, args.input, ".tflite")
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    step(1, STEPS, f"loading {source}")
    model = tflite.Model.GetRootAsModel(source.read_bytes(), 0)

    step(2, STEPS, "converting to Relax IR")
    mod = from_tflite(model)
    # Weights are already graph constants; discard the duplicate params attribute.
    try:
        mod, _ = relax.frontend.detach_params(mod)
    except Exception as error:  # noqa: BLE001
        print(f"WARN: weights copy not detached: {error}")
    save_relax_ir(mod, out_dir)

    step(3, STEPS, "writing metadata.json")
    inputs, outputs = signature(model)
    metadata = {
        "entry": "main",
        "source_format": "tflite",
        "source_sha256": sha256_of(source),
        "tvm_version": tvm.__version__,
        "model_name": args.name,
        "inputs": inputs,
        "outputs": outputs,
    }
    write_json(out_dir / "metadata.json", metadata)
    print_signature(inputs, outputs)

    step(4, STEPS, "publishing the Model (kind tvm-ir)")
    from publish import publish_ir_model

    publish_ir_model(out_dir, args.name, metadata, parameters={"model_name": args.name})
    print("Done")


if __name__ == "__main__":
    main()
