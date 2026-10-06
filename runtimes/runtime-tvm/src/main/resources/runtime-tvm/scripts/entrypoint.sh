#!/usr/bin/env bash
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

# Entrypoint of the tvm+build and tvm+compile Jobs. CORE mounts the scripts in TVM_HOME_DIR
# and sets TVM_TASK_SCRIPT to the one to run; every script reads its options from the
# TVM_* variables of the Job.
set -euo pipefail

cd "${TVM_HOME_DIR:-/shared}"
mkdir -p "${TVM_INPUT_DIR:-input}" "${TVM_OUTPUT_DIR:-output}"

echo "==> ${TVM_TASK_KIND:-tvm task}: python ${TVM_TASK_SCRIPT:?TVM_TASK_SCRIPT is required}"
exec python "${TVM_TASK_SCRIPT}"
