# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

"""LLVM target checks and standard TVM library export."""

import json
import shutil
import tempfile
from pathlib import Path

import tvm
from tvm import tirx
from tvm.support import cc


def compiler_options(text):
    options = json.loads(text)
    if not isinstance(options, list) or any(not isinstance(value, str) or "\0" in value for value in options):
        raise ValueError("cross_cc_options must be a JSON array of compiler arguments")
    return options


def platform_key(triple):
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


def local_execution_error(target):
    """Check the platform and the CPU instructions detectable by LLVM."""
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
    return None


def check_toolchain(target, compiler, options):
    compiler = compiler or cc.get_cc()
    if not compiler or not shutil.which(compiler):
        raise ValueError(f"C++ compiler not found: {compiler!r}; set cross_cc or use a suitable compile image")
    # Link an LLVM object before compiling the model, without executing foreign code.
    probe = tirx.PrimFunc([], tirx.Evaluate(0)).with_attr("global_symbol", "tvm_toolchain_probe")
    module = tvm.tirx.build(tvm.IRModule({"tvm_toolchain_probe": probe}), target=target)
    with tempfile.TemporaryDirectory(prefix="tvm-toolchain-") as folder:
        module.export_library(str(Path(folder) / "probe.so"), cc=compiler, options=options)
    return compiler
