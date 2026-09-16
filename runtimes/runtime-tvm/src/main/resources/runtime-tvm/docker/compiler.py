#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""tvm+compile: Relax IR -> model.so.

The steps, in order:
1. load the Relax IR written by tvm+build and bind its weights (params.bin) as constants;
2. resolve the TVM target (for example mcpu=native becomes the real host CPU);
3. optionally tune the operators with MetaSchedule, or apply a database tuned earlier;
4. build the Relax VM executable and export it as model.so;
5. time a few inferences of model.so, when this machine can run it;
6. write metadata.json and publish everything as a tvm-so Model.

--target is passed as-is to tvm.target.Target: plain "llvm" or the JSON form, for example
{"kind":"llvm","mcpu":"x86-64-v3"}. External libraries (cuDNN, cuBLAS) go inside it.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import tvm
from tvm import relax
from tvm.s_tir import meta_schedule as ms
from tvm.s_tir.meta_schedule.relax_integration import extract_tasks, tune_relax
from tvm.tirx.function import PrimFunc

TUNING_WORKLOAD_FILE = "database_workload.json"
TUNING_RECORD_FILE = "database_tuning_record.json"
TUNING_MANIFEST_FILE = "manifest.json"
TUNING_COVERAGE_FILE = "coverage.json"
# MetaSchedule measures this many candidates for each task in one round (tune_relax default).
DEFAULT_TRIALS_PER_ITER = 64
BENCHMARK_TIMEOUT_SEC = 900


# --------------------------------------------------------------------------- command line


def parse_bool(value: Optional[str], default: bool) -> bool:
    """Reads the true/false strings forwarded by entrypoint.sh ("true", "1", "yes", ...)."""
    if value is None:
        return default
    return value.lower() in ("1", "true", "yes", "y", "on")


def fail(message: str) -> None:
    """Stops the compile with a readable error and exit code 2, like argparse does."""
    print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(2)


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    ap = argparse.ArgumentParser(description="TVM Relax IR -> model.so")

    model = ap.add_argument_group("model")
    model.add_argument("--ir-dir", required=True, help="folder with model.relax.json and metadata.json")
    model.add_argument("--output", required=True, help="folder that receives model.so and metadata.json")
    model.add_argument("--params-file", default=None,
                       help="params.bin to bind; defaults to the params.bin next to the IR")
    model.add_argument("--tag", default=None, help="recorded in metadata and appended to the Model name")

    build = ap.add_argument_group("target and build")
    build.add_argument("--target", required=True,
                       help='TVM target: "llvm" or the JSON form, e.g. {"kind":"llvm","mcpu":"x86-64-v3"}')
    build.add_argument("--target-num-cores", type=int, default=None,
                       help="cores the generated code assumes; also the tuning thread count")
    build.add_argument("--opt-level", type=int, default=3)
    build.add_argument("--cross-cc", default=None,
                       help="cross C++ compiler, e.g. aarch64-linux-gnu-g++ or arm-linux-gnueabihf-g++")
    build.add_argument("--exec-mode", default="bytecode", choices=["bytecode", "compiled"])
    build.add_argument("--relax-pipeline", default="default")
    build.add_argument("--tir-pipeline", default="default")
    build.add_argument("--system-lib", default="false")
    build.add_argument("--benchmark-runs", type=int, default=10,
                       help="timed inferences of the finished model.so; 0 disables the benchmark")

    tuning = ap.add_argument_group("MetaSchedule tuning")
    tuning.add_argument("--tuning-mode", choices=["off", "tune", "apply"], default="off",
                        help="standard Apache TVM MetaSchedule lifecycle")
    tuning.add_argument("--tuning-trials", type=int, default=None,
                        help="global trial budget; required in tune mode")
    tuning.add_argument("--max-trials-per-task", type=int, default=16,
                        help="most trials a single task may use")
    tuning.add_argument("--tuning-trials-per-iter", type=int, default=DEFAULT_TRIALS_PER_ITER,
                        help="candidates measured for each task in one round")
    tuning.add_argument("--tuning-ops", default=None,
                        help="comma-separated filters on the task names, e.g. conv2d")
    tuning.add_argument("--tuning-database", default=None,
                        help="earlier MetaSchedule work_dir or compiled Model folder to resume or apply")
    tuning.add_argument("--tuning-runner", choices=["local", "rpc"], default="local",
                        help="where candidates are measured: this machine or an RPC device")
    tuning.add_argument("--tuning-workers", type=int, default=None,
                        help="parallel builder workers; defaults to the target core count")
    tuning.add_argument("--tuning-seed", type=int, default=0)
    tuning.add_argument("--tuning-number", type=int, default=3)
    tuning.add_argument("--tuning-repeat", type=int, default=1)
    tuning.add_argument("--tuning-min-repeat-ms", type=int, default=100)
    tuning.add_argument("--tuning-enable-cpu-cache-flush", default="false")
    tuning.add_argument("--tuning-builder-timeout-sec", type=float, default=30.0)
    tuning.add_argument("--tuning-runner-timeout-sec", type=float, default=30.0)
    tuning.add_argument("--tuning-alloc-repeat", type=int, default=1)
    tuning.add_argument("--allow-partial-tuning", default="false",
                        help="publish even when the budget or database does not cover every selected task")

    rpc = ap.add_argument_group("RPC runner (measure on the target device)")
    rpc.add_argument("--rpc-tracker-host", default=None)
    rpc.add_argument("--rpc-tracker-port", type=int, default=None)
    rpc.add_argument("--rpc-tracker-key", default=None)
    rpc.add_argument("--rpc-session-timeout-sec", type=int, default=60)

    args = ap.parse_args(argv)
    args.system_lib = parse_bool(args.system_lib, False)
    args.allow_partial_tuning = parse_bool(args.allow_partial_tuning, False)
    args.tuning_enable_cpu_cache_flush = parse_bool(args.tuning_enable_cpu_cache_flush, False)
    args.tuning_ops = [item.strip() for item in (args.tuning_ops or "").split(",") if item.strip()] or None
    validate_args(args)
    return args


