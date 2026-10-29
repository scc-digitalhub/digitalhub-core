#!/usr/bin/env bash
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

cd "${TVM_HOME_DIR:-/shared}"
mkdir -p "${TVM_INPUT_DIR:-input}" "${TVM_OUTPUT_DIR:-output}"

echo "==> ${TVM_TASK_KIND:-tvm task}: python ${TVM_TASK_SCRIPT:?TVM_TASK_SCRIPT is required}"
exec python "${TVM_TASK_SCRIPT}"
