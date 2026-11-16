#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Benchmark model.so in a subprocess so execution errors cannot abort compilation.

    python benchmark.py <model.so> <entry> '<inputs as JSON>' <runs> [cpu|cuda]
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
from pathlib import Path

TIMEOUT_SEC = 900


def benchmark_library(so_path: Path, metadata: dict, runs: int, execution_error: str | None,
                      system_lib: bool, target=None) -> dict | None:
    """The timings of `runs` inferences, the reason they were skipped, or None when off."""
    if runs <= 0:
        return None
    if execution_error:
        return {"skipped": execution_error}
    if system_lib:
        return {"skipped": "a system-lib module cannot be loaded as a shared library"}
    if not metadata.get("inputs"):
        return {"skipped": "metadata.json declares no inputs"}

    command = [sys.executable, str(Path(__file__).resolve()), str(so_path), metadata.get("entry", "main"),
               json.dumps(metadata["inputs"]), str(runs), "cuda" if target is not None and target.kind.name == "cuda" else "cpu"]
    try:
        done = subprocess.run(command, capture_output=True, text=True, timeout=TIMEOUT_SEC)
    except subprocess.TimeoutExpired:
        return {"skipped": f"no result within {TIMEOUT_SEC} s"}
    if done.returncode < 0:
        return {"skipped": f"model.so crashed with signal {-done.returncode}; "
                           "the target may need CPU features this machine does not have"}
    if done.returncode != 0:
        lines = done.stderr.strip().splitlines()
        return {"skipped": (lines[-1] if lines else f"exit code {done.returncode}")[:300]}
    return json.loads(done.stdout.strip().splitlines()[-1])


def random_input(spec: dict, rng):
    """Use small integer values to fit quantized and unquantized input dtypes."""
    import numpy as np

    dtype = np.dtype(spec.get("dtype", "float32"))
    shape = [dim if dim > 0 else 1 for dim in spec["shape"]]
    if np.issubdtype(dtype, np.floating):
        return rng.standard_normal(shape).astype(dtype)
    if dtype == np.bool_:
        return rng.integers(0, 2, size=shape).astype(dtype)
    low = 0 if np.issubdtype(dtype, np.unsignedinteger) else -8
    return rng.integers(low, 8, size=shape).astype(dtype)


def timing_summary(times: list[float]) -> dict:
    """Summarize milliseconds using the same percentiles as the CPU benchmark."""
    times = sorted(times)
    count = len(times)

    def percentile(q: float) -> float:
        return round(times[min(count - 1, int(round(q * (count - 1))))], 4)

    return {
        "mean_ms": round(sum(times) / count, 4),
        "p50_ms": percentile(0.5),
        "p90_ms": percentile(0.9),
        "min_ms": round(times[0], 4),
    }


def benchmark_cuda(vm, device, entry: str, host_inputs: list, runs: int, warmup: int) -> dict:
    """Measure resident inputs with TVM and transfers with synchronized wall time."""
    import tvm

    tensors = [tvm.runtime.tensor(value, device) for value in host_inputs]
    device.sync()
    run = vm[entry]
    for _ in range(warmup):
        run(*tensors)
    device.sync()
    # The stateful API also supports models returning tuples of tensors.
    vm.set_input(entry, *tensors)
    result = vm.time_evaluator("invoke_stateful", device, number=1, repeat=runs, min_repeat_ms=0)(entry)
    device.sync()

    transfer_times = []
    for _ in range(runs):
        device.sync()
        start = time.perf_counter()
        copied = [tvm.runtime.tensor(value, device) for value in host_inputs]
        run(*copied)
        device.sync()
        transfer_times.append((time.perf_counter() - start) * 1000)
    return {
        "runs": runs,
        "warmup": warmup + 1,  # time_evaluator performs one additional warmup.
        "threads": os.environ.get("TVM_NUM_THREADS", "default"),
        "device": f"cuda:{device.index}",
        "input_resident": {
            "measurement": "cuda_time_evaluator",
            **timing_summary([value * 1000 for value in result.results]),
        },
        "input_transfer_inclusive": {
            "measurement": "synchronized_wall_clock",
            "includes": "host_to_device_inputs_and_inference",
            **timing_summary(transfer_times),
        },
    }


def main(so_path: str, entry: str, inputs_json: str, runs_text: str, device_kind: str = "cpu") -> None:
    import numpy as np
    import tvm
    from tvm import relax

    runs = int(runs_text)
    warmup = min(5, runs)
    if device_kind not in ("cpu", "cuda"):
        raise ValueError(f"unsupported benchmark device {device_kind!r}")
    device = tvm.cuda(0) if device_kind == "cuda" else tvm.cpu()
    if device_kind == "cuda" and not device.exist:
        raise ValueError("no local CUDA device is available")
    vm = relax.VirtualMachine(tvm.runtime.load_module(so_path), device)
    rng = np.random.default_rng(0)
    host_inputs = [random_input(spec, rng) for spec in json.loads(inputs_json)]
    if device_kind == "cuda":
        print(json.dumps(benchmark_cuda(vm, device, entry, host_inputs, runs, warmup)))
        return
    tensors = [tvm.runtime.tensor(value, device) for value in host_inputs]

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


if __name__ == "__main__":
    main(*sys.argv[1:6])
