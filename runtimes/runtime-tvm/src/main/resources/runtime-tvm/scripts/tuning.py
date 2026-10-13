# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""MetaSchedule tune/apply using TVM's static_shape_tuning sequence.

Databases under tuning/ are specific to the IR, TVM build and target.
CPU weight prepacking folds scheduled layouts into constants at compile time.
"""

from __future__ import annotations

import argparse
import hashlib
import os
import shutil
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import tvm
from tvm import relax
from tvm.s_tir import meta_schedule as ms
from tvm.s_tir.meta_schedule.relax_integration import extract_tasks, tune_relax
from tvm.tirx.function import PrimFunc

from common import fail, option, read_json, to_bool, to_list, write_json

WORKLOAD_FILE = "database_workload.json"
RECORD_FILE = "database_tuning_record.json"
MANIFEST_FILE = "manifest.json"
COVERAGE_FILE = "coverage.json"
TASKS_FILE = "tasks.json"
# Matches tune_relax's default.
DEFAULT_TRIALS_PER_ITER = 64
# MetaSchedule's failed-candidate sentinel.
FAILED_RUN_SECS = 1e10


def add_options(parser: argparse.ArgumentParser) -> None:
    tuning = parser.add_argument_group("MetaSchedule tuning")
    option(tuning, "--tuning-mode", "TVM_TUNING_MODE", default="off", choices=["off", "tune", "apply"],
           help="off, tune (search schedules) or apply (reuse a database)")
    option(tuning, "--tuning-trials", "TVM_TUNING_TRIALS", type=int, help="max_trials_global: total trials, required to tune")
    option(tuning, "--max-trials-per-task", "TVM_MAX_TRIALS_PER_TASK", type=int, default=16, help="max_trials_per_task")
    option(tuning, "--tuning-trials-per-iter", "TVM_TUNING_TRIALS_PER_ITER", type=int, default=DEFAULT_TRIALS_PER_ITER,
           help="num_trials_per_iter: candidates measured per task in one round")
    option(tuning, "--tuning-ops", "TVM_TUNING_OPS", type=to_list, help="op_names: comma-separated filters on the task names")
    option(tuning, "--tuning-database", "TVM_TUNING_DATABASE", help="earlier database or compiled Model folder to resume or apply")
    option(tuning, "--tuning-runner", "TVM_TUNING_RUNNER", default="local", choices=["local", "rpc"],
           help="where candidates are measured: this Job or an RPC device")
    option(tuning, "--tuning-workers", "TVM_TUNING_WORKERS", type=int,
           help="builder max_workers; with rpc also the parallel device connections")
    option(tuning, "--tuning-seed", "TVM_TUNING_SEED", type=int, default=0, help="random seed of the search")
    option(tuning, "--tuning-number", "TVM_TUNING_NUMBER", type=int, default=3, help="evaluator number: runs per measurement")
    option(tuning, "--tuning-repeat", "TVM_TUNING_REPEAT", type=int, default=1, help="evaluator repeat: measurements per candidate")
    option(tuning, "--tuning-min-repeat-ms", "TVM_TUNING_MIN_REPEAT_MS", type=int, default=100, help="evaluator min_repeat_ms")
    option(tuning, "--tuning-alloc-repeat", "TVM_TUNING_ALLOC_REPEAT", type=int, default=1, help="runner alloc_repeat")
    option(tuning, "--tuning-enable-cpu-cache-flush", "TVM_TUNING_ENABLE_CPU_CACHE_FLUSH", type=to_bool, default=False,
           help="evaluator enable_cpu_cache_flush")
    option(tuning, "--tuning-builder-timeout-sec", "TVM_TUNING_BUILDER_TIMEOUT_SEC", type=float, default=30.0,
           help="builder timeout_sec")
    option(tuning, "--tuning-runner-timeout-sec", "TVM_TUNING_RUNNER_TIMEOUT_SEC", type=float, default=30.0,
           help="local runner timeout_sec (rpc uses the session timeout)")
    option(tuning, "--allow-partial-tuning", "TVM_ALLOW_PARTIAL_TUNING", type=to_bool, default=False,
           help="accept a budget or database that does not cover every task")

    rpc = parser.add_argument_group("RPC runner (measure on the target device)")
    option(rpc, "--rpc-tracker-host", "TVM_RPC_TRACKER_HOST", help="tracker_host")
    option(rpc, "--rpc-tracker-port", "TVM_RPC_TRACKER_PORT", type=int, help="tracker_port")
    option(rpc, "--rpc-tracker-key", "TVM_RPC_TRACKER_KEY", help="tracker_key")
    option(rpc, "--rpc-session-timeout-sec", "TVM_RPC_SESSION_TIMEOUT_SEC", type=int, default=60, help="session_timeout_sec")


def parse_options(parser: argparse.ArgumentParser, argv: Optional[list[str]]) -> argparse.Namespace:
    """Ignore inactive CLI/env values before argparse converts their types."""
    selector = argparse.ArgumentParser(add_help=False, allow_abbrev=False)
    option(selector, "--tuning-mode", "TVM_TUNING_MODE", default="off", choices=["off", "tune", "apply"])
    option(selector, "--tuning-runner", "TVM_TUNING_RUNNER", default="local")
    selected, _ = selector.parse_known_args(argv)
    for group in parser._action_groups:
        if group.title not in ("MetaSchedule tuning", "RPC runner (measure on the target device)"):
            continue
        for action in group._group_actions:
            if not option_is_active(action.dest, selected.tuning_mode, selected.tuning_runner):
                action.type = lambda _: None
                action.choices = None
                action.default = None
    return parser.parse_args(argv)


def option_is_active(name: str, mode: str, runner: str) -> bool:
    if name == "tuning_mode":
        return True
    if mode == "off":
        return False
    if mode == "apply":
        return name in ("tuning_database", "tuning_ops", "allow_partial_tuning")
    if name.startswith("rpc_"):
        return runner == "rpc"
    return name != "tuning_runner_timeout_sec" or runner != "rpc"


def validate_options(args: argparse.Namespace) -> None:
    if args.tuning_mode not in ("off", "tune", "apply"):
        fail(f"tuning mode must be off, tune or apply, not {args.tuning_mode!r}")
    if args.tuning_mode == "off":
        return
    if args.tuning_mode == "apply":
        if not args.tuning_database:
            fail("tuning_mode=apply requires --tuning-database")
        return
    if args.tuning_runner not in ("local", "rpc"):
        fail(f"tuning runner must be local or rpc, not {args.tuning_runner!r}")
    for flag, value in {
        "--tuning-trials": args.tuning_trials,
        "--max-trials-per-task": args.max_trials_per_task,
        "--tuning-trials-per-iter": args.tuning_trials_per_iter,
        "--tuning-workers": args.tuning_workers,
        "--tuning-number": args.tuning_number,
        "--tuning-repeat": args.tuning_repeat,
        "--tuning-alloc-repeat": args.tuning_alloc_repeat,
    }.items():
        if value is not None and value < 1:
            fail(f"{flag} must be >= 1")
    if args.tuning_min_repeat_ms < 0:
        fail("--tuning-min-repeat-ms must be >= 0")
    if args.tuning_seed < 0:
        fail("--tuning-seed must be >= 0")
    if args.tuning_builder_timeout_sec <= 0 or (
        args.tuning_runner == "local" and args.tuning_runner_timeout_sec <= 0
    ):
        fail("tuning timeouts must be > 0")

    if args.tuning_trials is None:
        fail("tuning_mode=tune requires --tuning-trials")
    # Cross-compiled candidates must be measured on the target device.
    if args.cross_cc and args.tuning_runner != "rpc":
        fail("cannot measure a cross-compiled target locally; tune on the target device "
             "with --tuning-runner rpc or compile with tuning_mode=apply")
    if args.tuning_runner == "rpc":
        missing = [flag for flag, value in (("--rpc-tracker-host", args.rpc_tracker_host),
                                            ("--rpc-tracker-port", args.rpc_tracker_port),
                                            ("--rpc-tracker-key", args.rpc_tracker_key)) if not value]
        if missing:
            fail("tuning_runner=rpc requires " + ", ".join(missing))
        if not 1 <= args.rpc_tracker_port <= 65535:
            fail("--rpc-tracker-port must be between 1 and 65535")
        if args.rpc_session_timeout_sec < 1:
            fail("--rpc-session-timeout-sec must be >= 1")


@dataclass
class TuningDatabase:
    work_dir: Path
    reused: bool
    # "new", "manifest" or "legacy metadata".
    identity_basis: str
    weight_prepack: bool


def module_sha256(mod) -> str:
    return hashlib.sha256(tvm.ir.save_json(mod).encode("utf-8")).hexdigest()


def database_identity(source: Path) -> tuple[dict, str]:
    """Read the manifest, falling back to legacy Model metadata."""
    if (source / MANIFEST_FILE).is_file():
        return read_json(source / MANIFEST_FILE), "manifest"
    for metadata_path in (source / "metadata.json", source.parent / "metadata.json"):
        if metadata_path.is_file():
            metadata = read_json(metadata_path)
            if metadata.get("target") and metadata.get("tvm_version"):
                keys = ("tvm_version", "target", "source_sha256", "tvm_git_commit")
                return {key: metadata.get(key) for key in keys}, "legacy metadata"
    raise ValueError(f"tuning database under {source} has no {MANIFEST_FILE} or compatible model metadata; "
                     "retune it before reuse")


def check_identity(actual: dict, expected: dict, basis: str) -> None:
    """A database only helps the exact TVM build, target and model it was tuned on."""
    mismatches = [f"{key}: database={actual.get(key)!r}, requested={expected.get(key)!r}"
                  for key in ("tvm_version", "target") if actual.get(key) != expected.get(key)]
    mismatches += [f"{key}: database={actual[key]!r}, requested={expected[key]!r}"
                   for key in ("ir_sha256", "source_sha256", "tvm_git_commit")
                   if actual.get(key) and expected.get(key) and actual[key] != expected[key]]
    if mismatches:
        raise ValueError(f"incompatible MetaSchedule database ({basis}): " + "; ".join(mismatches))


def find_database(path: Path) -> Path:
    """Accepts a MetaSchedule work_dir or a compiled Model folder that contains tuning/."""
    for candidate in (path, path / "tuning"):
        if (candidate / WORKLOAD_FILE).is_file() and (candidate / RECORD_FILE).is_file():
            return candidate
    raise FileNotFoundError(f"MetaSchedule database not found under {path}; expected {WORKLOAD_FILE} and {RECORD_FILE}")


def open_database(out_dir: Path, database_path: Optional[str], identity: dict, weight_prepack: bool) -> TuningDatabase:
    """Create tuning/, validating and copying a previous database when supplied."""
    work_dir = out_dir / "tuning"
    work_dir.mkdir(parents=True, exist_ok=True)
    if not database_path:
        write_json(work_dir / MANIFEST_FILE, {**identity, "cpu_weight_prepack": weight_prepack})
        return TuningDatabase(work_dir, False, "new", weight_prepack)

    source = find_database(Path(database_path).resolve())
    actual, basis = database_identity(source)
    check_identity(actual, identity, basis)
    # Legacy records require the original preparation, without weight prepacking.
    weight_prepack = bool(actual.get("cpu_weight_prepack", False))
    if source.resolve() != work_dir.resolve():
        for name in (WORKLOAD_FILE, RECORD_FILE):
            shutil.copy2(source / name, work_dir / name)
    write_json(work_dir / MANIFEST_FILE, {**identity, "cpu_weight_prepack": weight_prepack, "reused_database_identity": basis})
    print(f"      reusing the MetaSchedule database from {source}")
    return TuningDatabase(work_dir, True, basis, weight_prepack)


def prepare(mod, target, pass_ctx, weight_prepack: bool):
    """Lower to TIR tasks, marking constant weights layout-free for prepacking."""
    passes = [relax.transform.DecomposeOpsForInference(), relax.transform.CanonicalizeBindings(), relax.get_pipeline("zero")]
    if weight_prepack:
        passes.append(relax.transform.AttachAttrLayoutFreeBuffers())
    with target, pass_ctx:
        return tvm.transform.Sequential(passes)(mod)


def apply_database(prepared, database: TuningDatabase, target, pass_ctx, allow_partial: bool):
    """Apply schedules and fold prepacked weights so model.so takes only real inputs."""
    with target, pass_ctx:
        mod = relax.transform.MetaScheduleApplyDatabase(work_dir=str(database.work_dir), enable_warning=allow_partial)(prepared)
        if database.weight_prepack:
            mod = relax.transform.SplitLayoutRewritePreproc()(mod)
            mod = relax.transform.LiftTransformParams()(mod)
            mod = relax.transform.FoldConstant()(mod)
    return mod


def first_round_budget(task_count: int, max_trials_per_task: int, trials_per_iter: int) -> int:
    """Budget to visit every task once, accounting for per-task candidate batches."""
    return task_count * min(trials_per_iter, max_trials_per_task)


def builder_and_runner(args: argparse.Namespace, workers: int):
    evaluator = ms.runner.EvaluatorConfig(
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
            evaluator_config=evaluator,
            alloc_repeat=args.tuning_alloc_repeat,
            max_workers=workers,
        )
    else:
        runner = ms.runner.LocalRunner(timeout_sec=args.tuning_runner_timeout_sec, evaluator_config=evaluator,
                                       alloc_repeat=args.tuning_alloc_repeat)
    return builder, runner


def tune(args, prepared, selected, all_tasks, target, database: TuningDatabase, pass_ctx, cpu_count: int) -> int:
    """Tune candidates and return the worker count."""
    budget = first_round_budget(len(selected), args.max_trials_per_task, args.tuning_trials_per_iter)
    print(f"      tune: {len(selected)}/{len(all_tasks)} tasks, trials={args.tuning_trials}, "
          f"max_per_task={args.max_trials_per_task}, per_iter={args.tuning_trials_per_iter}")
    print(f"      budget for one round over every task: {budget}; "
          f"for {args.max_trials_per_task} trials on every task: {len(selected) * args.max_trials_per_task}")
    if args.tuning_trials < budget:
        message = f"global tuning budget is below one complete task round ({args.tuning_trials} < {budget})"
        if not args.allow_partial_tuning:
            fail(message + "; set --allow-partial-tuning true for a smoke test")
        print(f"WARN: {message}", file=sys.stderr)

    workers = args.tuning_workers or args.target_num_cores or cpu_count
    builder, runner = builder_and_runner(args, workers)
    print(f"      runner={args.tuning_runner}, workers={workers}, seed={args.tuning_seed}")
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


def coverage(work_dir: Path, mod, target, op_names) -> tuple[int, int, list]:
    """Query with TVM apply-pass keys; return (records, usable records, function coverage)."""
    if not (work_dir / WORKLOAD_FILE).exists() or not (work_dir / RECORD_FILE).exists():
        return 0, 0, []
    database = ms.database.JSONDatabase(path_workload=str(work_dir / WORKLOAD_FILE),
                                        path_tuning_record=str(work_dir / RECORD_FILE), allow_missing=False)
    records = database.get_all_tuning_records()
    usable = sum(1 for record in records if record.run_secs and min(float(v) for v in record.run_secs) < FAILED_RUN_SECS)
    normalize = tvm.get_global_func("tvm.s_tir.meta_schedule.normalize_mod")
    functions = []
    for global_var, function in mod.functions_items():
        name = global_var.name_hint
        if not isinstance(function, PrimFunc) or (op_names and not any(op in name for op in op_names)):
            continue
        record = database.query_tuning_record(normalize(function), target, name)
        run_secs = [float(v) for v in record.run_secs] if record is not None and record.run_secs else []
        functions.append({
            "name": name,
            "covered": bool(run_secs) and min(run_secs) < FAILED_RUN_SECS,
            "best_run_ms": min(run_secs) * 1000 if run_secs else None,
        })
    return len(records), usable, functions


def run(args: argparse.Namespace, mod, target, effective_target: str, ir_dir: Path, out_dir: Path, pass_ctx, cpu_count: int):
    """Runs tune or apply. Returns the tuned module and the summary saved in metadata.json."""
    ir_metadata = read_json(ir_dir / "metadata.json") if (ir_dir / "metadata.json").is_file() else {}
    identity = {
        "schema_version": 1,
        "tvm_version": tvm.__version__,
        "target": effective_target,
        "ir_sha256": module_sha256(mod),
        "source_sha256": ir_metadata.get("source_sha256"),
        "tvm_git_commit": os.environ.get("TVM_GIT_COMMIT"),
    }
    database = open_database(out_dir, args.tuning_database, identity, weight_prepack=target.kind.name == "llvm")
    prepared = prepare(mod, target, pass_ctx, database.weight_prepack)
    tasks = extract_tasks(prepared, target, params={})
    selected = [t for t in tasks if not args.tuning_ops or any(op in t.task_name for op in args.tuning_ops)]
    if not selected:
        fail(f"no MetaSchedule task matches {args.tuning_ops}; available tasks: {[t.task_name for t in tasks]}")
    write_json(database.work_dir / TASKS_FILE, {
        "all": [{"name": t.task_name, "weight": int(t.weight)} for t in tasks],
        "selected": [t.task_name for t in selected],
        "filters": args.tuning_ops,
    })

    workers = None
    if args.tuning_mode == "tune":
        workers = tune(args, prepared, selected, tasks, target, database, pass_ctx, cpu_count)
    else:
        print(f"      apply: the database may cover up to {len(tasks)} tasks")

    attempts, records, functions = coverage(database.work_dir, prepared, target, args.tuning_ops)
    missing = [f["name"] for f in functions if not f["covered"]]
    write_json(database.work_dir / COVERAGE_FILE, {
        "selected_function_count": len(functions),
        "covered_function_count": len(functions) - len(missing),
        "missing_functions": missing,
        "functions": functions,
    })
    if records == 0:
        fail("MetaSchedule database contains no successful tuning records; inspect tuning/logs")
    if missing and not args.allow_partial_tuning:
        fail(f"MetaSchedule database does not cover every selected function: {missing}; "
             "set --allow-partial-tuning true only when partial optimization is intentional")

    tuned = apply_database(prepared, database, target, pass_ctx, args.allow_partial_tuning)
    summary = {
        "mode": args.tuning_mode,
        "database": "tuning",
        "database_reused": database.reused,
        "database_identity": database.identity_basis,
        "weight_prepack": database.weight_prepack,
        "task_count": len(tasks),
        "selected_task_count": len(selected),
        "selected_tasks": [t.task_name for t in selected],
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
        "covered_function_count": len(functions) - len(missing),
        "missing_functions": missing,
    }
    return tuned, summary
