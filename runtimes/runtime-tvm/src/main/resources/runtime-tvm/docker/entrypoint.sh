#!/usr/bin/env bash
# SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
#
# SPDX-License-Identifier: Apache-2.0

# Entrypoint of the tvm+build and tvm+compile Jobs. CORE describes the task with TVM_*
# env variables; this script turns them into command-line flags and runs task.py, which
# is the builder script of the source format or compiler.py.
set -euo pipefail

WORK_DIR="${TVM_HOME_DIR:-/shared}"
INPUT_DIR="${TVM_INPUT_DIR:-${WORK_DIR}/input}"
OUTPUT_DIR="${TVM_OUTPUT_DIR:-${WORK_DIR}/output}"
SCRIPT="${WORK_DIR}/task.py"

mkdir -p "${INPUT_DIR}" "${OUTPUT_DIR}"

echo "==> TVM runtime entrypoint"
echo "    task kind:   ${TVM_TASK_KIND:-?}"
echo "    work dir:    ${WORK_DIR}"
echo "    input dir:   ${INPUT_DIR}"
echo "    output dir:  ${OUTPUT_DIR}"
echo "    script:      ${SCRIPT}"

if [[ ! -f "${SCRIPT}" ]]; then
    echo "ERROR: task script not found at ${SCRIPT}" >&2
    exit 2
fi

declare -a ARGS=()

# Reads "ENV_VARIABLE --flag" lines from stdin and appends "--flag value" for every
# variable that is set, so unset options keep the Python script's defaults.
add_flags() {
    local name flag
    while read -r name flag; do
        if [[ -n "${name}" && -n "${!name:-}" ]]; then
            ARGS+=("${flag}" "${!name}")
        fi
    done
}

# The source model file. When the source was a folder, the expected name may not exist:
# then the single file with the same extension found in the folder is used.
source_model_file() {
    local file="${INPUT_DIR}/${TVM_INPUT_FILE:-model.onnx}"
    if [[ ! -f "${file}" ]]; then
        local candidates=()
        mapfile -t candidates < <(find "${INPUT_DIR}" -maxdepth 2 -type f -iname "*.${file##*.}")
        if [[ ${#candidates[@]} -eq 1 ]]; then
            file="${candidates[0]}"
        fi
    fi
    echo "${file}"
}

case "${TVM_TASK_KIND:-}" in
    tvm+build)
        ARGS=(--input "$(source_model_file)" --output "${OUTPUT_DIR}" --name "${TVM_FUNCTION_NAME:-model}")
        # Conversion options (the TFLite builder ignores the ONNX-only ones).
        add_flags <<'EOF'
TVM_OPSET_OVERRIDE        --opset
TVM_KEEP_PARAMS_IN_INPUT  --keep-params-in-input
TVM_SANITIZE_INPUT_NAMES  --sanitize-input-names
TVM_TARGET_OPSET          --target-opset
TVM_SIMPLIFY              --simplify
TVM_STRICT_SHAPE_INFER    --strict-shape-inference
TVM_DATA_PROP             --data-prop
EOF
        ;;
    tvm+compile)
        ARGS=(--ir-dir "${INPUT_DIR}" --output "${OUTPUT_DIR}" --target "${TVM_TARGET:?TVM_TARGET required for tvm+compile}")
        add_flags <<'EOF'
TVM_TARGET_NUM_CORES               --target-num-cores
TVM_OPT_LEVEL                      --opt-level
TVM_EXEC_MODE                      --exec-mode
TVM_RELAX_PIPELINE                 --relax-pipeline
TVM_TIR_PIPELINE                   --tir-pipeline
TVM_CROSS_CC                       --cross-cc
TVM_SYSTEM_LIB                     --system-lib
TVM_PARAMS_FILE                    --params-file
TVM_TAG                            --tag
TVM_BENCHMARK_RUNS                 --benchmark-runs
TVM_TUNING_MODE                    --tuning-mode
TVM_TUNING_TRIALS                  --tuning-trials
TVM_MAX_TRIALS_PER_TASK            --max-trials-per-task
TVM_TUNING_TRIALS_PER_ITER         --tuning-trials-per-iter
TVM_TUNING_OPS                     --tuning-ops
TVM_TUNING_DATABASE                --tuning-database
TVM_TUNING_RUNNER                  --tuning-runner
TVM_TUNING_WORKERS                 --tuning-workers
TVM_TUNING_SEED                    --tuning-seed
TVM_TUNING_NUMBER                  --tuning-number
TVM_TUNING_REPEAT                  --tuning-repeat
TVM_TUNING_MIN_REPEAT_MS           --tuning-min-repeat-ms
TVM_TUNING_ALLOC_REPEAT            --tuning-alloc-repeat
TVM_TUNING_ENABLE_CPU_CACHE_FLUSH  --tuning-enable-cpu-cache-flush
TVM_TUNING_BUILDER_TIMEOUT_SEC     --tuning-builder-timeout-sec
TVM_TUNING_RUNNER_TIMEOUT_SEC      --tuning-runner-timeout-sec
TVM_RPC_TRACKER_HOST               --rpc-tracker-host
TVM_RPC_TRACKER_PORT               --rpc-tracker-port
TVM_RPC_TRACKER_KEY                --rpc-tracker-key
TVM_RPC_SESSION_TIMEOUT_SEC        --rpc-session-timeout-sec
TVM_ALLOW_PARTIAL_TUNING           --allow-partial-tuning
EOF
        ;;
    *)
        echo "ERROR: unsupported TVM_TASK_KIND=${TVM_TASK_KIND:-} (expected tvm+build or tvm+compile)" >&2
        exit 3
        ;;
esac

echo "==> Running: python ${SCRIPT} ${ARGS[*]}"
# The script uploads the result and creates the Model entity itself (see _dh_publish.py).
python "${SCRIPT}" "${ARGS[@]}"

echo "==> Done"