def validate_args(args: argparse.Namespace) -> None:
    """Checks the combinations argparse cannot check on its own."""
    at_least_one = {
        "--target-num-cores": args.target_num_cores,
        "--tuning-trials": args.tuning_trials,
        "--max-trials-per-task": args.max_trials_per_task,
        "--tuning-trials-per-iter": args.tuning_trials_per_iter,
        "--tuning-workers": args.tuning_workers,
        "--tuning-number": args.tuning_number,
        "--tuning-repeat": args.tuning_repeat,
        "--tuning-alloc-repeat": args.tuning_alloc_repeat,
        "--rpc-session-timeout-sec": args.rpc_session_timeout_sec,
    }
    for flag, value in at_least_one.items():
        if value is not None and value < 1:
            fail(f"{flag} must be >= 1")
    if args.tuning_min_repeat_ms < 0 or args.benchmark_runs < 0:
        fail("--tuning-min-repeat-ms and --benchmark-runs must be >= 0")
    if args.tuning_builder_timeout_sec <= 0 or args.tuning_runner_timeout_sec <= 0:
        fail("tuning timeouts must be > 0")
    if args.relax_pipeline == "static_shape_tuning":
        fail("use --tuning-mode tune; --relax-pipeline remains the final build pipeline")

    if args.tuning_mode == "tune" and args.tuning_trials is None:
        fail("tuning_mode=tune requires --tuning-trials")
    if args.tuning_mode == "apply" and not args.tuning_database:
        fail("tuning_mode=apply requires --tuning-database")
    # The local runner executes the candidates here, which cannot run code for another CPU.
    if args.tuning_mode == "tune" and args.cross_cc and args.tuning_runner != "rpc":
        fail("cannot measure a cross-compiled target locally; tune on the target device "
             "with --tuning-runner rpc or compile with tuning_mode=apply")
    if args.tuning_mode == "tune" and args.tuning_runner == "rpc":
        missing = [
            flag
            for flag, value in (
                ("--rpc-tracker-host", args.rpc_tracker_host),
                ("--rpc-tracker-port", args.rpc_tracker_port),
                ("--rpc-tracker-key", args.rpc_tracker_key),
            )
            if value is None
        ]
        if missing:
            fail("tuning_runner=rpc requires " + ", ".join(missing))


# --------------------------------------------------------------------------- loading


def load_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"invalid JSON file {path}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"expected a JSON object in {path}")
    return value


def load_ir(ir_dir: Path):
    """Loads model.relax.json, the round-trip safe IR.

    model.relax.ir, the TVMScript dump next to it, is for people only: it cannot be loaded
    back when the weights are constants.
    """
    json_path = ir_dir / "model.relax.json"
    if not json_path.exists():
        print(f"ERROR: model.relax.json not found in {ir_dir}", file=sys.stderr)
        sys.exit(2)
    return tvm.ir.load_json(json_path.read_text())


def _load_param_file(path: Path) -> dict:
    """Loads a params.bin (save_param_dict format) into {name: Tensor}."""
    from tvm.runtime import load_param_dict

    # TVM 0.26 wants a flat Map<str, Tensor>: the old grouped {fn: [v0, v1, ...]} makes
    # VMLink reject every value as an ffi.Array.
    return dict(load_param_dict(path.read_bytes()).items())


