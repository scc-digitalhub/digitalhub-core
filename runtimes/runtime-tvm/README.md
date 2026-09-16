# runtime-tvm

DigitalHub CORE runtime that integrates **[Apache TVM](https://tvm.apache.org/)** as a
managed, three-stage model pipeline on Kubernetes. It is the Java/Spring glue that lets a
user take a source ONNX or TFLite model, lower it to TVM's **Relax IR**, compile that IR
into a native shared library (`model.so`) for a chosen hardware target — optionally tuned
with MetaSchedule — and finally serve the compiled model behind an **Open Inference
Protocol v2 (KServe v2)** endpoint.

Maven coordinates: `it.smartcommunitylabdhub:dh-runtime-tvm`. It plugs into CORE as a
`@RuntimeComponent(runtime = "tvm")` and is discovered automatically at startup — no
central wiring changes are needed to add it.

> This README is the source of truth for the runtime. A `docs/` folder with an extended
> design write-up may exist in a working copy but is **not versioned** (it is gitignored),
> so do not rely on it.

---

## Table of contents

1. [Overview](#1-overview)
2. [The `tvm` function](#2-the-tvm-function)
3. [The three tasks](#3-the-three-tasks)
4. [Model kinds](#4-model-kinds)
5. [End-to-end flow](#5-end-to-end-flow)
6. [Runtime wiring (`TvmRuntime`)](#6-runtime-wiring-tvmruntime)
7. [Runners and helpers](#7-runners-and-helpers)
8. [Pod scripts](#8-pod-scripts)
9. [Images and configuration](#9-images-and-configuration)
10. [Model-centric serving](#10-model-centric-serving)
11. [Compiling for speed](#11-compiling-for-speed)
12. [Examples](#12-examples)
13. [Related projects](#13-related-projects)

---

## 1. Overview

`runtime-tvm` orchestrates Apache TVM as **three independent Kubernetes tasks**, each a
distinct run kind. The tasks never call each other directly: they pass artifacts through the
parent **`Function.spec`** (`ir_model`, `so_model`) and through `run.status.outputs`, a
"convention over wiring" chaining model borrowed from `runtime-python`.

| Task          | Input                                   | Output                                                       | Where it runs                                                   |
| ------------- | --------------------------------------- | ------------------------------------------------------------ | --------------------------------------------------------------- |
| `tvm+build`   | source model (ONNX or TFLite)           | Relax IR, published as a Model of kind **`tvm-ir`**          | one K8s **Job** on the `tvm-toolkit` image                      |
| `tvm+compile` | Relax IR (`tvm-ir` Model) + target arch | native `model.so`, published as a Model of kind **`tvm-so`** | one K8s **Job** on the `tvm-toolkit` image (runs `compiler.py`) |
| `tvm+serve`   | compiled `tvm-so` Model                 | KServe v2 inference endpoint                                 | K8s **Deployment + Service** running a swappable serve image    |

Design principles:

- **Portable IR.** One `tvm+build` → many `tvm+compile` runs, one per target platform
  (cpu, x86, arm64, armv7l), without re-parsing the source model.
- **Typed models.** Upload the source as a Model of kind **`onnx`** or **`tflite`**; build
  and compile publish **`tvm-ir`** and **`tvm-so`** Models.
- **S3-first via the SDK.** Build and compile Jobs publish their result as a **Model entity**
  on S3 (MinIO) using the `digitalhub` Python SDK, then write the Model key back into the
  run status; CORE copies it onto the function spec on completion.
- **Model-centric serving.** `tvm+serve` does _not_ use a baked per-model image. An init
  container downloads the `tvm-so` Model's S3 folder (`model.so` + `metadata.json`) into a
  **generic, swappable base serve image**.
- **Selectable serve runtime.** The default serve image is the native **Go** runtime in
  `digitalhub-serverless` (a Nuclio processor with the `tvm` runtime compiled in); the
  native **Rust** runtime in `digitalhub-tvm-rust` honours the same env contract and can be
  plugged in per task or per deployment.

```
                     ┌──────────────────────────────────────────────────────────┐
                     │                     Function (kind "tvm")                  │
                     │  spec.model  spec.format  spec.ir_model  spec.so_model     │
                     └──────────────────────────────────────────────────────────┘
   source model            │ (build writes ir_model)   │ (compile writes so_model)
   onnx / tflite           ▼                           ▼
        ┌───────────┐  tvm+build   ┌───────────┐  tvm+compile  ┌───────────┐  tvm+serve  ┌──────────┐
        │  s3://... │ ───────────► │  tvm-ir   │ ────────────► │  tvm-so   │ ──────────► │ KServe   │
        │ store://  │   (Job)      │  Model    │    (Job)      │  Model    │ (Deployment)│ v2 endpt │
        └───────────┘              └───────────┘               └───────────┘             └──────────┘
```

---

## 2. The `tvm` function

`TvmFunctionSpec` (`@SpecType(runtime="tvm", kind="tvm", entity=Function.class)`) describes a
"logical" model. Only the first two fields are user input; the last two are **outputs** that
`TvmRuntime` writes back when the build and compile tasks finish.

| Field (JSON) | Type                | User input?             | Meaning                                                                                                  |
| ------------ | ------------------- | ----------------------- | -------------------------------------------------------------------------------------------------------- |
| `model`      | string (`@NotNull`) | yes                     | Source model: an `s3://` / `https://` path or a `store://` model key.                                    |
| `format`     | enum `TvmFormat`    | yes                     | `auto` (default), `onnx` or `tflite`. See [format detection](#format-detection).                         |
| `ir_model`   | string              | **no — set by build**   | `store://` key of the built Relax IR Model (kind `tvm-ir`). Consumed by `tvm+compile`.                   |
| `so_model`   | string              | **no — set by compile** | `store://` key of the compiled Model (kind `tvm-so`). Consumed by `tvm+serve`.                           |

`ir_model`/`so_model` implement the chaining: after a build Job succeeds, `TvmRuntime`
records its Model key on `function.spec.ir_model`; a later `tvm+compile` picks it up
automatically (no manual wiring). Same for `so_model` after compile.

### Format detection

`tvm+build` needs to know the format to pick the builder image and script. In order:

1. `format` set explicitly to `onnx` or `tflite` always wins;
2. otherwise the **kind of the referenced Model**: a `store://…/model/onnx/…` key is ONNX,
   `store://…/model/tflite/…` is TFLite;
3. otherwise the **file extension** (`.onnx`, `.tflite`), for generic `model` Models and plain
   paths.

When none applies (a folder path of a generic Model) the build fails asking for a typed
Model or an explicit `format`.

---

## 3. The three tasks

Each task has three spec classes: a **task spec** (`K8sFunctionTaskBaseSpec` subclass, the
run template), a **run spec** (flattens function spec + task spec via `@JsonUnwrapped`), and a
run kind of the form `<task>:run`.

| Task kind     | Run kind          | Task spec            | Run spec            |
| ------------- | ----------------- | -------------------- | ------------------- |
| `tvm+build`   | `tvm+build:run`   | `TvmBuildTaskSpec`   | `TvmBuildRunSpec`   |
| `tvm+compile` | `tvm+compile:run` | `TvmCompileTaskSpec` | `TvmCompileRunSpec` |
| `tvm+serve`   | `tvm+serve:run`   | `TvmServeTaskSpec`   | `TvmServeRunSpec`   |

### 3.1 `tvm+build` — source → Relax IR

Converts the source model to TVM Relax IR and publishes it as a `tvm-ir` Model. A single
K8s Job runs on the `tvm-toolkit` image with the builder script of the format
(`builder_onnx.py` or `builder_tflite.py`). When the source is a folder, the entrypoint
uses the single file with the format's extension found in it.

Task fields (`TvmBuildTaskSpec`) — forwarded to the builder script as env vars:

| Field                    | Type   | Applies to | Effect                                                                                        |
| ------------------------ | ------ | ---------- | --------------------------------------------------------------------------------------------- |
| `image`                  | string | all        | Override the per-format builder image (default: `runtime.tvm.builders[<format>]`).            |
| `simplify`               | bool   | ONNX       | Run `onnxsim.simplify` before conversion.                                                     |
| `target_opset`           | int    | ONNX       | Convert the model to this opset (`onnx.version_converter`) first.                             |
| `opset_override`         | int    | ONNX       | Opset passed to `from_onnx`, overriding the model's declared opset.                           |
| `strict_shape_inference` | bool   | ONNX       | Strict mode during ONNX shape inference.                                                      |
| `data_prop`              | bool   | ONNX       | Enable data propagation during ONNX shape inference.                                          |
| `keep_params_in_input`   | bool   | ONNX       | Keep weights as graph inputs instead of folding them into constants; produces a `params.bin`. |
| `sanitize_input_names`   | bool   | ONNX       | Rewrite input tensor names to valid Relax identifiers.                                        |

The TFLite builder ignores the ONNX-only options.

### 3.2 `tvm+compile` — Relax IR → `model.so`

Lowers the Relax IR to a native shared library for a chosen hardware target and publishes it
as a `tvm-so` Model. It is a **plain K8s Job** running `compiler.py` on the `tvm-toolkit`
image — there is no image build. The IR to compile is taken from `task.model_path`
(explicit) or, if unset, from `function.spec.ir_model` (written by a prior build).

Task fields (`TvmCompileTaskSpec`) map directly to `compiler.py` arguments:

| Field                           | Type                         | Default                    | Effect                                                                                                                                                                                                                                                   |
| ------------------------------- | ---------------------------- | -------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `model_path`                    | string                       | → `function.spec.ir_model` | Explicit `store://` IR Model key to compile.                                                                                                                                                                                                             |
| `target_architecture`           | enum `TvmTargetArchitecture` | `cpu`                      | Target arch. Each value expands to a full `tvm.target.Target` string (see §4.6). The JSON key is deliberately `target_architecture`, **not** `target` — a form field literally named `target` breaks the console run-create form.                         |
| `target_num_cores`              | int ≥ 1                      | task CPU / auto            | Number of cores assumed by generated schedules. When omitted, the `resources.cpu` request (rounded up) is used; `x86_native` otherwise detects the pod CPU affinity. Use the same value during tuning and inference.                                    |
| `opt_level`                     | int ≥ 0                      | 3                          | TVM optimization level (0–3).                                                                                                                                                                                                                            |
| `cross_cc`                      | string                       | per-arch                   | Cross C++ compiler used by `export_library`. **Filled in automatically** when unset: `aarch64-linux-gnu-g++` for `arm64`/`arm64_pi5`, `arm-linux-gnueabihf-g++` for `armv7l`, nothing for the x86 targets. Set it explicitly only to override.         |
| `exec_mode`                     | string                       | `bytecode`                 | Relax VM execution mode: `bytecode` or `compiled`.                                                                                                                                                                                                       |
| `relax_pipeline`                | string                       | `default`                  | Named Relax optimization pipeline.                                                                                                                                                                                                                       |
| `tir_pipeline`                  | string                       | `default`                  | Named TIR optimization pipeline.                                                                                                                                                                                                                         |
| `benchmark_runs`                | int ≥ 0                      | 10                         | Timed inferences of the finished `model.so` inside the Job, recorded in `metadata.json` under `benchmark`; `0` disables it. Skipped (with the reason) for cross-compiled targets or when the library cannot run on the Job's CPU.                          |
| `tuning_mode`                   | enum                         | `off`                      | Standard Apache TVM MetaSchedule lifecycle: `off`, `tune`, or `apply`. See §11.                                                                                                                                                                         |
| `tuning_trials`                 | int ≥ 1                      | —                          | Global measurement budget; required with `tuning_mode: tune`.                                                                                                                                                                                            |
| `max_trials_per_task`           | int ≥ 1                      | 16                         | Limits the number of trials assigned to one extracted task.                                                                                                                                                                                              |
| `tuning_trials_per_iter`        | int ≥ 1                      | 64                         | Candidates measured for each task in one tuning round.                                                                                                                                                                                                   |
| `tuning_ops`                    | list of strings              | all                        | Optional task-name filters, for example `[conv2d]`.                                                                                                                                                                                                      |
| `tuning_model_path`             | string                       | —                          | Prior compiled `tvm-so` Model whose MetaSchedule database is resumed by `tune` or consumed by `apply`. Required by `apply`.                                                                                                                              |
| `tuning_runner`                 | enum `local`, `rpc`          | `local`                    | Standard TVM measurement runner. Use `rpc` to measure cross-compiled candidates on the target device.                                                                                                                                                   |
| `tuning_workers`                | int ≥ 1                      | task CPU / auto            | Maximum parallel LocalBuilder workers and RPC measurement sessions.                                                                                                                                                                                      |
| `tuning_seed`                   | int ≥ 0                      | 0                          | MetaSchedule random seed for reproducible searches.                                                                                                                                                                                                      |
| `tuning_number`                 | int ≥ 1                      | 3                          | Timed executions per evaluator result.                                                                                                                                                                                                                   |
| `tuning_repeat`                 | int ≥ 1                      | 1                          | Repeated evaluator results collected for each candidate.                                                                                                                                                                                                 |
| `tuning_min_repeat_ms`          | int ≥ 0                      | 100                        | Minimum duration TVM targets for each repeated measurement.                                                                                                                                                                                              |
| `tuning_alloc_repeat`           | int ≥ 1                      | 1                          | Number of input-allocation sets rotated by the runner to reduce cache-reuse bias, at the cost of additional memory.                                                                                                                                     |
| `tuning_enable_cpu_cache_flush` | bool                         | false                      | Flush CPU caches before evaluator measurements.                                                                                                                                                                                                          |
| `tuning_builder_timeout_sec`    | number > 0                   | 30                         | Timeout for compiling one tuning candidate.                                                                                                                                                                                                              |
| `tuning_runner_timeout_sec`     | number > 0                   | 30                         | Timeout for one local-runner candidate execution.                                                                                                                                                                                                        |
| `rpc_tracker_host`              | string                       | —                          | TVM RPC tracker host; required with `tuning_runner: rpc`.                                                                                                                                                                                                |
| `rpc_tracker_port`              | int 1–65535                  | —                          | TVM RPC tracker port; required with `tuning_runner: rpc`.                                                                                                                                                                                                |
| `rpc_tracker_key`               | string                       | —                          | Device key registered with the TVM RPC tracker; required with `tuning_runner: rpc`.                                                                                                                                                                      |
| `rpc_session_timeout_sec`       | int ≥ 1                      | 60                         | Timeout for acquiring and using an RPC measurement session.                                                                                                                                                                                              |
| `allow_partial_tuning`          | bool                         | false                      | Permit a budget below one complete task round or missing selected-function records. Keep false for production artifacts.                                                                                                                               |
| `system_lib`                    | bool                         | false                      | Build a system-lib style module (advanced).                                                                                                                                                                                                              |
| `params_path`                   | string                       | auto                       | Explicit `params.bin` to bind into the IR; otherwise auto-detected in the IR dir. **In-pod path only** — it is forwarded verbatim as `--params-file`, `store://` / `s3://` are _not_ resolved.                                                           |
| `tag`                           | string                       | `so`                       | Free-form tag recorded in the compiled model metadata and appended to the produced Model name (`<function>-<tag>`).                                                                                                                                     |
| `image`                         | string                       | `runtime.tvm.compiler`     | Override the compiler image.                                                                                                                                                                                                                             |

The runner rejects tuning settings that can only fail inside the Job (for example local
tuning of a cross-compiled target, or `apply` without `tuning_model_path`) when the run is
submitted.

### 3.3 `tvm+serve` — deploy the `tvm-so` Model

Deploys the compiled model behind the serve image (Open Inference v2: REST on `8080`, gRPC
on `9000`). **Model-centric**: the `tvm-so` Model to serve comes from `task.model_path`
(explicit) or `function.spec.so_model`. An init container downloads the Model's S3 folder
into `TVM_MODEL_DIR`; a swappable serve image (`runtime.tvm.serve`) loads it.

Task fields (`TvmServeTaskSpec`):

| Field          | Type                                 | Default                    | Effect                                                                                                                                                                  |
| -------------- | ------------------------------------ | -------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `model_path`   | string (pattern `store://…/model/…`) | → `function.spec.so_model` | Explicit `tvm-so` Model key to serve.                                                                                                                                   |
| `served_name`  | string                               | function name (cleaned)    | Model name exposed at `/v2/models/<served_name>`. Validated against `^[a-zA-Z0-9]([a-zA-Z0-9._-]*[a-zA-Z0-9])?$` — it lands in URLs and generated YAML.                 |
| `image`        | string                               | `runtime.tvm.serve`        | Override the serve image.                                                                                                                                               |
| `replicas`     | int ≥ 0                              | —                          | Deployment replica count (horizontal scaling).                                                                                                                          |
| `workers`      | int ≥ 1                              | —                          | In-process inference workers **per replica** (`TVM_SERVE_WORKERS`), read identically by the Go and Rust backends; each worker loads its own copy of the model.          |
| `service_type` | enum `CoreServiceType`               | `ClusterIP`                | `ClusterIP` / `NodePort` / `LoadBalancer`.                                                                                                                              |
| `service_name` | string                               | —                          | Extra Service alias `<funcName>-<service_name>`.                                                                                                                        |

**Threads.** Each worker runs its operators on its own TVM thread pool. When the task
requests CPUs (`resources.cpu`), the runner sets `TVM_NUM_THREADS` to the requested cores
divided by the workers (at least 1), so the pools fit the pod. Set `TVM_NUM_THREADS` in the
task `envs` to choose it yourself.

---

## 4. Model kinds

Runtime-tvm registers four Model kinds: two for the **source** models a user uploads, and
two for the artifacts the tasks produce. All of them have the generic Model fields `path`,
`framework` and `algorithm`.

### 4.1 Source models: `onnx` and `tflite`

Upload a source model with one of these kinds (from the console or the API) and point the
function's `model` at its `store://` key: `tvm+build` then knows the format without looking
at the file name. Every field is optional: the builders read the real signature from the
file, so these fields only describe the model in the catalog.

| Field        | Kind          | Type                       | Meaning                                                                    |
| ------------ | ------------- | -------------------------- | -------------------------------------------------------------------------- |
| `inputs`     | onnx, tflite  | `List<TvmTensorSpec>`      | Input tensors as declared by the model (name, dtype, shape, quantization). |
| `outputs`    | onnx, tflite  | `List<TvmTensorSpec>`      | Output tensors as declared by the model.                                   |
| `parameters` | onnx, tflite  | `Map<String,Serializable>` | Free-form extra information, e.g. the dataset or the export tool.          |
| `opset`      | onnx          | int ≥ 1                    | Default ONNX operator set version the model was exported with, e.g. `17`.  |

Classes: `TvmSourceModelSpec` (shared fields), `OnnxModelSpec` (`@SpecType kind = "onnx"`),
`TfliteModelSpec` (`@SpecType kind = "tflite"`). Generic `model` Models keep working: their
format comes from the file extension.

### 4.2 `TvmModelSpec` (base of `tvm-ir` and `tvm-so`)

| Field        | Type                       | Meaning                                        |
| ------------ | -------------------------- | ---------------------------------------------- |
| `entry`      | string                     | Relax entry function to invoke, e.g. `main`.   |
| `inputs`     | `List<TvmTensorSpec>`      | Input tensor signatures.                       |
| `outputs`    | `List<TvmTensorSpec>`      | Output tensor signatures.                      |
| `parameters` | `Map<String,Serializable>` | Free-form extra metadata (opset, model_name…). |

### 4.3 `TvmTensorSpec`

A single input/output tensor: `name` (string), `dtype` (element type, e.g. `float32`),
`shape` (`List<Long>`, e.g. `[1, 3, 640, 640]`).

Quantized tensors carry three more fields, describing the affine mapping
`real = (q - zero_point) * scale`:

| Field                 | Type           | Meaning                                                           |
| --------------------- | -------------- | ----------------------------------------------------------------- |
| `scale`               | `List<Double>` | Scale factor(s). More than one entry means per-axis quantization. |
| `zero_point`          | `List<Long>`   | Zero point(s), same cardinality as `scale`.                       |
| `quantized_dimension` | int            | Axis the per-axis entries are indexed by; absent for per-tensor.  |

**Quantization and source format are independent axes.** These fields appear whenever a
boundary tensor is `int8`/`uint8`, whether the model came from a TFLite full-integer
export or from a QDQ ONNX — the builders read them from different places (the tensor in
TFLite, the `QuantizeLinear`/`DequantizeLinear` nodes in ONNX) and write the same output.
A model that is quantized _internally_ but exposes `float32` at the boundary does **not**
carry them. The serve runtimes never use them for inference — TVM baked the quantization
into `model.so` — they only forward them on `/v2/models` so the caller can quantize its
input and dequantize the output.

### 4.4 `TvmIrModelSpec` (`@SpecType kind = "tvm-ir"`)

Produced by `tvm+build`. Adds, on top of the base signature, how the IR was derived:

| Field                  | Type             | Meaning                                                                     |
| ---------------------- | ---------------- | --------------------------------------------------------------------------- |
| `source_format`        | enum `TvmFormat` | Original source format.                                                     |
| `keep_params_in_input` | bool             | Whether ONNX initializers were kept as graph inputs vs folded to constants. |
| `sanitize_input_names` | bool             | Whether input names were rewritten to valid Relax identifiers.              |

Published S3 layout: `model.relax.json` (canonical, round-trip safe), `model.relax.ir`
(debug Relax IR text dump), `metadata.json`, and optionally `params.bin`.

### 4.5 `TvmSoModelSpec` (`@SpecType kind = "tvm-so"`)

Produced by `tvm+compile`. Adds the compile settings:

| Field       | Type                       | Meaning                                                    |
| ----------- | -------------------------- | ---------------------------------------------------------- |
| `target`    | string                     | Full `tvm.target.Target` string the library was built for. |
| `opt_level` | int                        | Optimization level used at compile time.                   |
| `manifest`  | `Map<String,Serializable>` | Parsed `metadata.json` emitted alongside the library.      |

Published S3 layout: `model.so` + `metadata.json`; tuned models also contain `tuning/`
with Apache TVM's JSON database, `manifest.json` (what the database was tuned for),
`tasks.json` and `coverage.json`.

### 4.6 Enums

**`TvmFormat`** — source model format for `tvm+build`: `auto`, `onnx`, `tflite`.

**`TvmTargetArchitecture`** — target for `tvm+compile`. Each constant carries the **full**
`tvm.target.Target` string (TVM 0.26 uses the JSON-dict form for specialized targets).
The constant name equals the schema value so the console renders a proper select dropdown;
the legacy value `llvm` is still accepted as an alias for `cpu`.

| Constant     | `getValue()` (→ `TVM_TARGET`)                                                                       | Runs on                                          |
| ------------ | --------------------------------------------------------------------------------------------------- | ------------------------------------------------ |
| `cpu`        | `llvm`                                                                                              | the architecture of the compile Job, generic CPU |
| `x86`        | `{"kind":"llvm","mcpu":"x86-64-v2"}`                                                                | any x86-64 CPU with SSE4.2                       |
| `x86_v3`     | `{"kind":"llvm","mcpu":"x86-64-v3"}`                                                                | x86-64 CPUs with AVX2 and FMA (Haswell and newer) |
| `x86_native` | `{"kind":"llvm","mcpu":"native"}`                                                                   | exactly the CPU model of the compile Job         |
| `arm64`      | `{"kind":"llvm","mtriple":"aarch64-linux-gnu"}`                                                     | any 64-bit ARM                                   |
| `arm64_pi5`  | `{"kind":"llvm","mtriple":"aarch64-linux-gnu","mcpu":"cortex-a76","mattr":["+neon"],"num-cores":4}` | Raspberry Pi 5                                   |
| `armv7l`     | `{"kind":"llvm","mtriple":"armv7l-linux-gnueabihf","mfloat-abi":"hard","mattr":["+neon"]}`          | 32-bit hard-float ARM (Raspberry Pi armhf)       |

For `x86_native`, `compiler.py` replaces `native` with LLVM's detected concrete CPU and
triple before compiling and records both requested and effective targets in metadata.

The serve images are published for `linux/amd64`, `linux/arm64` and `linux/arm/v7`, so every
target above can be served on a node of the matching architecture.

---

## 5. End-to-end flow

### 5.1 Build

```
POST run  tvm+build:run
   │
   ▼
TvmRuntime.build()   merge function spec + task spec  →  TvmBuildRunSpec
TvmRuntime.run()     → TvmBuildRunner.produce()
   │                    • resolveModelPath(store:// → s3://)
   │                    • format: explicit, else the Model kind, else the file extension
   │                    • assembles the build Job (envs, contextRefs, image, builder script)
   ▼
K8sJobRunnable   (image = tvm-toolkit, args = /bin/bash <home>/entrypoint.sh)
   │
   ▼  ── Pod ──────────────────────────────────────────────────────────────────
   init container    downloads source (s3/https) into  <home>/input/
   entrypoint.sh     reads TVM_TASK_KIND=tvm+build → builds CLI args → python task.py
   builder_<format>  load → convert → model.relax.json + metadata.json [+ params.bin]
   _dh_publish.py    logs a tvm-ir Model + S3 upload
                     → writes run.status.outputs.ir_module = <model.key>
   ── /Pod ─────────────────────────────────────────────────────────────────────
   │
   ▼
TvmRuntime.onComplete() → writeModelKeyBack(run, "ir_module", …)
   reads run.status.outputs.ir_module → sets function.spec.ir_model = <model.key>
```

### 5.2 Compile

```
POST run  tvm+compile:run
   │
   ▼
TvmRuntime.build()   →  TvmCompileRunSpec
TvmRuntime.run()     → TvmCompileRunner.produce()
   │                    • modelKey = task.model_path OR function.spec.ir_model  (required)
   │                    • resolveModelPath(store:// → s3://), force trailing "/"
   │                    • TVM_TARGET = target_architecture.getValue()  (default cpu)
   │                    • validates the tuning settings
   ▼
K8sJobRunnable   (image = tvm-toolkit, args = /bin/bash <home>/entrypoint.sh)
   │
   ▼  ── Pod ──────────────────────────────────────────────────────────────────
   init container   downloads the whole IR dir into  <home>/input/
   entrypoint.sh    reads TVM_TASK_KIND=tvm+compile → CLI args → python task.py
   compiler.py      load IR → [bind params] → resolve target → [MetaSchedule tune/apply]
                    → relax.build → export model.so → benchmark → metadata.json
   _dh_publish.py   logs a tvm-so Model + S3 upload
                    → optional CONSUMES relationship to the source IR Model
                    → writes run.status.outputs.compiled_so = <model.key>
   ── /Pod ─────────────────────────────────────────────────────────────────────
   │
   ▼
TvmRuntime.onComplete() → writeModelKeyBack(run, "compiled_so", …)
   reads run.status.outputs.compiled_so → sets function.spec.so_model = <model.key>
```

### 5.3 Serve

```
POST run  tvm+serve:run
   │
   ▼
TvmRuntime.build()   →  TvmServeRunSpec
TvmRuntime.run()     → TvmServeRunner.produce()
   │                    • modelKey = task.model_path OR function.spec.so_model  (required)
   │                    • resolveModelPath(store:// → s3://), force trailing "/"
   │                    • image = task.image OR runtime.tvm.serve  (swappable)
   ▼
K8sServeRunnable  (no command/args: the serve image's ENTRYPOINT launches the server)
   • contextRef: init container downloads model.so + metadata.json into  <home>/model/
   • env: TVM_TASK_KIND=tvm+serve, TVM_MODEL_DIR=<home>/model, TVM_MODEL_NAME=<served_name>,
          TVM_SERVE_WORKERS=<workers> and TVM_NUM_THREADS=<cpu / workers> (when set)
   • servicePorts 8080 (REST) + 9000 (gRPC), serviceType, service aliases
   │
   ▼
K8s Deployment + Service  →  Open Inference v2 endpoint at /v2/models/<served_name>/infer
```

Serve does **not** update the function spec (`onComplete` returns null for serve).

---

## 6. Runtime wiring (`TvmRuntime`)

`TvmRuntime extends K8sFunctionBaseRuntime<TvmFunctionSpec, TvmRunSpec, TvmRunStatus, K8sRunnable>`.

- **`RUNTIME = "tvm"`**, **`KINDS = { tvm+build:run, tvm+compile:run, tvm+serve:run }`**.
- Pod identity defaults: `UID = 1000`, `GID = 1000`, `HOME_DIR = "/shared"` (overridable via
  `TvmProperties`).
- The three runners (`TvmBuildRunner`, `TvmCompileRunner`, `TvmServeRunner`) are plain
  objects created in `afterPropertiesSet()` with the properties and the K8s helpers.

Lifecycle methods:

| Method                       | Behavior                                                                                                                                                                                                                                                                                                                           |
| ---------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `build(function, task, run)` | Assembles the run spec. Merge precedence: **run spec first**, task fills only unset keys, then **function spec overrides everything** (the function is the source of truth). Returns the reconfigured `TvmRunSpec`.                                                                                                                |
| `run(run)`                   | Dispatches by task kind to `buildRunner` / `compileRunner` / `serveRunner`, then attaches user `Credentials` and `Configurations` to the runnable.                                                                                                                                                                                 |
| `onBuilt(run)`               | Records **CONSUMES** lineage: each declared `run.spec.inputs` entry becomes a `RelationshipDetail(CONSUMES, run, input)` in the run's `RelationshipsMetadata`.                                                                                                                                                                     |
| `onComplete(run, runnable)`  | Both build and compile delegate to one generic `writeModelKeyBack(run, outputKey, setter, label)`; serve returns null. Exceptions are caught and logged, never propagated.                                                                                                                                                         |
| `writeModelKeyBack(…)`       | Reads `status.outputs.<outputKey>` (`ir_module` for build, `compiled_so` for compile), writes it to the matching function spec field (`ir_model` / `so_model`) via `FunctionManager`, and returns a `TvmRunStatus` with `modelKey`. A missing output key is logged as a warning and returns null — the function is left untouched. |
| `isSupported(run)`           | `run.kind ∈ KINDS`.                                                                                                                                                                                                                                                                                                                |

**Lifecycle managers.** Three thin `@RuntimeComponent`-annotated subclasses of
`RunLifecycleManager` register each run kind and delegate every hook to the single
`TvmRuntime` instance — they hold no logic:

- `TvmBuildLifecycleManager` → `tvm+build:run`
- `TvmCompileLifecycleManager` → `tvm+compile:run`
- `TvmServeLifecycleManager` → `tvm+serve:run`

**`TvmRunStatus`** (extends `RunBaseStatus`): `model_key` (Model produced by build/compile) and
`service` (`K8sServiceInfo`, populated for serve).

---

## 7. Runners and helpers

```
TvmBaseRunner  (abstract)
 ├─ resolves uid/gid/homeDir/volumeSize from TvmProperties (TvmRuntime constants as defaults)
 ├─ loads entrypoint.sh from the classpath
 ├─ addEnv()             → adds NAME=value only when the value is set
 ├─ hasTaskEnv()         → whether the task sets an env variable itself
 ├─ requestedCpuCores()  → resources.cpu rounded up, or null
 ├─ createEnvList()      → PROJECT_NAME, RUN_ID, TVM_HOME_DIR, TVM_INPUT_DIR, TVM_OUTPUT_DIR + task envs
 ├─ createSecrets()      → secret data as CoreEnv list
 ├─ createVolumes()      → task volumes + a shared scratch volume (sized from task disk or default)
 ├─ functionLabels()     → the `function=<name>` label on every runnable
 ├─ resolveImage()       → task image override, else the configured default, else throw
 └─ applyCommon()        → everything shared by Job and Serve runnables: runtime/task/state,
                           labels, image, envs, secrets, contextRefs, resources, volumes,
                           template (task `profile`), fsGroup/runAsGroup/runAsUser, id, project
     │
     ├── TvmBuildRunner    (build Job: format detection, builder image and script per format)
     ├── TvmCompileRunner  (compile Job: target, cross compiler, tuning validation and envs)
     └── TvmServeRunner    (serve Deployment: model folder, workers, TVM threads, Service names)
```

**`TvmRunnerHelper`** (stateless utilities):

| Method                                         | Purpose                                                                                                                                                                  |
| ---------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `resolveModel(key, modelManager)`              | The Model entity behind a `store://` key (latest version when the key has no id).                                                                                        |
| `resolveModelPath(path, modelManager)`         | `store://` model key → the Model's `spec.path` (`s3://…`); direct `s3://`/`https://` pass through.                                                                       |
| `resolveModelDir(modelKey, modelManager)`      | `resolveModelPath` + a forced trailing `/` so the init container pulls the whole folder (compile/serve inputs). Paths already ending in `/` or in `.zip` are left as-is. |
| `modelKindOf(reference)`                       | Model kind named in a `store://` key (`onnx`, `tflite`, `model`, …); null for plain paths.                                                                              |
| `inputContextRef(uri, dest)`                   | `ContextRef` telling the init container to pre-download an S3/HTTP source into the pod.                                                                                  |
| `createContextSources(entrypoint, taskScript)` | The files injected into every Job pod (see §8).                                                                                                                          |
| `loadClasspath(location)`                      | Text of a classpath resource (the pod scripts).                                                                                                                          |
| `cleanName(name)`                              | Last segment of a function name without the `function/tvm/` prefix or `:id` — used for `served_name` and Service names.                                                  |
| `extractFileName(uri)`                         | Last path segment of a URI.                                                                                                                                              |
| `parseCpuCores(quantity)`                      | Whole cores in a Kubernetes CPU quantity, rounded up (`500m` → 1).                                                                                                      |

---

## 8. Pod scripts

The pod scripts are **not** Java. They live in `src/main/resources/runtime-tvm/docker/` and
are injected into the Job pods as base64 `ContextSource` objects (mounted under `<home>/`).
Every build/compile pod receives three files: `entrypoint.sh`, the per-task script (always
mounted as `task.py`), and the shared publish helper `_dh_publish.py`.

| File                | Role                                                                                                                                                                                                                                                                                                |
| ------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `entrypoint.sh`     | Pod orchestrator. Reads `TVM_TASK_KIND` and translates the `TVM_*` env contract into CLI flags through one `ENV_VARIABLE --flag` table per task, then runs `python <home>/task.py …`.                                                                                                                |
| `builder_onnx.py`   | ONNX → Relax IR. `onnx.load` → (opset convert / `onnxsim.simplify` / shape inference) → `from_onnx` → `model.relax.json` + `metadata.json` [+ `params.bin`]. Extracts input/output tensor specs, quantization included. Publishes as `tvm-ir`.                                                     |
| `builder_tflite.py` | TFLite → Relax IR. `from_tflite` → `model.relax.json` + `metadata.json` with the quantization params of the boundary tensors. Publishes as `tvm-ir`.                                                                                                                                                 |
| `compiler.py`       | Relax IR → `model.so`, in seven steps: load the IR and bind `params.bin`; resolve the target; optional MetaSchedule `tune`/`apply` (§11); `relax.build` + `export_library`; benchmark in a child process; `metadata.json`; publish as `tvm-so` with an optional CONSUMES link to the source IR. |
| `_dh_publish.py`    | Shared SDK helper. `publish_model_and_register_output()` logs the typed Model, optionally adds a CONSUMES relationship, and writes the Model key into `run.status.outputs[<key>]`. Entity names are sanitized first (lowercased, anything outside `[a-zA-Z0-9._+-]` collapsed to `-`).              |

Implementation details worth knowing:

- **Typed Models with any SDK.** SDK releases without the TVM kinds only know `model`,
  `mlflow`, `sklearn` and `huggingface`. `_dh_publish.py` registers the missing `tvm-ir` /
  `tvm-so` kind at run time as a copy of the generic model builder whose spec accepts the
  TVM fields; if that fails with a future SDK, it logs a generic `model` and moves the typed
  fields into `parameters`, so nothing is lost.
- **`RUN_ID` is popped before importing `digitalhub`.** The SDK's run-context loader would try
  to load a run of kind `tvm+*:run`, but there is no Python builder for the TVM runtime (it's
  Java-only), which would raise `BuilderError`. `_dh_publish.py` keeps `RUN_ID` locally and
  updates status via a direct REST **read-modify-write GET + PUT** of the whole run (up to 3
  attempts, retrying on 409/412/500/502/503), because there is no PATCH endpoint. Auth comes
  from `DHCORE_ACCESS_TOKEN`, else `DHCORE_USER` + `DHCORE_PASSWORD`. A failure here is logged
  but does not fail the pod — the Model is already created, only the chaining key is lost.
- **Output keys.** Builders write `status.outputs.ir_module`; the compiler writes
  `status.outputs.compiled_so`. `TvmRuntime` reads exactly those keys.

---

## 9. Images and configuration

Configuration is bound from `src/main/resources/runtime-tvm.yml` (prefix `runtime.tvm`) into
`TvmProperties` by `TvmConfig`.

```yaml
runtime:
  tvm:
    user-id: ${RUNTIME_TVM_USER_ID:${kubernetes.security.user}}
    group-id: ${RUNTIME_TVM_GROUP_ID:${kubernetes.security.group}}
    home-dir: ${RUNTIME_TVM_HOME_DIR:/shared}
    volume-size: ${RUNTIME_TVM_VOLUME_SIZE:4Gi}

    # format -> builder image for tvm+build (onnx, tflite).
    builders:
      onnx: ${RUNTIME_TVM_BUILDER_ONNX:ghcr.io/scc-digitalhub/tvm-toolkit:0.26.0}
      tflite: ${RUNTIME_TVM_BUILDER_TFLITE:ghcr.io/scc-digitalhub/tvm-toolkit:0.26.0}

    # image running compiler.py for tvm+compile (IR -> model.so)
    compiler: ${RUNTIME_TVM_COMPILER:ghcr.io/scc-digitalhub/tvm-toolkit:0.26.0}

    # base serving image for tvm+serve (selectable)
    serve: ${RUNTIME_TVM_SERVE:ghcr.io/scc-digitalhub/tvm-runtime-go:0.26.0}

    entrypoint: classpath:/runtime-tvm/docker/entrypoint.sh
    builder-scripts:
      onnx: classpath:/runtime-tvm/docker/builder_onnx.py
      tflite: classpath:/runtime-tvm/docker/builder_tflite.py
```

| Property                                            | Used by       | Notes                                                                                                        |
| --------------------------------------------------- | ------------- | ------------------------------------------------------------------------------------------------------------ |
| `builders.<format>`                                 | `tvm+build`   | Per-format builder image. Overridable per task via `image`.                                                  |
| `compiler`                                          | `tvm+compile` | Image running `compiler.py`. Overridable per task via `image`.                                               |
| `serve`                                             | `tvm+serve`   | Base serve image; the `tvm-so` Model is downloaded into it at deploy time. Overridable per task via `image`. |
| `user-id` / `group-id` / `home-dir` / `volume-size` | all           | Pod identity + scratch volume defaults.                                                                      |
| `entrypoint` / `builder-scripts`                    | build/compile | Classpath locations of the injected pod scripts; a format without a configured script uses `builder_<format>.py`. |

There is **no bucket setting on this runtime**: the build/compile pods upload through the
`digitalhub` SDK, which resolves the destination from the platform's own files-store
configuration (`FILES_DEFAULT_STORE`) and the `AWS_*` credentials injected by the framework.

Only **two kinds of container image** are involved: `tvm-toolkit` (build + compile) and a
serve runtime image (default `tvm-runtime-go`). The image tags are the release versions of
the projects that build them, which are also the Apache TVM versions they contain.

---

## 10. Model-centric serving

Serving is intentionally **model-centric** rather than image-centric:

- The **model is the artifact**: the compiled `tvm-so` Model lives on S3 and is downloaded at
  deploy time by an init container into `TVM_MODEL_DIR` (`<home>/model`). There is no baked
  per-model image and no image build in the serve (or compile) path.
- The **serve image is generic and swappable**. The same base image can serve any compiled
  model; you select it with `runtime.tvm.serve` (global default), `RUNTIME_TVM_SERVE` (env),
  or the task `image` field (per deployment).
  - Default: a **native Go** runtime shipped in `digitalhub-serverless` — a Nuclio
    processor with the `tvm` runtime compiled in (cgo → TVM), Open Inference v2 on
    REST `8080` + gRPC `9000`.
  - Alternative: a **native Rust** runtime (`digitalhub-tvm-rust`), same contract.
- **Contract** the serve image must honor: read `TVM_MODEL_DIR` (a folder with `model.so` +
  `metadata.json`), `TVM_MODEL_NAME` (the served name), `TVM_SERVE_WORKERS` (defaulting to
  1 when unset) and let the TVM runtime read `TVM_NUM_THREADS`; expose Open Inference v2 on
  ports `8080`/`9000`. The base image's `ENTRYPOINT` starts the server, so the runner sets no
  command/args.
- Both serve images refuse to start a model compiled by a different TVM version or source
  revision, or for another CPU architecture.
- Ports are **hardcoded** (`HTTP_PORT = 8080`, `GRPC_PORT = 9000`) and the Kubernetes
  Service is left to the framework — no custom `Service` object is created, mirroring
  `runtime-python`. A best-effort `<funcName>-latest` Service alias is added only when the run
  belongs to the function's current latest version.
  > The images also honour `TVM_SERVE_PORT` / `TVM_SERVE_GRPC_PORT`, but the runner never sets
  > them and always publishes 8080/9000 on the Service. Overriding them through the task
  > `envs` would move the listeners away from the ports the Service targets — don't.

---

## 11. Compiling for speed

On CPU, Apache TVM 0.26 has **no default schedule** for the operators: without tuning they
are compiled as plain loops. They work, but a convolutional network such as YOLOv8n can be
tens of times slower than ONNX Runtime. `tvm+compile` makes the model fast with
**MetaSchedule**, TVM's auto-tuner, following TVM's own `static_shape_tuning` pipeline:

1. **Prepare.** The IR is decomposed and legalized, and the operators are fused into TIR
   functions: these are the tuning **tasks** (`tuning/tasks.json`).
2. **Tune** (`tuning_mode: tune`). MetaSchedule visits the tasks in turn. Each round
   generates `tuning_trials_per_iter` candidate schedules per task, compiles them on
   `tuning_workers` processes and times them on the Job (or on a device through RPC). The
   best ones are stored in the database under `tuning/`. A task stops at
   `max_trials_per_task`, the whole search at `tuning_trials`.
3. **Apply.** Every TIR function covered by the database is replaced by its best schedule.
   `tuning_mode: apply` does only this step, from the database of an earlier compile.
4. **Prepack the weights** (LLVM targets). Before tuning the constant weights are marked
   as free to change layout; after applying, they are rewritten into the layout the tuned
   schedules prefer and folded back into constants, so `model.so` still takes only the
   real inputs (TVM's `cpu_weight_prepack`). The database records it
   (`cpu_weight_prepack` in `manifest.json`); older databases without the flag are applied
   without prepacking.
5. **Measure.** The finished library is timed (`benchmark_runs`) and the result lands in
   `metadata.json`, so an untuned and a tuned compile of the same IR can be compared.

**How big a budget?** Tuning proceeds in rounds, so the budget must at least cover one round
over every task: `tasks × min(tuning_trials_per_iter, max_trials_per_task)`. With 65 tasks
(YOLOv8n) and the default 64 candidates per round that is 4160 trials; a smaller budget
leaves the last tasks untuned, and the compile refuses it unless `allow_partial_tuning` is
set. The compiler prints both this minimum and `tasks × max_trials_per_task`, the budget
that gives every task its full share. As a rule of thumb, start from 64–256 trials per task.

**Keep the numbers consistent.** The schedules are generated for `target_num_cores` threads
and measured with `TVM_NUM_THREADS` set to the same value; serve with the same number of
threads per worker to get the measured speed. A database is only valid for the same IR,
TVM build and target (the compile checks `manifest.json`).

**Cross targets.** The local runner executes candidates on the compile Job, so it cannot
tune ARM targets from an x86 Job: use `tuning_runner: rpc` with a device, or `apply` a
database tuned on the device. The registered RPC server must use the same TVM revision,
be built with `USE_RANDOM=ON` and start with `TVM_NUM_THREADS` equal to `target_num_cores`.

---

## 12. Examples

### 12.1 Upload the source and run the three tasks

```yaml
# 1) Model: the ONNX file, uploaded with kind onnx
kind: onnx
name: yolov8n
spec:
  path: "s3://digitalhub/models/yolov8n.onnx"
  opset: 17
```

```yaml
# 2) Function: points at the typed Model, so the format is known
kind: tvm
spec:
  model: "store://<project>/model/onnx/yolov8n"
```

```yaml
# 3) tvm+build task → produces a tvm-ir Model, sets function.spec.ir_model
kind: tvm+build
spec:
  simplify: true
  resources: { cpu: "2", mem: "4Gi" }
```

```yaml
# 4) tvm+compile task → produces a tvm-so Model, sets function.spec.so_model
kind: tvm+compile
spec:
  target_architecture: x86_v3
  target_num_cores: 4
  opt_level: 3
  exec_mode: compiled
  # model_path omitted → uses function.spec.ir_model from the build
  resources: { cpu: "4", mem: "8Gi" } # compile is memory-hungry
```

```yaml
# 5) tvm+serve task → deploys the tvm-so Model behind the serve image
kind: tvm+serve
spec:
  served_name: yolov8n
  service_type: NodePort
  workers: 1
  # model_path omitted → uses function.spec.so_model from the compile
  # image omitted → uses runtime.tvm.serve (default: tvm-runtime-go)
  resources: { cpu: "4" } # → TVM_NUM_THREADS=4
```

### 12.2 Tune once, then reuse the result

```yaml
kind: tvm+compile
spec:
  target_architecture: x86_native
  target_num_cores: 4
  exec_mode: compiled
  tuning_mode: tune
  tuning_trials: 8192
  max_trials_per_task: 256
  tuning_workers: 4
  resources: { cpu: "4", mem: "8Gi" }
```

The resulting `tvm-so` Model contains the database, and its `metadata.json` holds
`meta_schedule` (tasks, coverage, prepacking) and `benchmark` (timings of the tuned
library). Recompile the same IR for the exact same target without measuring again:

```yaml
kind: tvm+compile
spec:
  target_architecture: x86_native
  target_num_cores: 4
  exec_mode: compiled
  tuning_mode: apply
  tuning_model_path: "store://<project>/model/tvm-so/<name>:<id>"
  resources: { cpu: "4", mem: "8Gi" }
```

For a quick check of the flow (not for performance), a tiny budget with
`allow_partial_tuning: true` is enough.

### 12.3 Inference request (Open Inference v2)

```
POST http://<host>:<port>/v2/models/yolov8n/infer
{
  "id": "req-1",
  "inputs": [
    { "name": "images", "datatype": "FP32", "shape": [1, 3, 640, 640], "data": [ ... ] }
  ]
}

→ { "model_name": "yolov8n",
    "outputs": [ { "name": "output0", "datatype": "FP32", "shape": [1, 84, 8400], "data": [ ... ] } ] }
```

---

## 13. Related projects

| Project                                                               | Role                                                                                                                                                  |
| --------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`digitalhub-core`**                                                 | This repository. Hosts `runtime-tvm` (the Java/Spring integration) alongside the other runtimes and the platform core.                                |
| **`digitalhub-tvm-toolkit`** (`ghcr.io/scc-digitalhub/tvm-toolkit`)   | Builder/compiler image: Apache TVM + LLVM + native and cross g++ + ONNX + TFLite + MetaSchedule dependencies + the `digitalhub` SDK.                   |
| **`digitalhub-serverless`** (`ghcr.io/scc-digitalhub/tvm-runtime-go`) | Home of the **native Go** serve runtime — the default serve image — implementing the `TVM_MODEL_DIR` / Open Inference v2 contract.                    |
| **`digitalhub-tvm-rust`** (`ghcr.io/scc-digitalhub/tvm-runtime-rust`) | Alternative serve runtime: a native **Rust** server that loads `model.so`, runs the Relax VM, and exposes Open Inference v2 (REST + gRPC). No Python. |
| **`digitalhub` Python SDK**                                           | Used inside the build/compile pods to create the Model entities and upload artifacts to S3.                                                           |

A change to the contract between these projects (the `TVM_*` env variables, `metadata.json`,
the Model kinds) must be carried to all of them.

---

## Build and test

This module is built as part of `digitalhub-core`. Use **JDK 21** (Lombok annotation
processing is disabled by `javac` 23+, causing spurious `cannot find symbol` errors on
JDK 25):

```bash
export JAVA_HOME=~/.sdkman/candidates/java/21.0.4-graal
mvn -o -pl runtimes/runtime-tvm install
```

The pod scripts have their own tests, run inside the toolkit image:

```bash
docker run --rm -v "$PWD/runtimes/runtime-tvm/src":/src -w /src/test/python \
  ghcr.io/scc-digitalhub/tvm-toolkit:<version> python3 -m unittest -v test_compiler
```
