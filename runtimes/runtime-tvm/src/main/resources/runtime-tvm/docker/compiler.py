#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Relax IR -> model.so. --target is forwarded as-is to tvm.target.Target; external libs (cuDNN/cuBLAS) go inline in the target string."""

import argparse
import hashlib
import json
import os
import shutil
import sys
from pathlib import Path
from typing import Any, Optional

import tvm
from tvm import relax
from tvm.s_tir import meta_schedule as ms
from tvm.s_tir.meta_schedule.relax_integration import extract_tasks, tune_relax
from tvm.tirx.function import PrimFunc


TUNING_WORKLOAD_FILE = "database_workload.json"
TUNING_RECORD_FILE = "database_tuning_record.json"
TUNING_MANIFEST_FILE = "manifest.json"
TUNING_COVERAGE_FILE = "coverage.json"


def parse_bool(value: str, default: bool) -> bool:
    if value is None:
        return default
    return value.lower() in ("1", "true", "yes", "y", "on")


def load_ir(ir_dir: Path):
    """Loads model.relax.json (round-trip safe); the .ir TVMScript dump is debug-only (can't reload with const params)."""
    json_path = ir_dir / "model.relax.json"
    if json_path.exists():
        return tvm.ir.load_json(json_path.read_text())
    print(f"ERROR: model.relax.json not found in {ir_dir}", file=sys.stderr)
    sys.exit(2)


def _load_param_file(path: Path) -> dict:
    """Loads a params.bin (save_param_dict format) into {name: Tensor}."""
    from tvm.runtime import load_param_dict
    flat = load_param_dict(path.read_bytes())
    # TVM 0.26: params must be a flat Map<str, Tensor>; the old grouped {fn: [v0,v1,...]} makes VMLink reject each value as an ffi.Array.
    return dict(flat.items())


def load_params(ir_dir: Path) -> Optional[dict]:
    """Loads params from params.bin if present."""
    params_path = ir_dir / "params.bin"
    if not params_path.exists():
        return None
    return _load_param_file(params_path)


def _bind_params(mod, flat):
    """Bind detached weights into the entry fn as constants. Keys are either the fn's param names or positional ("<fn>.<i>"); positional keys map onto the trailing params (after the real inputs) by order."""
    entry, func = "main", None
    for gv, f in mod.functions_items():
        if isinstance(f, relax.Function):
            if gv.name_hint == "main":
                entry, func = "main", f
                break
            if func is None:
                entry, func = gv.name_hint, f
    fparams = list(func.params)
    pnames = {v.name_hint for v in fparams}
    if set(flat).issubset(pnames):
        named = dict(flat)                      # keys already are parameter names
    else:                                       # positional keys -> map by order
        num_input = len(fparams) - len(flat)    # leading args are the real inputs
        if num_input < 0:
            raise ValueError(
                f"{len(flat)} saved params > {len(fparams)} function params")
        ordered = [flat[k] for k in sorted(flat, key=lambda k: int(k.rpartition(".")[2]))]
        named = {v.name_hint: t for v, t in zip(fparams[num_input:], ordered)}
    return entry, len(named), relax.transform.BindParams(entry, named)(mod)


def _resolve_target(target_text: str, target_num_cores: Optional[int]):
    """Resolve LLVM's unsupported ``mcpu=native`` to the concrete host CPU.

    TVM accepts the string but LLVM silently falls back to ``generic`` for the
    inferred x86 triple.  Recording and compiling the concrete CPU avoids a
    deceptively named, non-vectorized artifact.
    """
    requested = tvm.target.Target(target_text)
    resolve_native = (
        requested.kind.name == "llvm"
        and str(requested.attrs.get("mcpu", "")) == "native"
    )
    if not resolve_native and target_num_cores is None:
        return requested, target_text

    try:
        config = json.loads(target_text)
    except json.JSONDecodeError as error:
        if target_text.strip() == "llvm":
            config = {"kind": "llvm"}
        else:
            raise ValueError(
                "target_num_cores and mcpu=native require a JSON target or plain llvm"
            ) from error
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
            try:
                target_num_cores = len(os.sched_getaffinity(0))
            except AttributeError:
                target_num_cores = os.cpu_count() or 1
        print(
            f"      resolved mcpu=native -> mcpu={detected_cpu}, "
            f"mtriple={config.get('mtriple')}"
        )
    if target_num_cores is not None:
        config["num-cores"] = target_num_cores
        print(f"      target num-cores={target_num_cores}")
    resolved_text = json.dumps(config, separators=(",", ":"))
    return tvm.target.Target(resolved_text), resolved_text