def load_params(ir_dir: Path, params_file: Optional[str]) -> Optional[dict]:
    """Weights to bind: --params-file when given, else the params.bin that tvm+build writes
    next to the IR when keep_params_in_input=true."""
    if params_file:
        path = Path(params_file)
        if not path.exists():
            print(f"ERROR: --params-file {path} not found", file=sys.stderr)
            sys.exit(3)
        try:
            return _load_param_file(path)
        except Exception as error:  # noqa: BLE001
            print(f"WARN: failed to load params: {error}", file=sys.stderr)
            return None
    path = ir_dir / "params.bin"
    if not path.exists():
        return None
    print("      auto-detected params.bin in ir-dir")
    return _load_param_file(path)


def _var_name(var) -> str:
    """Name of a Relax variable: TVM 0.26 exposes it as `name`, older releases as `name_hint`."""
    return var.name if hasattr(var, "name") else var.name_hint


def _bind_params(mod, flat):
    """Binds detached weights into the entry function as constants.

    The keys are either the function's parameter names or positional ("<fn>.<i>").
    Positional keys map, in order, onto the trailing parameters, the ones after the real
    inputs. Returns (entry name, number of bound weights, new module).
    """
    entry, func = "main", None
    for gv, f in mod.functions_items():
        if isinstance(f, relax.Function):
            if gv.name_hint == "main":
                entry, func = "main", f
                break
            if func is None:
                entry, func = gv.name_hint, f
    fparams = list(func.params)
    pnames = {_var_name(v) for v in fparams}
    if set(flat).issubset(pnames):
        named = dict(flat)
    else:
        num_input = len(fparams) - len(flat)
        if num_input < 0:
            raise ValueError(f"{len(flat)} saved params > {len(fparams)} function params")
        ordered = [flat[k] for k in sorted(flat, key=lambda k: int(k.rpartition(".")[2]))]
        named = {_var_name(v): t for v, t in zip(fparams[num_input:], ordered)}
    return entry, len(named), relax.transform.BindParams(entry, named)(mod)


# --------------------------------------------------------------------------- target


def available_cpu_count() -> int:
    """CPUs this process may use (the container limit, not the size of the node)."""
    try:
        return len(os.sched_getaffinity(0))
    except AttributeError:
        return os.cpu_count() or 1


def _resolve_target(target_text: str, target_num_cores: Optional[int]):
    """Returns (Target, effective target text).

    LLVM accepts mcpu=native but silently falls back to a generic, non-vectorized CPU for
    the inferred x86 triple. The concrete host CPU and triple are written instead, so the
    artifact says exactly what it was built for. --target-num-cores ends up in the target
    too, because the generated schedules depend on it.
    """
    requested = tvm.target.Target(target_text)
    resolve_native = requested.kind.name == "llvm" and str(requested.attrs.get("mcpu", "")) == "native"
    if not resolve_native and target_num_cores is None:
        return requested, target_text

    try:
        config = json.loads(target_text)
    except json.JSONDecodeError as error:
        if target_text.strip() != "llvm":
            raise ValueError("target_num_cores and mcpu=native require a JSON target or plain llvm") from error
        config = {"kind": "llvm"}
    if not isinstance(config, dict):
        raise ValueError("LLVM target must be a JSON object")

    if resolve_native:
        detected_cpu = tvm.target.codegen.llvm_get_system_cpu()
        detected_triple = tvm.target.codegen.llvm_get_system_triple()
        if not detected_cpu or detected_cpu in {"generic", "unknown"}:
            raise RuntimeError("LLVM could not detect a concrete native CPU")
        config["mcpu"] = detected_cpu
        if detected_triple:
            config["mtriple"] = detected_triple
        if target_num_cores is None:
            target_num_cores = available_cpu_count()
        print(f"      resolved mcpu=native -> mcpu={detected_cpu}, mtriple={config.get('mtriple')}")
    config["num-cores"] = target_num_cores
    print(f"      target num-cores={target_num_cores}")
    resolved_text = json.dumps(config, separators=(",", ":"))
    return tvm.target.Target(resolved_text), resolved_text


# --------------------------------------------------------------------------- MetaSchedule


@dataclass
class TuningDatabase:
    """The MetaSchedule database published under tuning/ in the compiled Model."""

    work_dir: Path
    reused: bool
    # "new", "manifest" or "legacy metadata": how the reused database was identified.
    identity_basis: str
    # Whether the records were tuned with weight prepacking (see prepare_for_tuning).
    weight_prepack: bool


def _module_sha256(mod) -> str:
    return hashlib.sha256(tvm.ir.save_json(mod).encode("utf-8")).hexdigest()


