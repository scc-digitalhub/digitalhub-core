#!/usr/bin/env bash
# Entrypoint for tvm+build and tvm+compile; dispatches on TVM_TASK_KIND to task.py with the right CLI args.
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
case "${TVM_TASK_KIND:-}" in
    tvm+build)
        ARGS=(
            --input "${INPUT_DIR}/${TVM_INPUT_FILE:-model.onnx}"
            --output "${OUTPUT_DIR}"
            --name "${TVM_FUNCTION_NAME:-model}"
        )
        # from_onnx parameters
        [[ -n "${TVM_OPSET_OVERRIDE:-}" ]]        && ARGS+=(--opset "${TVM_OPSET_OVERRIDE}")
        [[ -n "${TVM_KEEP_PARAMS_IN_INPUT:-}" ]]  && ARGS+=(--keep-params-in-input "${TVM_KEEP_PARAMS_IN_INPUT}")
        [[ -n "${TVM_SANITIZE_INPUT_NAMES:-}" ]]  && ARGS+=(--sanitize-input-names "${TVM_SANITIZE_INPUT_NAMES}")
        # ONNX preprocessing
        [[ -n "${TVM_TARGET_OPSET:-}" ]]          && ARGS+=(--target-opset "${TVM_TARGET_OPSET}")
        [[ -n "${TVM_SIMPLIFY:-}" ]]              && ARGS+=(--simplify "${TVM_SIMPLIFY}")
        [[ -n "${TVM_STRICT_SHAPE_INFER:-}" ]]    && ARGS+=(--strict-shape-inference "${TVM_STRICT_SHAPE_INFER}")
        [[ -n "${TVM_DATA_PROP:-}" ]]             && ARGS+=(--data-prop "${TVM_DATA_PROP}")
        ;;
    tvm+compile)
        ARGS=(
            --ir-dir "${INPUT_DIR}"
            --output "${OUTPUT_DIR}"
            --target "${TVM_TARGET:?TVM_TARGET required for tvm+compile}"
        )
        [[ -n "${TVM_OPT_LEVEL:-}" ]]      && ARGS+=(--opt-level "${TVM_OPT_LEVEL}")
        [[ -n "${TVM_EXEC_MODE:-}" ]]      && ARGS+=(--exec-mode "${TVM_EXEC_MODE}")
        [[ -n "${TVM_RELAX_PIPELINE:-}" ]] && ARGS+=(--relax-pipeline "${TVM_RELAX_PIPELINE}")
        [[ -n "${TVM_TIR_PIPELINE:-}" ]]   && ARGS+=(--tir-pipeline "${TVM_TIR_PIPELINE}")
        [[ -n "${TVM_TARGET_NUM_CORES:-}" ]] && ARGS+=(--target-num-cores "${TVM_TARGET_NUM_CORES}")
        [[ -n "${TVM_TUNING_MODE:-}" ]]    && ARGS+=(--tuning-mode "${TVM_TUNING_MODE}")
        [[ -n "${TVM_TUNING_TRIALS:-}" ]]  && ARGS+=(--tuning-trials "${TVM_TUNING_TRIALS}")
        [[ -n "${TVM_MAX_TRIALS_PER_TASK:-}" ]] && ARGS+=(--max-trials-per-task "${TVM_MAX_TRIALS_PER_TASK}")
        [[ -n "${TVM_TUNING_OPS:-}" ]]     && ARGS+=(--tuning-ops "${TVM_TUNING_OPS}")
        [[ -n "${TVM_TUNING_DATABASE:-}" ]] && ARGS+=(--tuning-database "${TVM_TUNING_DATABASE}")
        [[ -n "${TVM_TUNING_RUNNER:-}" ]]  && ARGS+=(--tuning-runner "${TVM_TUNING_RUNNER}")
        [[ -n "${TVM_TUNING_WORKERS:-}" ]] && ARGS+=(--tuning-workers "${TVM_TUNING_WORKERS}")
        [[ -n "${TVM_TUNING_SEED:-}" ]]    && ARGS+=(--tuning-seed "${TVM_TUNING_SEED}")
        [[ -n "${TVM_TUNING_NUMBER:-}" ]]  && ARGS+=(--tuning-number "${TVM_TUNING_NUMBER}")
        [[ -n "${TVM_TUNING_REPEAT:-}" ]]  && ARGS+=(--tuning-repeat "${TVM_TUNING_REPEAT}")
        [[ -n "${TVM_TUNING_MIN_REPEAT_MS:-}" ]] && ARGS+=(--tuning-min-repeat-ms "${TVM_TUNING_MIN_REPEAT_MS}")
        [[ -n "${TVM_TUNING_ALLOC_REPEAT:-}" ]] && ARGS+=(--tuning-alloc-repeat "${TVM_TUNING_ALLOC_REPEAT}")
        [[ -n "${TVM_TUNING_ENABLE_CPU_CACHE_FLUSH:-}" ]] && ARGS+=(--tuning-enable-cpu-cache-flush "${TVM_TUNING_ENABLE_CPU_CACHE_FLUSH}")
        [[ -n "${TVM_TUNING_BUILDER_TIMEOUT_SEC:-}" ]] && ARGS+=(--tuning-builder-timeout-sec "${TVM_TUNING_BUILDER_TIMEOUT_SEC}")
        [[ -n "${TVM_TUNING_RUNNER_TIMEOUT_SEC:-}" ]] && ARGS+=(--tuning-runner-timeout-sec "${TVM_TUNING_RUNNER_TIMEOUT_SEC}")
        [[ -n "${TVM_RPC_TRACKER_HOST:-}" ]] && ARGS+=(--rpc-tracker-host "${TVM_RPC_TRACKER_HOST}")
        [[ -n "${TVM_RPC_TRACKER_PORT:-}" ]] && ARGS+=(--rpc-tracker-port "${TVM_RPC_TRACKER_PORT}")
        [[ -n "${TVM_RPC_TRACKER_KEY:-}" ]] && ARGS+=(--rpc-tracker-key "${TVM_RPC_TRACKER_KEY}")
        [[ -n "${TVM_RPC_SESSION_TIMEOUT_SEC:-}" ]] && ARGS+=(--rpc-session-timeout-sec "${TVM_RPC_SESSION_TIMEOUT_SEC}")
        [[ -n "${TVM_ALLOW_PARTIAL_TUNING:-}" ]] && ARGS+=(--allow-partial-tuning "${TVM_ALLOW_PARTIAL_TUNING}")
        [[ -n "${TVM_CROSS_CC:-}" ]]       && ARGS+=(--cross-cc "${TVM_CROSS_CC}")
        [[ -n "${TVM_SYSTEM_LIB:-}" ]]     && ARGS+=(--system-lib "${TVM_SYSTEM_LIB}")
        [[ -n "${TVM_PARAMS_FILE:-}" ]]    && ARGS+=(--params-file "${TVM_PARAMS_FILE}")
        [[ -n "${TVM_TAG:-}" ]]            && ARGS+=(--tag "${TVM_TAG}")
        ;;
    *)
        echo "ERROR: unsupported TVM_TASK_KIND=${TVM_TASK_KIND:-} (expected tvm+build or tvm+compile)" >&2
        exit 3
        ;;
esac

echo "==> Running: python ${SCRIPT} ${ARGS[*]}"
python "${SCRIPT}" "${ARGS[@]}"

# S3 upload + Model entity creation happen inside the Python script (_dh_publish.py).

echo "==> Done"
