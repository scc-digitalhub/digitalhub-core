#!/usr/bin/env bash
# Entrypoint for tvm+build (ONNX -> Relax IR) and tvm+compile
# (Relax IR -> model.so). Dispatches on TVM_TASK_KIND to the injected task.py
# (builder_*.py or compiler.py) with the right CLI args; both publish the
# resulting Model via _dh_publish.py.
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

# S3 upload + Model entity creation are done by the Python script via the
# digitalhub SDK (see _dh_publish.py).

echo "==> Done"