def _database_identity(source: Path) -> tuple[dict, str]:
    """What an earlier database was tuned for: its manifest.json, or for databases older
    than the manifest the metadata.json of the Model that contains it."""
    manifest_path = source / TUNING_MANIFEST_FILE
    if manifest_path.is_file():
        return load_json(manifest_path), "manifest"

    for metadata_path in (source / "metadata.json", source.parent / "metadata.json"):
        if metadata_path.is_file():
            metadata = load_json(metadata_path)
            if metadata.get("target") and metadata.get("tvm_version"):
                return {
                    "tvm_version": metadata["tvm_version"],
                    "target": metadata["target"],
                    "source_sha256": metadata.get("source_sha256"),
                    "tvm_git_commit": metadata.get("tvm_git_commit"),
                }, "legacy metadata"
    raise ValueError(
        f"tuning database under {source} has no {TUNING_MANIFEST_FILE} or "
        "compatible model metadata; retune it before reuse"
    )


def _validate_database_identity(actual: dict, expected: dict, basis: str) -> None:
    """A database only helps the exact TVM build, target and model it was tuned on."""
    mismatches = [
        f"{key}: database={actual.get(key)!r}, requested={expected.get(key)!r}"
        for key in ("tvm_version", "target")
        if actual.get(key) != expected.get(key)
    ]
    for key in ("ir_sha256", "source_sha256", "tvm_git_commit"):
        if actual.get(key) and expected.get(key) and actual[key] != expected[key]:
            mismatches.append(f"{key}: database={actual[key]!r}, requested={expected[key]!r}")
    if mismatches:
        raise ValueError(f"incompatible MetaSchedule database ({basis}): " + "; ".join(mismatches))


def _find_tuning_database(path: Path) -> Path:
    """Accepts a MetaSchedule work_dir or a compiled Model folder that contains tuning/."""
    for candidate in (path, path / "tuning"):
        if (candidate / TUNING_WORKLOAD_FILE).is_file() and (candidate / TUNING_RECORD_FILE).is_file():
            return candidate
    raise FileNotFoundError(
        f"MetaSchedule database not found under {path}; expected "
        f"{TUNING_WORKLOAD_FILE} and {TUNING_RECORD_FILE}"
    )


def open_tuning_database(
    out_dir: Path, database_path: Optional[str], identity: dict, weight_prepack: bool
) -> TuningDatabase:
    """Creates tuning/ in the output folder. With an earlier database, checks that it fits
    this IR and target, then copies it in so tuning resumes from it."""
    work_dir = out_dir / "tuning"
    work_dir.mkdir(parents=True, exist_ok=True)
    manifest_path = work_dir / TUNING_MANIFEST_FILE
    if not database_path:
        manifest_path.write_text(json.dumps({**identity, "cpu_weight_prepack": weight_prepack}, indent=2))
        return TuningDatabase(work_dir, False, "new", weight_prepack)

    source = _find_tuning_database(Path(database_path).resolve())
    actual, basis = _database_identity(source)
    _validate_database_identity(actual, identity, basis)
    # Records only match a module prepared the same way: databases from before weight
    # prepacking have no flag and keep working without it.
    weight_prepack = bool(actual.get("cpu_weight_prepack", False))
    if source.resolve() != work_dir.resolve():
        for name in (TUNING_WORKLOAD_FILE, TUNING_RECORD_FILE):
            shutil.copy2(source / name, work_dir / name)
    manifest_path.write_text(json.dumps(
        {**identity, "cpu_weight_prepack": weight_prepack, "reused_database_identity": basis}, indent=2
    ))
    print(f"      reusing MetaSchedule database from {source}")
    return TuningDatabase(work_dir, True, basis, weight_prepack)


def prepare_for_tuning(mod, target, pass_ctx, weight_prepack: bool):
    """The preparation of TVM's static_shape_tuning pipeline: decompose inference-only
    operators, then legalize and fuse them into TIR functions, the tuning tasks.

    With weight_prepack the constant weights are also marked as free to change layout,
    so the tuned schedules may repack them for the CPU (cpu_weight_prepack in TVM).
    """
    passes = [
        relax.transform.DecomposeOpsForInference(),
        relax.transform.CanonicalizeBindings(),
        relax.get_pipeline("zero"),
    ]
    if weight_prepack:
        passes.append(relax.transform.AttachAttrLayoutFreeBuffers())
    with target, pass_ctx:
        return tvm.transform.Sequential(passes)(mod)