def _parse_tuning_ops(value: Optional[str]) -> Optional[list[str]]:
    if not value:
        return None
    result = [item.strip() for item in value.split(",") if item.strip()]
    return result or None


def _module_sha256(mod) -> str:
    serialized = tvm.ir.save_json(mod).encode("utf-8")
    return hashlib.sha256(serialized).hexdigest()


def _available_cpu_count() -> int:
    try:
        return len(os.sched_getaffinity(0))
    except AttributeError:
        return os.cpu_count() or 1


def _load_json(path: Path) -> dict:
    try:
        value = json.loads(path.read_text())
    except (OSError, json.JSONDecodeError) as error:
        raise ValueError(f"invalid JSON file {path}: {error}") from error
    if not isinstance(value, dict):
        raise ValueError(f"expected a JSON object in {path}")
    return value


def _database_identity(source: Path) -> tuple[dict, str]:
    manifest_path = source / TUNING_MANIFEST_FILE
    if manifest_path.is_file():
        return _load_json(manifest_path), "manifest"

    for metadata_path in (source / "metadata.json", source.parent / "metadata.json"):
        if metadata_path.is_file():
            metadata = _load_json(metadata_path)
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
    required = ("tvm_version", "target")
    mismatches = [
        f"{key}: database={actual.get(key)!r}, requested={expected.get(key)!r}"
        for key in required
        if actual.get(key) != expected.get(key)
    ]
    for key in ("ir_sha256", "source_sha256", "tvm_git_commit"):
        if actual.get(key) and expected.get(key) and actual[key] != expected[key]:
            mismatches.append(
                f"{key}: database={actual[key]!r}, requested={expected[key]!r}"
            )
    if mismatches:
        raise ValueError(
            f"incompatible MetaSchedule database ({basis}): " + "; ".join(mismatches)
        )


def _find_tuning_database(path: Path) -> Path:
    """Accept a MetaSchedule work_dir or a published Model containing tuning/."""
    for candidate in (path, path / "tuning"):
        if (candidate / TUNING_WORKLOAD_FILE).is_file() and (
            candidate / TUNING_RECORD_FILE
        ).is_file():
            return candidate
    raise FileNotFoundError(
        f"MetaSchedule database not found under {path}; expected "
        f"{TUNING_WORKLOAD_FILE} and {TUNING_RECORD_FILE}"
    )


def _prepare_tuning_work_dir(
    out_dir: Path, database_path: Optional[str], expected_identity: dict
) -> tuple[Path, bool, str]:
    """Create the published work_dir and optionally warm-start from a prior Model."""
    work_dir = out_dir / "tuning"
    work_dir.mkdir(parents=True, exist_ok=True)
    if not database_path:
        (work_dir / TUNING_MANIFEST_FILE).write_text(
            json.dumps(expected_identity, indent=2)
        )
        return work_dir, False, "new"

    source = _find_tuning_database(Path(database_path).resolve())
    actual_identity, identity_basis = _database_identity(source)
    _validate_database_identity(actual_identity, expected_identity, identity_basis)
    if source.resolve() != work_dir.resolve():
        for name in (TUNING_WORKLOAD_FILE, TUNING_RECORD_FILE):
            shutil.copy2(source / name, work_dir / name)
    published_identity = dict(expected_identity)
    published_identity["reused_database_identity"] = identity_basis
    (work_dir / TUNING_MANIFEST_FILE).write_text(
        json.dumps(published_identity, indent=2)
    )
    print(f"      reusing MetaSchedule database from {source}")
    return work_dir, True, identity_basis


def _prepare_meta_schedule_ir(mod, target, pass_ctx):
    """Standard preparation used by TVM's static_shape_tuning pipeline."""
    with target, pass_ctx:
        return tvm.transform.Sequential(
            [
                relax.transform.DecomposeOpsForInference(),
                relax.transform.CanonicalizeBindings(),
                relax.get_pipeline("zero"),
            ]
        )(mod)


def _select_tasks(tasks, op_names: Optional[list[str]]):
    if not op_names:
        return tasks
    return [
        task
        for task in tasks
        if any(op_name in task.task_name for op_name in op_names)
    ]


