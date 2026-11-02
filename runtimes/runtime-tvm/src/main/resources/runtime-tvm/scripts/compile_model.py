#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Compile Relax IR into model.so and publish a tvm-so Model.

Targets use TVM syntax: "llvm" or JSON, e.g. {"kind":"llvm","mcpu":"x86-64-v3"}.
"""

from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
from typing import Optional

import tvm
from tvm import relax

import tuning
import toolchain
from benchmark import benchmark_library
from common import INPUT_DIR, OUTPUT_DIR, fail, option, read_json, step, target_triple, to_bool, write_json

STEPS = 7


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Relax IR -> model.so (Model tvm-so)", allow_abbrev=False)

    model = parser.add_argument_group("model")
    option(model, "--ir-dir", "TVM_INPUT_DIR", default=str(INPUT_DIR), help="folder with model.relax.json and metadata.json")
    option(model, "--output", "TVM_OUTPUT_DIR", default=str(OUTPUT_DIR), help="folder that receives model.so")
    option(model, "--name", "TVM_FUNCTION_NAME", default="model", help="function name; the Model is <name>-<tag>")
    option(model, "--tag", "TVM_TAG", help="suffix of the Model name (default so), saved in metadata")
    option(model, "--params-file", "TVM_PARAMS_FILE", help="params.bin to embed (default: the one next to the IR)")
    option(model, "--source-ir-key", "TVM_SOURCE_IR_KEY", help="key of the IR Model, linked to the compiled Model")

    build = parser.add_argument_group("target and build")
    option(build, "--target", "TVM_TARGET", default="llvm", help='TVM target: "llvm" or the JSON form')
    option(build, "--target-num-cores", "TVM_TARGET_NUM_CORES", type=int, help="num-cores of the target, used by tuning")
    option(build, "--opt-level", "TVM_OPT_LEVEL", type=int, default=3, help="PassContext opt_level, 0-3")
    option(build, "--cross-cc", "TVM_CROSS_CC", help="cross compiler for export_library, e.g. aarch64-linux-gnu-g++")
    option(build, "--cross-cc-options", "TVM_CROSS_CC_OPTIONS", type=toolchain.compiler_options, default="[]",
           help='JSON array of C++ compiler arguments, e.g. ["--sysroot=/opt/sysroots/arm64"]')
    option(build, "--exec-mode", "TVM_EXEC_MODE", default="bytecode", choices=["bytecode", "compiled"], help="relax.build exec_mode")
    option(build, "--relax-pipeline", "TVM_RELAX_PIPELINE", default="default", help="relax.build relax_pipeline")
    option(build, "--tir-pipeline", "TVM_TIR_PIPELINE", default="default", help="relax.build tir_pipeline")
    option(build, "--system-lib", "TVM_SYSTEM_LIB", type=to_bool, default=False, help="relax.build system_lib")
    option(build, "--benchmark-runs", "TVM_BENCHMARK_RUNS", type=int, default=10, help="timed inferences of model.so, 0 = off")

    args = tuning.parse_options(parser, argv)
    validate_options(args)
    return args


def validate_options(args: argparse.Namespace) -> None:
    if not 0 <= args.opt_level <= 3:
        fail("--opt-level must be between 0 and 3")
    if args.exec_mode not in ("bytecode", "compiled"):
        fail(f"exec mode must be bytecode or compiled, not {args.exec_mode!r}")
    if args.target_num_cores is not None and args.target_num_cores < 1:
        fail("--target-num-cores must be >= 1")
    if args.benchmark_runs < 0:
        fail("--benchmark-runs must be >= 0")
    if args.relax_pipeline == "static_shape_tuning":
        fail("use --tuning-mode tune; --relax-pipeline remains the final build pipeline")
    tuning.validate_options(args)


def load_ir(ir_dir: Path):
    path = ir_dir / "model.relax.json"
    if not path.is_file():
        source = os.environ.get("TVM_SOURCE_IR_KEY", "the selected IR model")
        fail(
            f"model.relax.json not found in {ir_dir}. Source: {source}. "
            "Check that its storage folder contains the tvm+build output. "
            "An explicit run.model_path overrides function.ir_model; "
            "clear it to use the function's current IR."
        )
    return tvm.ir.load_json(path.read_text())


def load_weights(ir_dir: Path, params_file: Optional[str]) -> Optional[dict]:
    """The weights kept apart by tvm+build (keep_params_in_input), if any."""
    from tvm.runtime import load_param_dict

    path = Path(params_file) if params_file else ir_dir / "params.bin"
    if not path.exists():
        if params_file:
            fail(f"--params-file {path} not found", code=3)
        return None
    print(f"      weights from {path}")
    # TVM 0.26 wants a flat {name: tensor}; the old grouped form breaks the VM link.
    return dict(load_param_dict(path.read_bytes()).items())


def var_name(var) -> str:
    return var.name if hasattr(var, "name") else var.name_hint


def embed_weights(mod, weights: dict):
    """Bind named or positional (<function>.<i>) weights after the real inputs.

    Return (entry name, weight count, module); model.so then takes only real inputs.
    """
    functions = [(gv.name_hint, f) for gv, f in mod.functions_items() if isinstance(f, relax.Function)]
    entry, function = next(((name, f) for name, f in functions if name == "main"), functions[0])
    params = list(function.params)
    if set(weights).issubset({var_name(v) for v in params}):
        named = dict(weights)
    else:
        input_count = len(params) - len(weights)
        if input_count < 0:
            raise ValueError(f"{len(weights)} saved weights > {len(params)} function parameters")
        ordered = [weights[key] for key in sorted(weights, key=lambda key: int(key.rpartition(".")[2]))]
        named = {var_name(v): value for v, value in zip(params[input_count:], ordered)}
    return entry, len(named), relax.transform.BindParams(entry, named)(mod)


def cpu_count() -> int:
    """CPUs this process may use (the container limit, not the size of the node)."""
    try:
        return len(os.sched_getaffinity(0))
    except AttributeError:
        return os.cpu_count() or 1


def resolve_target(target_text: str, num_cores: Optional[int]):
    """Resolve mcpu=native to avoid LLVM's generic-CPU fallback.

    Return (Target, effective target text), including num-cores for tuning.
    """
    try:
        config = json.loads(target_text)
    except json.JSONDecodeError as error:
        if target_text.strip() != "llvm":
            raise ValueError("use a standard JSON target or plain llvm") from error
        config = {"kind": "llvm"}
    if not isinstance(config, dict):
        raise ValueError("an LLVM target must be a JSON object")
    if config.get("kind") != "llvm":
        raise ValueError("only LLVM CPU compilation is supported")
    host_triple = tvm.target.codegen.llvm_get_system_triple()
    config.setdefault("mtriple", host_triple)
    toolchain.platform_key(config["mtriple"])

    if config.get("mcpu") == "native":
        if toolchain.platform_key(config["mtriple"]) != toolchain.platform_key(host_triple):
            raise ValueError("mcpu=native requires a target matching the compile host")
        cpu = tvm.target.codegen.llvm_get_system_cpu()
        if not cpu or cpu in {"generic", "unknown"}:
            raise RuntimeError("LLVM could not detect the native CPU")
        config["mcpu"] = cpu
        features = tvm.get_global_func("tvm.codegen.llvm.GetHostCPUFeatures")()
        config["mattr"] = [
            ("+" if int(enabled) else "-") + name for name, enabled in sorted(features.items())
        ] + config.get("mattr", [])
        triple = tvm.target.codegen.llvm_get_system_triple()
        if triple:
            config["mtriple"] = triple
        config.setdefault("num-cores", cpu_count())
        print(f"      mcpu=native -> mcpu={cpu}, mtriple={config.get('mtriple')}")
    if num_cores is not None:
        config["num-cores"] = num_cores
    text = json.dumps(config, separators=(",", ":"))
    target = tvm.target.Target(text)
    cpus = tvm.target.codegen.llvm_get_cpu_archlist(target)
    if config.get("mcpu") not in (None, "generic") and config["mcpu"] not in cpus:
        raise ValueError(f"LLVM does not support CPU {config['mcpu']!r} for {config['mtriple']}")
    return target, text


def build_library(mod, args: argparse.Namespace, target, out_dir: Path, pass_ctx) -> Path:
    options = {"target": target, "relax_pipeline": args.relax_pipeline, "tir_pipeline": args.tir_pipeline,
               "exec_mode": args.exec_mode}
    if args.system_lib:
        options["system_lib"] = True
    with pass_ctx:
        executable = relax.build(mod, **options)

    so_path = out_dir / "model.so"
    if args.cross_cc:
        print(f"      cross compiler: {args.cross_cc}")
    executable.export_library(str(so_path), cc=args.cross_cc, options=args.cross_cc_options)
    print(f"      written: {so_path} ({so_path.stat().st_size / 1e6:.1f} MB)")
    return so_path


def compiled_metadata(ir_metadata: dict, args: argparse.Namespace, target, effective_target: str,
                      tuning_summary: Optional[dict], benchmark: Optional[dict]) -> dict:
    """Extend IR metadata with build details consumed by the serve images."""
    metadata = {**ir_metadata, "target": effective_target, "tvm_version": tvm.__version__}
    triple = target_triple(target)
    if triple:
        metadata["target_triple"] = triple
    if os.environ.get("TVM_GIT_COMMIT"):
        metadata["tvm_git_commit"] = os.environ["TVM_GIT_COMMIT"]
    if effective_target != args.target:
        metadata["target_requested"] = args.target
    metadata.update(opt_level=args.opt_level, exec_mode=args.exec_mode, relax_pipeline=args.relax_pipeline,
                    tir_pipeline=args.tir_pipeline)
    metadata["toolchain"] = {
        "compiler": args.cross_cc, "options": args.cross_cc_options,
        "host_triple": tvm.target.codegen.llvm_get_system_triple(),
    }
    if tuning_summary is not None:
        metadata["meta_schedule"] = tuning_summary
    if benchmark is not None:
        metadata["benchmark"] = benchmark
    if args.system_lib:
        metadata["system_lib"] = True
    if args.tag:
        metadata["tag"] = args.tag
    return metadata


def main(argv: Optional[list[str]] = None) -> None:
    args = parse_args(argv)
    ir_dir = Path(args.ir_dir).resolve()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    step(1, STEPS, f"loading the IR from {ir_dir}")
    mod = load_ir(ir_dir)
    ir_metadata = read_json(ir_dir / "metadata.json") if (ir_dir / "metadata.json").exists() else {"entry": "main"}
    weights = load_weights(ir_dir, args.params_file)
    if weights:
        entry, count, mod = embed_weights(mod, weights)
        print(f"      embedded {count} weights into {entry}()")

    step(2, STEPS, f"target {args.target} (opt_level={args.opt_level}, exec_mode={args.exec_mode})")
    target, effective_target = resolve_target(args.target, args.target_num_cores)
    args.cross_cc = toolchain.check_toolchain(target, args.cross_cc, args.cross_cc_options)
    execution_error = toolchain.local_execution_error(target)
    if args.tuning_mode == "tune" and args.tuning_runner == "local" and execution_error:
        fail(execution_error + "; use tuning_runner=rpc or tuning_mode=apply")
    if args.tuning_mode == "tune" and target.attrs.get("num-cores") is not None:
        # Candidates must be timed with the thread count the schedules are built for.
        os.environ.setdefault("TVM_NUM_THREADS", str(target.attrs["num-cores"]))
        print(f"      tuning threads={os.environ['TVM_NUM_THREADS']}")
    pass_ctx = tvm.transform.PassContext(opt_level=args.opt_level)

    tuning_summary = None
    if args.tuning_mode == "off":
        step(3, STEPS, "MetaSchedule tuning off")
    else:
        step(3, STEPS, f"MetaSchedule {args.tuning_mode}")
        mod, tuning_summary = tuning.run(args, mod, target, effective_target, ir_metadata, out_dir, pass_ctx, cpu_count())

    step(4, STEPS, "building model.so")
    so_path = build_library(mod, args, target, out_dir, pass_ctx)

    step(5, STEPS, "benchmarking model.so")
    benchmark = benchmark_library(so_path, ir_metadata, args.benchmark_runs, execution_error, args.system_lib)
    print(f"      {json.dumps(benchmark) if benchmark is not None else 'off'}")

    step(6, STEPS, "writing metadata.json")
    metadata = compiled_metadata(ir_metadata, args, target, effective_target, tuning_summary, benchmark)
    write_json(out_dir / "metadata.json", metadata)

    step(7, STEPS, "publishing the Model (kind tvm-so)")
    from publish import publish_compiled_model

    publish_compiled_model(out_dir, f"{args.name}-{args.tag or 'so'}", metadata, args.source_ir_key)
    print("Done")


if __name__ == "__main__":
    main()