def apply_tuning_database(prepared, database: TuningDatabase, target, pass_ctx, allow_partial: bool):
    """Swaps every covered TIR function for its best tuned schedule.

    With weight prepacking, the weights are then rewritten into the layout the schedules
    chose, and that rewrite is computed now, at compile time, so model.so still takes only
    the real inputs.
    """
    with target, pass_ctx:
        mod = relax.transform.MetaScheduleApplyDatabase(
            work_dir=str(database.work_dir), enable_warning=allow_partial
        )(prepared)
        if database.weight_prepack:
            mod = relax.transform.SplitLayoutRewritePreproc()(mod)
            mod = relax.transform.LiftTransformParams()(mod)
            mod = relax.transform.FoldConstant()(mod)
    return mod


def _select_tasks(tasks, op_names: Optional[list[str]]):
    if not op_names:
        return tasks
    return [task for task in tasks if any(op_name in task.task_name for op_name in op_names)]


def first_round_budget(task_count: int, max_trials_per_task: int, trials_per_iter: int) -> int:
    """Trials needed before every task has been measured at least once.

    MetaSchedule visits the tasks in turn and gives each one a batch of
    min(trials_per_iter, max_trials_per_task) candidates: with a smaller global budget the
    last tasks are never tuned.
    """
    return task_count * min(trials_per_iter, max_trials_per_task)


def _builder_and_runner(args: argparse.Namespace, workers: int):
    """The standard MetaSchedule builder (compiles candidates) and runner (times them)."""
    evaluator_config = ms.runner.EvaluatorConfig(
        number=args.tuning_number,
        repeat=args.tuning_repeat,
        min_repeat_ms=args.tuning_min_repeat_ms,
        enable_cpu_cache_flush=args.tuning_enable_cpu_cache_flush,
    )
    builder = ms.builder.LocalBuilder(max_workers=workers, timeout_sec=args.tuning_builder_timeout_sec)
    if args.tuning_runner == "rpc":
        runner = ms.runner.RPCRunner(
            rpc_config=ms.runner.RPCConfig(
                tracker_host=args.rpc_tracker_host,
                tracker_port=args.rpc_tracker_port,
                tracker_key=args.rpc_tracker_key,
                session_timeout_sec=args.rpc_session_timeout_sec,
            ),
            evaluator_config=evaluator_config,
            alloc_repeat=args.tuning_alloc_repeat,
            max_workers=workers,
        )
    else:
        runner = ms.runner.LocalRunner(
            timeout_sec=args.tuning_runner_timeout_sec,
            evaluator_config=evaluator_config,
            alloc_repeat=args.tuning_alloc_repeat,
        )
    return builder, runner


def tune(args: argparse.Namespace, prepared, selected_tasks, all_tasks, target, database, pass_ctx) -> int:
    """Measures candidates and stores the best ones in the database. Returns the workers used."""
    round_budget = first_round_budget(len(selected_tasks), args.max_trials_per_task, args.tuning_trials_per_iter)
    print(
        f"      MetaSchedule tune: {len(selected_tasks)}/{len(all_tasks)} tasks, "
        f"trials={args.tuning_trials}, max_per_task={args.max_trials_per_task}, "
        f"per_iter={args.tuning_trials_per_iter}"
    )
    print(
        f"      budget for one round over every task: {round_budget}; "
        f"for {args.max_trials_per_task} trials on every task: {len(selected_tasks) * args.max_trials_per_task}"
    )
    if args.tuning_trials < round_budget:
        message = f"global tuning budget is below one complete task round ({args.tuning_trials} < {round_budget})"
        if not args.allow_partial_tuning:
            fail(message + "; set --allow-partial-tuning true for a smoke test")
        print(f"WARN: {message}", file=sys.stderr)

    workers = args.tuning_workers or args.target_num_cores or available_cpu_count()
    builder, runner = _builder_and_runner(args, workers)
    print(f"      runner={args.tuning_runner}, builder_workers={workers}, seed={args.tuning_seed}")
    with target, pass_ctx:
        tune_relax(
            mod=prepared,
            params={},
            target=target,
            work_dir=str(database.work_dir),
            max_trials_global=args.tuning_trials,
            max_trials_per_task=args.max_trials_per_task,
            op_names=args.tuning_ops,
            num_trials_per_iter=args.tuning_trials_per_iter,
            builder=builder,
            runner=runner,
            seed=args.tuning_seed,
        )
    return workers


