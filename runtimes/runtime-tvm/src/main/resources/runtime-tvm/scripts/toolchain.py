# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""CPU host and CUDA runtime checks for standard TVM library export."""

from __future__ import annotations

import json
import shutil
import tempfile
from pathlib import Path

import tvm
from tvm import tirx
from tvm.support import cc

from common import host_target

CUDA_LIMITS = ("max_shared_memory_per_block", "max_threads_per_block", "thread_warp_size")


def compiler_options(text: str) -> list[str]:
    options = json.loads(text)
    if not isinstance(options, list) or any(not isinstance(value, str) or "\0" in value for value in options):
        raise ValueError("cross_cc_options must be a JSON array of compiler arguments")
    return options


def platform_key(triple: str) -> tuple[str, str]:
    arch = triple.split("-", 1)[0].lower()
    if arch in ("amd64", "x86_64"):
        arch = "x86_64"
    elif arch in ("arm64", "aarch64"):
        arch = "aarch64"
    elif arch == "arm" or arch.startswith(("armv", "thumb")):
        arch = "arm"
    if "-linux-" not in triple:
        raise ValueError(f"only Linux CPU targets are supported, got {triple!r}")
    return arch, triple.split("-linux-", 1)[1]


def local_execution_error(target: tvm.target.Target) -> str | None:
    """Check host CPU compatibility and, for CUDA, local device availability.

    CUDA's driver remains the authority on loading the compiled GPU architecture.
    """
    device_target = target
    target = host_target(target)
    host_triple = tvm.target.codegen.llvm_get_system_triple()
    triple = str(target.attrs["mtriple"])
    if platform_key(triple) != platform_key(host_triple):
        return f"target {triple} cannot run on compile host {host_triple}"
    required = tvm.target.codegen.llvm_get_cpu_features(target)
    host_features = tvm.get_global_func("tvm.codegen.llvm.GetHostCPUFeatures")()
    # LLVM target features also contain scheduling flags, not just CPU instructions.
    missing = {name for name in required if name in host_features and not int(host_features[name])}
    if missing:
        return "compile host lacks target CPU features: " + ", ".join(sorted(missing))
    if device_target.kind.name == "cuda":
        try:
            if not tvm.cuda(0).exist:
                return "no local CUDA device is available; CUDA execution requires a GPU and its driver"
        except Exception as error:  # noqa: BLE001
            return f"local CUDA device is unavailable: {error}"
    return None


def check_cuda_runtime() -> None:
    """Codegen alone can emit a non-runnable source fallback on CPU-only TVM."""
    if tvm.get_global_func("ffi.Module.create.cuda", allow_missing=True) is None:
        raise ValueError("CUDA compilation requires a CUDA-enabled TVM toolkit with its CUDA runtime factory; "
                         "target.build.cuda alone only provides source fallback codegen. For offline compilation "
                         "without a GPU driver, explicitly set the task env "
                         "LD_LIBRARY_PATH=/opt/tvm/cuda-driver-stubs:/opt/tvm/lib:/usr/local/cuda/lib64 before Python starts")


def check_cuda_modules(module) -> None:
    """Reject source fallback modules anywhere in a standard TVM import tree."""
    pending = [module]
    visited = set()
    while pending:
        current = pending.pop()
        if current in visited:
            continue
        visited.add(current)
        if current.kind == "cuda" and not current.is_runnable():
            raise ValueError("CUDA build produced a non-runnable source fallback; use a CUDA-enabled TVM toolkit "
                             "with nvcc and disable TVM_COMPILE_FORCE_FALLBACK before publishing model.so")
        pending.extend(current.imports)


def check_cuda_tuning(target: tvm.target.Target, target_text: str, local: bool) -> None:
    """Require explicit real-device limits instead of TVM's generic GPU defaults."""
    config = json.loads(target_text)
    missing = [name for name in CUDA_LIMITS if type(config.get(name)) is not int or config[name] <= 0]
    if missing:
        raise ValueError("CUDA tuning requires explicit positive integer target limits from the device profile: "
                         + ", ".join(missing)
                         + "; obtain them with tvm.target.Target.from_device(tvm.cuda(0)) on the measurement device")
    if not local:
        return
    profile = tvm.target.Target.from_device(tvm.cuda(0))
    incompatible = []
    for name in CUDA_LIMITS:
        requested, available = int(target.attrs[name]), int(profile.attrs[name])
        if (name == "thread_warp_size" and requested != available) or requested > available:
            incompatible.append(f"{name}={requested} (device={available})")
    if incompatible:
        raise ValueError("CUDA tuning limits are incompatible with the local device: " + ", ".join(incompatible))


def check_toolchain(target: tvm.target.Target, compiler: str | None, options: list[str]) -> str:
    if target.kind.name == "cuda":
        check_cuda_runtime()
    target = host_target(target)
    compiler = compiler or cc.get_cc()
    if not compiler or not shutil.which(compiler):
        raise ValueError(f"C++ compiler not found: {compiler!r}; set cross_cc or use a suitable compile image")
    # Link an LLVM object before compiling the model, without executing foreign code.
    probe = tirx.PrimFunc([], tirx.Evaluate(0)).with_attr("global_symbol", "tvm_toolchain_probe")
    module = tvm.tirx.build(tvm.IRModule({"tvm_toolchain_probe": probe}), target=target)
    with tempfile.TemporaryDirectory(prefix="tvm-toolchain-") as folder:
        module.export_library(str(Path(folder) / "probe.so"), cc=compiler, options=options)
    return compiler
