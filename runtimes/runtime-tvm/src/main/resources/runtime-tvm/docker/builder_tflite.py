#!/usr/bin/env python3
"""TFLite -> Relax IR builder (from_tflite + metadata extraction, as CLI args)."""

import argparse
import json
import sys
from pathlib import Path
from typing import Any

import tflite
import tvm  # noqa: F401  (side-effect: register ops)
from tvm import relax
from tvm.relax.frontend.tflite import from_tflite


def _dtype_name(code: int) -> str:
    """TFLite TensorType code -> TVM dtype string (FLOAT32 -> "float32")."""
    from tflite.TensorType import TensorType

    for name, value in vars(TensorType).items():
        if not name.startswith("_") and value == code:
            return name.lower()
    return "float32"


def _tensor_spec(tensor) -> dict:
    shape = [tensor.Shape(i) for i in range(tensor.ShapeLength())]
    spec = {
        "name": tensor.Name().decode("utf-8") if tensor.Name() else "unknown",
        # TFLite marks dynamic dims as -1; the served model needs a concrete shape.
        "shape": [d if d > 0 else 1 for d in shape],
        "dtype": _dtype_name(tensor.Type()),
    }
    # Affine quantization params: real = (q - zero_point) * scale. A property of the
    # QUANTIZATION, not of the source format — builder_onnx.py extracts the same thing
    # from the QDQ nodes of a quantized ONNX. Without them a client receiving int8 has
    # no way to map the values back to reals, so they travel with the model: the serve
    # runtimes expose them on /v2/models and compiler.py copies metadata.json verbatim.
    q = tensor.Quantization()
    if q is not None and q.ScaleLength() > 0:
        spec["scale"] = [float(q.Scale(i)) for i in range(q.ScaleLength())]
        spec["zero_point"] = [int(q.ZeroPoint(i)) for i in range(q.ZeroPointLength())]
        if q.QuantizedDimension():
            spec["quantized_dimension"] = int(q.QuantizedDimension())
    return spec


def extract_io_specs(model):
    """Input/output signatures of the main subgraph (index 0)."""
    subgraph = model.Subgraphs(0)
    inputs = [_tensor_spec(subgraph.Tensors(subgraph.Inputs(i))) for i in range(subgraph.InputsLength())]
    outputs = [_tensor_spec(subgraph.Tensors(subgraph.Outputs(i))) for i in range(subgraph.OutputsLength())]
    return inputs, outputs


def main():
    ap = argparse.ArgumentParser(description="TFLite -> Relax IR + metadata.json")
    ap.add_argument("--input", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--name", default="model")
    # parse_known_args: the shared entrypoint.sh also emits ONNX-only flags
    # (--simplify, --opset, ...) whenever the task sets them; ignore them here.
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
    model = tflite.Model.GetRootAsModel(in_path.read_bytes(), 0)

    print("[2/4] TFLite -> Relax IR")
    mod: Any = from_tflite(model)

    # Weights are already relax.const in the graph; the frontend also attaches a
    # copy as the "params" attr. Drop it so save_json doesn't store them twice
    # (the detached dict is deliberately discarded — no params.bin for TFLite,
    # compiler.py must not try to bind constants that are already inlined).
    try:
        mod, _ = relax.frontend.detach_params(mod)
    except Exception as e:  # noqa: BLE001
        print(f"      detach_params skipped: {e}", file=sys.stderr)

    # model.relax.json is round-trip safe; the .ir TVMScript dump is debug-only.
    ir_json = out_dir / "model.relax.json"
    ir_text = out_dir / "model.relax.ir"
    ir_json.write_text(tvm.ir.save_json(mod))
    try:
        ir_text.write_text(mod.script())
    except Exception as e:  # noqa: BLE001
        print(f"      (skipping mod.script() debug dump: {e})")
    print(f"      IR written: {ir_json}")

    print("[3/4] Extracting metadata")
    inputs, outputs = extract_io_specs(model)
    meta = {
        "entry": "main",
        "source_format": "tflite",
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


if __name__ == "__main__":
    main()