def _database_coverage(work_dir: Path, mod, target, op_names):
    """Queries the records with the same public API and keys used by TVM's apply pass.

    Returns (records, successful records, per-function coverage).
    """
    workload_file = work_dir / TUNING_WORKLOAD_FILE
    record_file = work_dir / TUNING_RECORD_FILE
    if not workload_file.exists() or not record_file.exists():
        return 0, 0, []
    database = ms.database.JSONDatabase(
        path_workload=str(workload_file),
        path_tuning_record=str(record_file),
        allow_missing=False,
    )
    records = database.get_all_tuning_records()
    # A run time of 1e10 s is how MetaSchedule marks a candidate that failed.
    usable = sum(1 for record in records if record.run_secs and min(float(v) for v in record.run_secs) < 1e10)
    normalize_mod = tvm.get_global_func("tvm.s_tir.meta_schedule.normalize_mod")
    coverage = []
    for global_var, function in mod.functions_items():
        if not isinstance(function, PrimFunc):
            continue
        name = global_var.name_hint
        if op_names and not any(op_name in name for op_name in op_names):
            continue
        record = database.query_tuning_record(normalize_mod(function), target, name)
        run_secs = [float(v) for v in record.run_secs] if record is not None and record.run_secs else []
        coverage.append({
            "name": name,
            "covered": bool(run_secs) and min(run_secs) < 1e10,
            "best_run_ms": min(run_secs) * 1000 if run_secs else None,
        })
    return len(records), usable, coverage


def run_meta_schedule(args, mod, target, effective_target: str, ir_dir: Path, out_dir: Path, pass_ctx):
    """tune measures new candidates, apply reuses a database tuned earlier. Both return the
    tuned module and the summary recorded in metadata.json."""
    ir_metadata_path = ir_dir / "metadata.json"
    ir_metadata = load_json(ir_metadata_path) if ir_metadata_path.is_file() else {}
    identity = {
        "schema_version": 1,
        "tvm_version": tvm.__version__,
        "target": effective_target,
        "ir_sha256": _module_sha256(mod),
        "source_sha256": ir_metadata.get("source_sha256"),
        "tvm_git_commit": os.environ.get("TVM_GIT_COMMIT"),
    }
    database = open_tuning_database(
        out_dir, args.tuning_database, identity, weight_prepack=target.kind.name == "llvm"
    )
    prepared = prepare_for_tuning(mod, target, pass_ctx, database.weight_prepack)
    tasks = extract_tasks(prepared, target, params={})
    selected_tasks = _select_tasks(tasks, args.tuning_ops)
    if not selected_tasks:
        fail(f"no MetaSchedule task matches {args.tuning_ops}; available tasks: {[t.task_name for t in tasks]}")
    (database.work_dir / "tasks.json").write_text(json.dumps({
        "all": [{"name": task.task_name, "weight": int(task.weight)} for task in tasks],
        "selected": [task.task_name for task in selected_tasks],
        "filters": args.tuning_ops,
    }, indent=2))

    workers = None
    if args.tuning_mode == "tune":
        workers = tune(args, prepared, selected_tasks, tasks, target, database, pass_ctx)
    else:
        print(f"      MetaSchedule apply: database covers up to {len(tasks)} extracted tasks")

    attempts, records, coverage = _database_coverage(database.work_dir, prepared, target, args.tuning_ops)
    missing = [item["name"] for item in coverage if not item["covered"]]
    (database.work_dir / TUNING_COVERAGE_FILE).write_text(json.dumps({
        "selected_function_count": len(coverage),
        "covered_function_count": len(coverage) - len(missing),
        "missing_functions": missing,
        "functions": coverage,
    }, indent=2))
    if records == 0:
        fail("MetaSchedule database contains no successful tuning records; inspect tuning/logs")
    if missing and not args.allow_partial_tuning:
        fail(f"MetaSchedule database does not cover every selected function: {missing}; "
             "set --allow-partial-tuning true only when partial optimization is intentional")

    tuned = apply_tuning_database(prepared, database, target, pass_ctx, args.allow_partial_tuning)
    info = {
        "mode": args.tuning_mode,
        "database": "tuning",
        "database_reused": database.reused,
        "database_identity": database.identity_basis,
        "weight_prepack": database.weight_prepack,
        "task_count": len(tasks),
        "selected_task_count": len(selected_tasks),
        "selected_tasks": [task.task_name for task in selected_tasks],
        "task_filters": args.tuning_ops,
        "trials": args.tuning_trials if args.tuning_mode == "tune" else 0,
        "max_trials_per_task": args.max_trials_per_task,
        "trials_per_iter": args.tuning_trials_per_iter,
        "runner": args.tuning_runner,
        "workers": workers,
        "seed": args.tuning_seed,
        "alloc_repeat": args.tuning_alloc_repeat,
        "allow_partial": args.allow_partial_tuning,
        "evaluator": {
            "number": args.tuning_number,
            "repeat": args.tuning_repeat,
            "min_repeat_ms": args.tuning_min_repeat_ms,
            "enable_cpu_cache_flush": args.tuning_enable_cpu_cache_flush,
        },
        "attempt_count": attempts,
        "record_count": records,
        "covered_function_count": len(coverage) - len(missing),
        "missing_functions": missing,
    }
    return tuned, info