def _database_coverage(work_dir: Path, mod, target, op_names):
    """Query records with the same public API and keys used by TVM's apply pass."""
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
    usable = sum(
        1
        for record in records
        if record.run_secs
        and min(float(value) for value in record.run_secs) < 1e10
    )
    normalize_mod = tvm.get_global_func("tvm.s_tir.meta_schedule.normalize_mod")
    coverage = []
    for global_var, function in mod.functions_items():
        if not isinstance(function, PrimFunc):
            continue
        name = global_var.name_hint
        if op_names and not any(op_name in name for op_name in op_names):
            continue
        query_mod = normalize_mod(function)
        record = database.query_tuning_record(query_mod, target, name)
        run_secs = (
            [float(value) for value in record.run_secs]
            if record is not None and record.run_secs
            else []
        )
        coverage.append(
            {
                "name": name,
                "covered": bool(run_secs) and min(run_secs) < 1e10,
                "best_run_ms": min(run_secs) * 1000 if run_secs else None,
            }
        )
    return len(records), usable, coverage


def main():
    ap = argparse.ArgumentParser(description="TVM Relax IR -> model.so")
    ap.add_argument("--ir-dir", required=True)
    ap.add_argument("--output", required=True)
    ap.add_argument("--target", required=True,
                    help='TVM target string: "llvm" or JSON dict form, e.g. '
                         '{"kind":"llvm","mcpu":"x86-64-v2"} (TVM 0.26 expects '
                         'the JSON form for specialized targets)')
    ap.add_argument("--opt-level", type=int, default=3)
    ap.add_argument("--cross-cc", default=None,
                    help="cross C++ compiler (e.g. aarch64-linux-gnu-g++, arm-linux-gnueabihf-g++)")
    ap.add_argument("--exec-mode", default="bytecode",
                    choices=["bytecode", "compiled"])
    ap.add_argument("--relax-pipeline", default="default")
    ap.add_argument("--tir-pipeline", default="default")
    ap.add_argument("--target-num-cores", type=int, default=None)
    ap.add_argument(
        "--tuning-mode",
        choices=["off", "tune", "apply"],
        default="off",
        help="standard Apache TVM MetaSchedule lifecycle",
    )
    ap.add_argument(
        "--tuning-trials",
        type=int,
        default=None,
        help="global MetaSchedule trial budget; required in tune mode",
    )
    ap.add_argument(
        "--max-trials-per-task",
        type=int,
        default=16,
        help="maximum MetaSchedule trials assigned to each tuning task",
    )
    ap.add_argument(
        "--tuning-ops",
        default=None,
        help="comma-separated task-name filters applied to extracted MetaSchedule tasks",
    )
    ap.add_argument(
        "--tuning-database",
        default=None,
        help="previous MetaSchedule work_dir or published Model directory",
    )
    ap.add_argument(
        "--tuning-runner",
        choices=["local", "rpc"],
        default="local",
        help="standard TVM runner used to measure tuning candidates",
    )
    ap.add_argument(
        "--tuning-workers",
        type=int,
        default=None,
        help="maximum LocalBuilder worker processes; defaults to the target core count",
    )
    ap.add_argument("--tuning-seed", type=int, default=0)
    ap.add_argument("--tuning-number", type=int, default=3)
    ap.add_argument("--tuning-repeat", type=int, default=1)
    ap.add_argument("--tuning-min-repeat-ms", type=int, default=100)
    ap.add_argument("--tuning-enable-cpu-cache-flush", default="false")
    ap.add_argument("--tuning-builder-timeout-sec", type=float, default=30.0)
    ap.add_argument("--tuning-runner-timeout-sec", type=float, default=30.0)
    ap.add_argument("--tuning-alloc-repeat", type=int, default=1)
    ap.add_argument("--rpc-tracker-host", default=None)
    ap.add_argument("--rpc-tracker-port", type=int, default=None)
    ap.add_argument("--rpc-tracker-key", default=None)
    ap.add_argument("--rpc-session-timeout-sec", type=int, default=60)
    ap.add_argument(
        "--allow-partial-tuning",
        default="false",
        help="allow publishing when the budget or database does not cover every selected task",
    )
    ap.add_argument("--system-lib", default="false")
    ap.add_argument("--params-file", default=None,
                    help="params.bin (if IR was built with keep_params_in_input=true)")
    ap.add_argument("--tag", default=None, help="recorded in metadata")
    args = ap.parse_args()

    ir_dir = Path(args.ir_dir).resolve()
    out_dir = Path(args.output)
    out_dir.mkdir(parents=True, exist_ok=True)
    system_lib = parse_bool(args.system_lib, False)
    allow_partial_tuning = parse_bool(args.allow_partial_tuning, False)

    print(f"[1/6] Loading IR from {ir_dir}")
    mod = load_ir(ir_dir)

    params = None
    if args.params_file:
        pf = Path(args.params_file)
        if not pf.exists():
            print(f"ERROR: --params-file {pf} not found", file=sys.stderr)
            sys.exit(3)
        try:
            params = _load_param_file(pf)
        except Exception as e:  # noqa: BLE001
            print(f"WARN: failed to load params: {e}", file=sys.stderr)
    else:
        # auto-detect a sibling params.bin
        params = load_params(ir_dir)
        if params:
            print("      auto-detected params.bin in ir-dir")

    if args.target_num_cores is not None and args.target_num_cores < 1:
        ap.error("--target-num-cores must be >= 1")
    if args.tuning_trials is not None and args.tuning_trials < 1:
        ap.error("--tuning-trials must be >= 1")
    if args.max_trials_per_task < 1:
        ap.error("--max-trials-per-task must be >= 1")
    if args.tuning_workers is not None and args.tuning_workers < 1:
        ap.error("--tuning-workers must be >= 1")
    if args.tuning_number < 1 or args.tuning_repeat < 1:
        ap.error("--tuning-number and --tuning-repeat must be >= 1")
    if args.tuning_min_repeat_ms < 0:
        ap.error("--tuning-min-repeat-ms must be >= 0")
    if args.tuning_builder_timeout_sec <= 0 or args.tuning_runner_timeout_sec <= 0:
        ap.error("tuning timeouts must be > 0")
    if args.tuning_alloc_repeat < 1:
        ap.error("--tuning-alloc-repeat must be >= 1")
    if args.rpc_session_timeout_sec < 1:
        ap.error("--rpc-session-timeout-sec must be >= 1")
    print(f"[2/6] Preparing (target='{args.target}', opt_level={args.opt_level}, "
          f"exec_mode={args.exec_mode}, relax_pipeline={args.relax_pipeline})")
    target, effective_target = _resolve_target(args.target, args.target_num_cores)
    if args.relax_pipeline == "static_shape_tuning":
        ap.error("use --tuning-mode tune; relax-pipeline remains the final build pipeline")
    if args.tuning_mode == "tune" and args.tuning_trials is None:
        ap.error("tuning_mode=tune requires --tuning-trials")
    if args.tuning_mode == "apply" and not args.tuning_database:
        ap.error("tuning_mode=apply requires --tuning-database")
    if args.tuning_mode == "tune" and target.kind.name != "llvm":
        ap.error("local MetaSchedule tuning currently supports LLVM/CPU targets only")
    if (
        args.tuning_mode == "tune"
        and args.cross_cc
        and args.tuning_runner != "rpc"
    ):
        ap.error(
            "cannot measure a cross-compiled target locally; tune on the target device "
            "with --tuning-runner rpc or compile with tuning_mode=apply"
        )
    if args.tuning_mode == "tune" and args.tuning_runner == "rpc":
        missing_rpc = [
            name
            for name, value in (
                ("--rpc-tracker-host", args.rpc_tracker_host),
                ("--rpc-tracker-port", args.rpc_tracker_port),
                ("--rpc-tracker-key", args.rpc_tracker_key),
            )
            if value is None
        ]
        if missing_rpc:
            ap.error(
                "tuning_runner=rpc requires " + ", ".join(missing_rpc)
            )
    if args.tuning_mode == "tune" and target.attrs.get("num-cores") is not None:
        measured_threads = str(target.attrs["num-cores"])
        os.environ.setdefault("TVM_NUM_THREADS", measured_threads)
        print(f"      tuning runtime threads={os.environ['TVM_NUM_THREADS']}")

    build_kwargs: Any = {
        "target": target,
        "relax_pipeline": args.relax_pipeline,
        "tir_pipeline": args.tir_pipeline,
        "exec_mode": args.exec_mode,
    }
    if system_lib:
        build_kwargs["system_lib"] = True

    # relax.build does NOT fold params (TVM 0.26); the explicit BindParams below replaces the weight Vars with constants so the served model takes only the real inputs.
    if params:
        entry, n_bound, mod = _bind_params(mod, params)
        print(f"      bound {n_bound} params into '{entry}()'")

    pass_ctx = tvm.transform.PassContext(opt_level=args.opt_level)
    tuning_info = None
    if args.tuning_mode != "off":
        ir_metadata_path = ir_dir / "metadata.json"
        ir_metadata = _load_json(ir_metadata_path) if ir_metadata_path.is_file() else {}
        database_identity = {
            "schema_version": 1,
            "tvm_version": tvm.__version__,
            "target": effective_target,
            "ir_sha256": _module_sha256(mod),
            "source_sha256": ir_metadata.get("source_sha256"),
            "tvm_git_commit": os.environ.get("TVM_GIT_COMMIT"),
        }
        work_dir, database_reused, database_identity_basis = _prepare_tuning_work_dir(
            out_dir, args.tuning_database, database_identity
        )
        prepared_mod = _prepare_meta_schedule_ir(mod, target, pass_ctx)
        tasks = extract_tasks(prepared_mod, target, params={})
        op_names = _parse_tuning_ops(args.tuning_ops)
        selected_tasks = _select_tasks(tasks, op_names)
        if not selected_tasks:
            ap.error(
                f"no MetaSchedule task matches {op_names}; available tasks: "
                f"{[task.task_name for task in tasks]}"
            )

        task_manifest = {
            "all": [
                {"name": task.task_name, "weight": int(task.weight)}
                for task in tasks
            ],
            "selected": [task.task_name for task in selected_tasks],
            "filters": op_names,
        }
        (work_dir / "tasks.json").write_text(json.dumps(task_manifest, indent=2))

        if args.tuning_mode == "tune":
            recommended_iteration = len(selected_tasks) * args.max_trials_per_task
            print(
                f"      MetaSchedule tune: {len(selected_tasks)}/{len(tasks)} tasks, "
                f"trials={args.tuning_trials}, max_per_task={args.max_trials_per_task}"
            )
            if args.tuning_trials < recommended_iteration:
                message = (
                    "global tuning budget is below one complete task iteration "
                    f"({args.tuning_trials} < {recommended_iteration})"
                )
                if not allow_partial_tuning:
                    ap.error(message + "; set --allow-partial-tuning true for a smoke test")
                print(f"WARN: {message}", file=sys.stderr)
            tuning_workers = (
                args.tuning_workers
                or args.target_num_cores
                or _available_cpu_count()
            )
            evaluator_config = ms.runner.EvaluatorConfig(
                number=args.tuning_number,
                repeat=args.tuning_repeat,
                min_repeat_ms=args.tuning_min_repeat_ms,
                enable_cpu_cache_flush=parse_bool(
                    args.tuning_enable_cpu_cache_flush, False
                ),
            )
            builder = ms.builder.LocalBuilder(
                max_workers=tuning_workers,
                timeout_sec=args.tuning_builder_timeout_sec,
            )
            if args.tuning_runner == "rpc":
                rpc_config = ms.runner.RPCConfig(
                    tracker_host=args.rpc_tracker_host,
                    tracker_port=args.rpc_tracker_port,
                    tracker_key=args.rpc_tracker_key,
                    session_timeout_sec=args.rpc_session_timeout_sec,
                )
                runner = ms.runner.RPCRunner(
                    rpc_config=rpc_config,
                    evaluator_config=evaluator_config,
                    alloc_repeat=args.tuning_alloc_repeat,
                    max_workers=tuning_workers,
                )
            else:
                runner = ms.runner.LocalRunner(
                    timeout_sec=args.tuning_runner_timeout_sec,
                    evaluator_config=evaluator_config,
                    alloc_repeat=args.tuning_alloc_repeat,
                )
            print(
                f"      runner={args.tuning_runner}, builder_workers={tuning_workers}, "
                f"seed={args.tuning_seed}"
            )
            with target, pass_ctx:
                tune_relax(
                    mod=prepared_mod,
                    params={},
                    target=target,
                    work_dir=str(work_dir),
                    max_trials_global=args.tuning_trials,
                    max_trials_per_task=args.max_trials_per_task,
                    op_names=op_names,
                    builder=builder,
                    runner=runner,
                    seed=args.tuning_seed,
                )
        else:
            tuning_workers = None
            print(
                f"      MetaSchedule apply: database covers up to {len(tasks)} extracted tasks"
            )

        tuning_attempts, tuning_records, coverage = _database_coverage(
            work_dir, prepared_mod, target, op_names
        )
        missing_tasks = [item["name"] for item in coverage if not item["covered"]]
        coverage_report = {
            "selected_function_count": len(coverage),
            "covered_function_count": len(coverage) - len(missing_tasks),
            "missing_functions": missing_tasks,
            "functions": coverage,
        }
        (work_dir / TUNING_COVERAGE_FILE).write_text(
            json.dumps(coverage_report, indent=2)
        )
        if tuning_records == 0:
            ap.error(
                "MetaSchedule database contains no successful tuning records; "
                "inspect tuning/logs"
            )
        if missing_tasks and not allow_partial_tuning:
            ap.error(
                "MetaSchedule database does not cover every selected function: "
                f"{missing_tasks}; set --allow-partial-tuning true only when partial "
                "optimization is intentional"
            )

        # Standard TVM pass: applies matching records and leaves uncovered
        # functions unchanged only when partial tuning was explicitly allowed.
        with target, pass_ctx:
            mod = relax.transform.MetaScheduleApplyDatabase(
                work_dir=str(work_dir), enable_warning=allow_partial_tuning
            )(prepared_mod)

        tuning_info = {
            "mode": args.tuning_mode,
            "database": "tuning",
            "database_reused": database_reused,
            "database_identity": database_identity_basis,
            "task_count": len(tasks),
            "selected_task_count": len(selected_tasks),
            "selected_tasks": [task.task_name for task in selected_tasks],
            "task_filters": op_names,
            "trials": args.tuning_trials if args.tuning_mode == "tune" else 0,
            "max_trials_per_task": args.max_trials_per_task,
            "runner": args.tuning_runner,
            "workers": tuning_workers,
            "seed": args.tuning_seed,
            "alloc_repeat": args.tuning_alloc_repeat,
            "allow_partial": allow_partial_tuning,
            "evaluator": {
                "number": args.tuning_number,
                "repeat": args.tuning_repeat,
                "min_repeat_ms": args.tuning_min_repeat_ms,
                "enable_cpu_cache_flush": parse_bool(
                    args.tuning_enable_cpu_cache_flush, False
                ),
            },
            "attempt_count": tuning_attempts,
            "record_count": tuning_records,
            "covered_function_count": coverage_report["covered_function_count"],
            "missing_functions": missing_tasks,
        }

    print("[3/6] Running the standard Relax/TIR build pipeline")
    with pass_ctx:
        ex = relax.build(mod, **build_kwargs)

    print("[4/6] Exporting library -> model.so")
    so_path = out_dir / "model.so"
    export_kwargs: dict = {}
    if args.cross_cc:
        export_kwargs["cc"] = args.cross_cc
        print(f"      cross-cc: {args.cross_cc}")
    ex.export_library(str(so_path), **export_kwargs)
    size_mb = so_path.stat().st_size / 1e6
    print(f"      written: {so_path} ({size_mb:.1f} MB)")

    print("[5/6] Updating metadata.json")
    meta_in = ir_dir / "metadata.json"
    meta_out = out_dir / "metadata.json"
    if meta_in.exists():
        meta = json.loads(meta_in.read_text())
    else:
        meta = {"entry": "main"}
    meta["target"] = effective_target
    meta["tvm_version"] = tvm.__version__
    tvm_git_commit = os.environ.get("TVM_GIT_COMMIT")
    if tvm_git_commit:
        meta["tvm_git_commit"] = tvm_git_commit
    if effective_target != args.target:
        meta["target_requested"] = args.target
    meta["opt_level"] = args.opt_level
    meta["exec_mode"] = args.exec_mode
    meta["relax_pipeline"] = args.relax_pipeline
    meta["tir_pipeline"] = args.tir_pipeline
    if tuning_info is not None:
        meta["meta_schedule"] = tuning_info
    if system_lib:
        meta["system_lib"] = True
    if args.tag:
        meta["tag"] = args.tag
    meta_out.write_text(json.dumps(meta, indent=2))
    print(f"      written: {meta_out}")

    print("[6/6] Publishing Model entity via digitalhub SDK")
    sys.path.insert(0, str(Path(__file__).parent))
    from _dh_publish import publish_model_and_register_output

    func_name = os.environ.get("TVM_FUNCTION_NAME", "model")
    # compiled Model name suffix: "so" by default, overridable via task `tag` -> "<name>-<tag>".
    tag = args.tag or "so"
    source_ir = os.environ.get("TVM_SOURCE_IR_KEY") or None

    publish_model_and_register_output(
        out_dir=out_dir,
        name=f"{func_name}-{tag}",
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
        relationship_source=source_ir,
    )

    print("Done")


if __name__ == "__main__":
    main()
