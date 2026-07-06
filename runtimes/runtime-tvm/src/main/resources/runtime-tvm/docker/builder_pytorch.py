#!/usr/bin/env python3
"""PyTorch nn.Module -> Relax IR builder.

User file must expose either:
  - MODEL (torch.nn.Module instance) + EXAMPLE_INPUTS (positional tuple), or
  - get_model() -> (nn.Module, example_inputs tuple)

Pipeline: torch.export.export -> from_exported_program -> detach_params.
"""

import argparse
import importlib.util
import json
import sys
from pathlib import Path
from typing import Any, Optional, Tuple

try:
    import torch
    from torch.export import export
except ImportError:
    # Fail fast with an actionable message: the default tvm-toolkit builder image
    # deliberately does NOT ship torch (it would add ~2GB). The pytorch format
    # needs a torch-enabled builder image.
    sys.exit(
        "ERROR: the builder image does not include 'torch', which the pytorch "
        "format requires. Set task.image (or RUNTIME_TVM_BUILDER_PYTORCH) to a "
        "torch-enabled builder image, e.g. a tvm-toolkit variant with torch "
        "installed."
    )

import tvm  # noqa: F401
from tvm import relax
from tvm.relax.frontend.torch import from_exported_program


def parse_bool(value: Optional[str], default: bool) -> bool:
    if value is None:
        return default
    return value.lower() in ("1", "true", "yes", "y", "on")


def load_user_module(path: Path):
    spec = importlib.util.spec_from_file_location("user_torch_module", str(path))
    if spec is None or spec.loader is None:
        sys.exit(f"ERROR: cannot import {path}")
    mod = importlib.util.module_from_spec(spec)
    sys.modules["user_torch_module"] = mod
    spec.loader.exec_module(mod)
    return mod


def extract_model_and_inputs(user_mod) -> Tuple[torch.nn.Module, tuple]:
    """Locates MODEL+EXAMPLE_INPUTS or get_model() factory."""
    if hasattr(user_mod, "MODEL") and hasattr(user_mod, "EXAMPLE_INPUTS"):
        model = getattr(user_mod, "MODEL")
        example = getattr(user_mod, "EXAMPLE_INPUTS")
        if not isinstance(model, torch.nn.Module):
            sys.exit("ERROR: MODEL must be an instance of torch.nn.Module")
        if not isinstance(example, tuple):
            example = tuple(example)
        return model, example

    if hasattr(user_mod, "get_model") and callable(user_mod.get_model):
        result = user_mod.get_model()
        if not (isinstance(result, tuple) and len(result) == 2):
            sys.exit("ERROR: get_model() must return (nn.Module, example_inputs_tuple)")
        model, example = result
        if not isinstance(model, torch.nn.Module):
            sys.exit("ERROR: get_model() first return must be a torch.nn.Module")
        if not isinstance(example, tuple):
            example = tuple(example)
        return model, example

    sys.exit(
        "ERROR: user file must expose either:\n"
        "  - MODEL (instance of torch.nn.Module) and EXAMPLE_INPUTS (tuple), OR\n"
        "  - get_model() returning (nn.Module, example_inputs_tuple)"
    )


def override_example_inputs(example: tuple, shape_dict: Optional[dict],
                            dtype_dict: Optional[Any]) -> tuple:
    """Rebuild example tensors using user shape/dtype overrides. PyTorch is
    positional, so keys must be input_0, input_1, ... to map by index.
    """
    if not shape_dict and not dtype_dict:
        return example
    new_inputs = []
    for i, t in enumerate(example):
        shape = list(t.shape)
        dtype = t.dtype
        key = f"input_{i}"
        if shape_dict and key in shape_dict:
            shape = list(shape_dict[key])
        if dtype_dict:
            if isinstance(dtype_dict, dict) and key in dtype_dict:
                dtype = _torch_dtype(dtype_dict[key])
            elif isinstance(dtype_dict, str):
                dtype = _torch_dtype(dtype_dict)
        new_inputs.append(torch.randn(*shape, dtype=dtype) if dtype.is_floating_point
                          else torch.zeros(*shape, dtype=dtype))
    return tuple(new_inputs)


def _torch_dtype(name: str) -> torch.dtype:
    return {
        "float32": torch.float32, "float16": torch.float16, "float64": torch.float64,
        "int32": torch.int32, "int64": torch.int64, "int8": torch.int8,
        "uint8": torch.uint8, "bool": torch.bool,
    }.get(name, torch.float32)


def discover_signature_from_examples(example: tuple, mod):
    """Recovers inputs/outputs from the Relax IR signature."""
    inputs = []
    for i, t in enumerate(example):
        inputs.append({
            "name": f"input_{i}",
            "shape": list(t.shape),
            "dtype": str(t.dtype).replace("torch.", ""),
        })

    outputs = []
    try:
        for gv, fn in mod.functions_items():
            if gv.name_hint != "main":
                continue
            if not isinstance(fn, relax.Function):
                continue
            ret = getattr(fn, "ret_struct_info", None)
            if ret is None:
                continue
            # A single-tensor return exposes .shape/.dtype directly; a multi-output
            # model returns a TupleStructInfo whose .fields each carry their own
            # shape/dtype. Emit one entry per element (output0, output1, ...) instead
            # of a single bogus output0 with an empty shape.
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
        print(f"WARN: output signature discovery failed: {e}", file=sys.stderr)
    return inputs, outputs