# --------------------------------------------------------------------------- build and benchmark


def build_library(mod, args: argparse.Namespace, target, out_dir: Path, pass_ctx) -> Path:
    """Runs the standard Relax/TIR build pipeline and exports model.so."""
    build_kwargs = {
        "target": target,
        "relax_pipeline": args.relax_pipeline,
        "tir_pipeline": args.tir_pipeline,
        "exec_mode": args.exec_mode,
    }
    if args.system_lib:
        build_kwargs["system_lib"] = True
    with pass_ctx:
        executable = relax.build(mod, **build_kwargs)

    so_path = out_dir / "model.so"
    if args.cross_cc:
        print(f"      cross-cc: {args.cross_cc}")
    executable.export_library(str(so_path), **({"cc": args.cross_cc} if args.cross_cc else {}))
    print(f"      written: {so_path} ({so_path.stat().st_size / 1e6:.1f} MB)")
    return so_path


def benchmark_library(so_path: Path, metadata: dict, args: argparse.Namespace) -> Optional[dict]:
    """Times --benchmark-runs inferences of model.so on random inputs.

    It runs in a child process, so a crash (for example an instruction this CPU does not
    have) cannot take the compile down. Returns None when disabled, else the timings or
    the reason they were skipped.
    """
    if args.benchmark_runs <= 0:
        return None
    if args.cross_cc:
        return {"skipped": f"cross-compiled with {args.cross_cc}"}
    if args.system_lib:
        return {"skipped": "a system-lib module cannot be loaded as a shared library"}
    if not metadata.get("inputs"):
        return {"skipped": "metadata.json declares no inputs"}

    command = [
        sys.executable, str(Path(__file__).resolve()), "--benchmark-worker",
        str(so_path), metadata.get("entry", "main"), json.dumps(metadata["inputs"]), str(args.benchmark_runs),
    ]
    try:
        done = subprocess.run(command, capture_output=True, text=True, timeout=BENCHMARK_TIMEOUT_SEC)
    except subprocess.TimeoutExpired:
        return {"skipped": f"no result within {BENCHMARK_TIMEOUT_SEC} s"}
    if done.returncode < 0:
        return {"skipped": f"model.so crashed with signal {-done.returncode}; "
                           "the target may need CPU features this machine does not have"}
    if done.returncode != 0:
        lines = done.stderr.strip().splitlines()
        return {"skipped": (lines[-1] if lines else f"exit code {done.returncode}")[:300]}
    return json.loads(done.stdout.strip().splitlines()[-1])


def benchmark_worker(so_path: str, entry: str, inputs_json: str, runs_text: str) -> None:
    """Child process of benchmark_library: loads model.so in a Relax VM, feeds random
    inputs shaped like metadata.json and prints the timings as one JSON line."""
    import numpy as np

    runs = int(runs_text)
    warmup = min(5, runs)
    device = tvm.cpu()
    vm = relax.VirtualMachine(tvm.runtime.load_module(so_path), device)
    rng = np.random.default_rng(0)
    tensors = []
    for spec in json.loads(inputs_json):
        dtype = np.dtype(spec.get("dtype", "float32"))
        shape = [dim if dim > 0 else 1 for dim in spec["shape"]]
        if np.issubdtype(dtype, np.floating):
            data = rng.standard_normal(shape).astype(dtype)
        else:
            # Small values fit every integer type, quantized inputs included.
            low = 0 if np.issubdtype(dtype, np.unsignedinteger) or dtype == np.bool_ else -8
            data = rng.integers(low, 2 if dtype == np.bool_ else 8, size=shape).astype(dtype)
        tensors.append(tvm.runtime.tensor(data, device))

    run = vm[entry]
    for _ in range(warmup):
        run(*tensors)
    times = []
    for _ in range(runs):
        start = time.perf_counter()
        run(*tensors)
        times.append((time.perf_counter() - start) * 1000)
    times.sort()

    def percentile(q: float) -> float:
        return round(times[min(runs - 1, int(round(q * (runs - 1))))], 4)

    print(json.dumps({
        "runs": runs,
        "warmup": warmup,
        "threads": os.environ.get("TVM_NUM_THREADS", "default"),
        "mean_ms": round(sum(times) / runs, 4),
        "p50_ms": percentile(0.5),
        "p90_ms": percentile(0.9),
        "min_ms": round(times[0], 4),
    }))


# --------------------------------------------------------------------------- metadata and publish


