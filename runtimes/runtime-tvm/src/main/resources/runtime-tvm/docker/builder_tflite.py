#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""tvm+build for TFLite models: TFLite -> Relax IR.

The steps, in order:
1. load the TFLite flatbuffer;
2. convert it to Relax IR with from_tflite (the weights stay inside as constants);
3. save model.relax.json and metadata.json, with the quantization params of the boundary
   tensors;
4. publish the folder as a tvm-ir Model.
"""

import argparse
import hashlib
import json
import sys
from pathlib import Path
from typing import Any

import tflite
import tvm  # noqa: F401  (importing registers the Relax operators)
from tvm import relax
from tvm.relax.frontend.tflite import from_tflite


def _dtype_name(code: int) -> str:
    """TFLite TensorType code -> TVM dtype name (FLOAT32 -> "float32")."""
    from tflite.TensorType import TensorType

    for name, value in vars(TensorType).items():
        if not name.startswith("_") and value == code:
            return name.lower()
    return "float32"


def _tensor_spec(tensor) -> dict:
    """name, shape, dtype and, for quantized tensors, the affine params of a TFLite tensor."""
    shape = [tensor.Shape(i) for i in range(tensor.ShapeLength())]
    spec = {
        "name": tensor.Name().decode("utf-8") if tensor.Name() else "unknown",
        # TFLite marks dynamic dims as -1; the served model needs a concrete shape.
        "shape": [dim if dim > 0 else 1 for dim in shape],
        "dtype": _dtype_name(tensor.Type()),
    }
    # real = (q - zero_point) * scale. This belongs to the quantization, not to the
    # format: builder_onnx.py reads the same params from the QDQ nodes of an ONNX model.
    # Without them a client receiving int8 cannot map the values back to reals, so they
    # travel with the model: the serve runtimes expose them on /v2/models.
    quantization = tensor.Quantization()
    if quantization is not None and quantization.ScaleLength() > 0:
        spec["scale"] = [float(quantization.Scale(i)) for i in range(quantization.ScaleLength())]
        spec["zero_point"] = [int(quantization.ZeroPoint(i)) for i in range(quantization.ZeroPointLength())]
        if quantization.QuantizedDimension():
            spec["quantized_dimension"] = int(quantization.QuantizedDimension())
    return spec


def extract_io_specs(model):
    """Input and output signatures of the main subgraph (index 0)."""
    subgraph = model.Subgraphs(0)
    inputs = [_tensor_spec(subgraph.Tensors(subgraph.Inputs(i))) for i in range(subgraph.InputsLength())]
    outputs = [_tensor_spec(subgraph.Tensors(subgraph.Outputs(i))) for i in range(subgraph.OutputsLength())]
    return inputs, outputs


def main() -> None:
    ap = argparse.ArgumentParser(description="TFLite -> Relax IR + metadata.json")
    ap.add_argument("--input", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--name", default="model")
    # entrypoint.sh also forwards the ONNX-only options (--simplify, --opset, ...) when the
    # task sets them; they do not apply here and are ignored.
    args, ignored = ap.parse_known_args()
    if ignored:
        print(f"      ignoring flags that do not apply to TFLite: {' '.join(ignored)}")

    in_path = Path(args.input).resolve()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)
    if not in_path.exists():
        print(f"ERROR: {in_path} not found", file=sys.stderr)
        sys.exit(2)

    print(f"[1/4] Loading TFLite model from {in_path}")
    model_bytes = in_path.read_bytes()
    model = tflite.Model.GetRootAsModel(model_bytes, 0)

    print("[2/4] TFLite -> Relax IR")
    mod: Any = from_tflite(model)
    # The weights are already relax constants in the graph, and the frontend also attaches
    # a copy as the "params" attribute. Detaching drops that copy so save_json does not
    # store the weights twice; the detached dict is discarded on purpose (no params.bin for
    # TFLite, compiler.py must not bind constants that are already inlined).
    try:
        mod, _ = relax.frontend.detach_params(mod)
    except Exception as error:  # noqa: BLE001
        print(f"      detach_params skipped: {error}", file=sys.stderr)

    ir_json = out_dir / "model.relax.json"
    ir_json.write_text(tvm.ir.save_json(mod))
    try:
        # For people only: the TVMScript text cannot be loaded back.
        (out_dir / "model.relax.ir").write_text(mod.script())
    except Exception as error:  # noqa: BLE001
        print(f"      (skipping mod.script() debug dump: {error})")
    print(f"      IR written: {ir_json}")

    print("[3/4] Extracting metadata")
    inputs, outputs = extract_io_specs(model)
    meta = {
        "entry": "main",
        "source_format": "tflite",
        "source_sha256": hashlib.sha256(model_bytes).hexdigest(),
        "tvm_version": tvm.__version__,
        "model_name": args.name,
        "inputs": inputs,
        "outputs": outputs,
    }
    (out_dir / "metadata.json").write_text(json.dumps(meta, indent=2))
    for spec in inputs:
        print(f"      in  '{spec['name']}': {spec['dtype']} {spec['shape']}")
    for spec in outputs:
        print(f"      out '{spec['name']}': {spec['dtype']} {spec['shape']}")

    print("[4/4] Publishing the tvm-ir Model via the digitalhub SDK")
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
            "parameters": {"model_name": meta["model_name"]},
        },
    )
    print("Done")


if __name__ == "__main__":
    main()
