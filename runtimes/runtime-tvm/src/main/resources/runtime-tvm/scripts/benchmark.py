#!/usr/bin/env python3
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""Times a few inferences of the compiled model.so on random inputs.

compile_model.py runs this file as a separate process: a model built for CPU features
this machine lacks can crash, and the crash must not stop the compile.

    python benchmark.py <model.so> <entry> '<inputs as JSON>' <runs>
"""

from __future__ import annotations

import json
import os
import subprocess
import sys
import time
from pathlib import Path
from typing import Optional

TIMEOUT_SEC = 900


def benchmark_library(so_path: Path, metadata: dict, runs: int, cross_cc: Optional[str], system_lib: bool) -> Optional[dict]:
    """The timings of `runs` inferences, the reason they were skipped, or None when off."""
    if runs <= 0:
        return None
    if cross_cc:
        return {"skipped": f"cross-compiled with {cross_cc}"}
    if system_lib:
        return {"skipped": "a system-lib module cannot be loaded as a shared library"}
    if not metadata.get("inputs"):
        return {"skipped": "metadata.json declares no inputs"}

    command = [sys.executable, str(Path(__file__).resolve()), str(so_path), metadata.get("entry", "main"),
               json.dumps(metadata["inputs"]), str(runs)]
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
    """A random tensor shaped like the metadata input; small values fit every integer
    type, quantized inputs included."""
    import numpy as np

    dtype = np.dtype(spec.get("dtype", "float32"))
    shape = [dim if dim > 0 else 1 for dim in spec["shape"]]
    if np.issubdtype(dtype, np.floating):
        return rng.standard_normal(shape).astype(dtype)
    if dtype == np.bool_:
        return rng.integers(0, 2, size=shape).astype(dtype)
    low = 0 if np.issubdtype(dtype, np.unsignedinteger) else -8
    return rng.integers(low, 8, size=shape).astype(dtype)


def main(so_path: str, entry: str, inputs_json: str, runs_text: str) -> None:
    """Loads model.so in a Relax VM and prints the timings as one JSON line."""
    import numpy as np
    import tvm
    from tvm import relax

    runs = int(runs_text)
    warmup = min(5, runs)
    device = tvm.cpu()
    vm = relax.VirtualMachine(tvm.runtime.load_module(so_path), device)
    rng = np.random.default_rng(0)
    tensors = [tvm.runtime.tensor(random_input(spec, rng), device) for spec in json.loads(inputs_json)]

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
    main(*sys.argv[1:5])