def write_metadata(metadata: dict, args: argparse.Namespace, out_dir: Path, effective_target: str,
                   tuning_info: Optional[dict], benchmark: Optional[dict]) -> dict:
    """metadata.json of the compiled model: the IR metadata (entry, inputs, outputs, ...)
    plus how the library was built. The serve runtimes read it at startup."""
    meta = dict(metadata)
    meta["target"] = effective_target
    meta["tvm_version"] = tvm.__version__
    if os.environ.get("TVM_GIT_COMMIT"):
        meta["tvm_git_commit"] = os.environ["TVM_GIT_COMMIT"]
    if effective_target != args.target:
        meta["target_requested"] = args.target
    meta["opt_level"] = args.opt_level
    meta["exec_mode"] = args.exec_mode
    meta["relax_pipeline"] = args.relax_pipeline
    meta["tir_pipeline"] = args.tir_pipeline
    if tuning_info is not None:
        meta["meta_schedule"] = tuning_info
    if benchmark is not None:
        meta["benchmark"] = benchmark
    if args.system_lib:
        meta["system_lib"] = True
    if args.tag:
        meta["tag"] = args.tag
    path = out_dir / "metadata.json"
    path.write_text(json.dumps(meta, indent=2))
    print(f"      written: {path}")
    return meta


def publish(out_dir: Path, meta: dict, effective_target: str, args: argparse.Namespace) -> None:
    """Publishes the output folder as a tvm-so Model named <function>-<tag>."""
    sys.path.insert(0, str(Path(__file__).parent))
    from _dh_publish import publish_model_and_register_output

    publish_model_and_register_output(
        out_dir=out_dir,
        name=f"{os.environ.get('TVM_FUNCTION_NAME', 'model')}-{args.tag or 'so'}",
        output_key="compiled_so",
        kind="tvm-so",
        spec={
            "framework": "tvm",
            "algorithm": "tvm-compiled-so",
            "entry": meta.get("entry", "main"),
            "inputs": meta.get("inputs"),
            "outputs": meta.get("outputs"),
            "target": effective_target,
            "opt_level": args.opt_level,
            "manifest": meta,
        },
        relationship_source=os.environ.get("TVM_SOURCE_IR_KEY") or None,
    )


def main() -> None:
    args = parse_args()
    ir_dir = Path(args.ir_dir).resolve()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)

    print(f"[1/7] Loading IR from {ir_dir}")
    mod = load_ir(ir_dir)
    params = load_params(ir_dir, args.params_file)
    metadata_path = ir_dir / "metadata.json"
    metadata = json.loads(metadata_path.read_text()) if metadata_path.exists() else {"entry": "main"}

    print(f"[2/7] Preparing (target='{args.target}', opt_level={args.opt_level}, "
          f"exec_mode={args.exec_mode}, relax_pipeline={args.relax_pipeline})")
    target, effective_target = _resolve_target(args.target, args.target_num_cores)
    if args.tuning_mode == "tune" and target.kind.name != "llvm":
        fail("local MetaSchedule tuning currently supports LLVM/CPU targets only")
    if args.tuning_mode == "tune" and target.attrs.get("num-cores") is not None:
        # Candidates must be timed with the thread count the schedules are built for.
        os.environ.setdefault("TVM_NUM_THREADS", str(target.attrs["num-cores"]))
        print(f"      tuning runtime threads={os.environ['TVM_NUM_THREADS']}")
    # relax.build does not fold weights: binding them as constants leaves model.so with
    # only the real inputs, which is what the serve runtimes call it with.
    if params:
        entry, bound_count, mod = _bind_params(mod, params)
        print(f"      bound {bound_count} params into '{entry}()'")

    pass_ctx = tvm.transform.PassContext(opt_level=args.opt_level)
    tuning_info = None
    if args.tuning_mode == "off":
        print("[3/7] MetaSchedule tuning off")
    else:
        print(f"[3/7] MetaSchedule {args.tuning_mode}")
        mod, tuning_info = run_meta_schedule(args, mod, target, effective_target, ir_dir, out_dir, pass_ctx)

    print("[4/7] Building model.so with the standard Relax/TIR pipeline")
    so_path = build_library(mod, args, target, out_dir, pass_ctx)

    print("[5/7] Benchmarking model.so")
    benchmark = benchmark_library(so_path, metadata, args)
    print(f"      {json.dumps(benchmark) if benchmark is not None else 'disabled'}")

    print("[6/7] Writing metadata.json")
    meta = write_metadata(metadata, args, out_dir, effective_target, tuning_info, benchmark)

    print("[7/7] Publishing the tvm-so Model via the digitalhub SDK")
    publish(out_dir, meta, effective_target, args)
    print("Done")


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "--benchmark-worker":
        benchmark_worker(*sys.argv[2:])
    else:
        main()