def main():
    ap = argparse.ArgumentParser(description="PyTorch nn.Module -> Relax IR + metadata.json")
    ap.add_argument("--input", required=True,
                    help=".py file exposing MODEL/EXAMPLE_INPUTS or get_model()")
    ap.add_argument("--output", required=True)
    ap.add_argument("--name", default="model")
    ap.add_argument("--shape-dict", default=None,
                    help='JSON shape override keyed by input_N, e.g. \'{"input_0":[1,3,640,640]}\'')
    ap.add_argument("--dtype-dict", default=None,
                    help='JSON per-input dict or a single dtype string')
    ap.add_argument("--keep-params-in-input", type=str, default="true",
                    help="default true: nn.Module weights saved in params.bin")
    # from_exported_program flags; None -> keep TVM's own default for that flag
    ap.add_argument("--unwrap-unit-return-tuple", type=str, default=None,
                    help="unwrap a unit (size-1) return tuple (TVM default false)")
    ap.add_argument("--no-bind-return-tuple", type=str, default=None,
                    help="do not bind the return tuple to a relax var (TVM default false)")
    ap.add_argument("--run-ep-decomposition", type=str, default=None,
                    help="run PyTorch's ExportedProgram decomposition (TVM default true)")
    args = ap.parse_args()

    in_path = Path(args.input).resolve()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)
    if not in_path.exists():
        print(f"ERROR: {in_path} not found", file=sys.stderr)
        sys.exit(2)

    shape_dict = None
    if args.shape_dict:
        try:
            shape_dict = json.loads(args.shape_dict)
        except json.JSONDecodeError as e:
            sys.exit(f"ERROR: --shape-dict not valid JSON: {e}")
    dtype_dict: Any = None
    if args.dtype_dict:
        try:
            dtype_dict = json.loads(args.dtype_dict)
        except json.JSONDecodeError:
            dtype_dict = args.dtype_dict
    keep_params = parse_bool(args.keep_params_in_input, True)

    print(f"[1/6] Importing user file from {in_path}")
    user_mod = load_user_module(in_path)
    model, example = extract_model_and_inputs(user_mod)
    example = override_example_inputs(example, shape_dict, dtype_dict)
    model = model.eval()
    print(f"      model: {type(model).__name__}, example inputs: "
          f"{[tuple(t.shape) for t in example]}")

    print("[2/6] torch.export → ExportedProgram")
    with torch.no_grad():
        ep = export(model, example)

    # Pass the optional flags only when the user set them, so TVM's own defaults
    # (unwrap=False, no_bind=False, run_ep_decomposition=True) apply otherwise. This
    # also stays compatible with TVM builds that predate a given flag.
    ep_kwargs = {"keep_params_as_input": keep_params}
    if args.unwrap_unit_return_tuple is not None:
        ep_kwargs["unwrap_unit_return_tuple"] = parse_bool(args.unwrap_unit_return_tuple, False)
    if args.no_bind_return_tuple is not None:
        ep_kwargs["no_bind_return_tuple"] = parse_bool(args.no_bind_return_tuple, False)
    if args.run_ep_decomposition is not None:
        ep_kwargs["run_ep_decomposition"] = parse_bool(args.run_ep_decomposition, True)
    print(f"[3/6] from_exported_program ({ep_kwargs})")
    mod = from_exported_program(ep, **ep_kwargs)

    params: Optional[dict] = None
    if keep_params:
        try:
            mod, params = relax.frontend.detach_params(mod)
        except Exception as e:  # noqa: BLE001
            print(f"      detach_params failed: {e}", file=sys.stderr)
            params = None

    print("[4/6] Writing IR + params")
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
            flat = {}
            for fn_name, lst in params.items():
                for i, v in enumerate(lst):
                    flat[f"{fn_name}.{i}"] = v
            params_bin.write_bytes(save_param_dict(flat))
            print(f"      params written: {params_bin}")
        except Exception as e:  # noqa: BLE001
            print(f"      save_param_dict failed: {e}", file=sys.stderr)

    print("[5/6] Extracting metadata")
    inputs, outputs = discover_signature_from_examples(example, mod)
    meta = {
        "entry": "main",
        "source_format": "pytorch",
        "model_name": args.name,
        "model_class": type(model).__name__,
        "keep_params_in_input": keep_params,
        "inputs": inputs,
        "outputs": outputs,
    }
    if shape_dict:
        meta["shape_overrides"] = shape_dict
    if dtype_dict:
        meta["dtype_overrides"] = dtype_dict
    (out_dir / "metadata.json").write_text(json.dumps(meta, indent=2))
    for i in inputs:
        print(f"      in  '{i['name']}': {i['dtype']} {i['shape']}")
    for o in outputs:
        print(f"      out '{o['name']}': {o['dtype']} {o['shape']}")

    print("[6/6] publishing Model entity via digitalhub SDK")
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
            "parameters": {
                "model_name": meta["model_name"],
                "model_class": meta.get("model_class"),
            },
        },
    )

    print("Done")


if __name__ == "__main__":
    main()
