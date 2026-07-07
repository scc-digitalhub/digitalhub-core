# runtime-tvm

DigitalHub CORE runtime that integrates **[Apache TVM](https://tvm.apache.org/)** as a
managed, three-stage model pipeline on Kubernetes. It is the Java/Spring glue that lets a
user take a source ONNX model, lower it to TVM's **Relax IR**,
compile that IR into a native shared library (`model.so`) for a chosen hardware target, and
finally serve the compiled model behind an **Open Inference Protocol v2 (KServe v2)** endpoint.

Maven coordinates: `it.smartcommunitylabdhub:dh-runtime-tvm`. It plugs into CORE as a
`@RuntimeComponent(runtime = "tvm")` and is discovered automatically at startup — no
central wiring changes are needed to add it.

> **Note on `docs/`:** `docs/ARCHITECTURE.md` is the current, English design reference for
> this runtime — it is **not** stale and tracks the code described in this README. The old
> `RUNBOOK.md` has been removed. The figures `img/fig-chaining`, `img/fig-compile`, and
> `img/fig-overview` originally depicted the earlier *baked / Kaniko* design, where
> `tvm+compile` built a self-contained per-model serve image published as
> `function.spec.serve_images[tag]`; they have been redrawn for the current model-centric
> architecture. This README stays the concise source of truth.

---

## Table of contents

1. [Overview](#1-overview)
2. [The `tvm` function](#2-the-tvm-function)
3. [The three tasks](#3-the-three-tasks)
4. [Model kinds: `tvm-ir` and `tvm-so`](#4-model-kinds-tvm-ir-and-tvm-so)
5. [End-to-end flow](#5-end-to-end-flow)
6. [Runtime wiring (`TvmRuntime`)](#6-runtime-wiring-tvmruntime)
7. [Runners and helpers](#7-runners-and-helpers)
8. [Pod scripts](#8-pod-scripts)
9. [Images and configuration](#9-images-and-configuration)
10. [Model-centric serving](#10-model-centric-serving)
11. [Examples](#11-examples)
12. [Parameter reference](#12-parameter-reference)
13. [Related projects](#13-related-projects)

---

## 1. Overview

`runtime-tvm` orchestrates Apache TVM as **three independent Kubernetes tasks**, each a
distinct run kind. The tasks never call each other directly: they pass artifacts through the
parent **`Function.spec`** (`ir_model`, `so_model`) and through `run.status.outputs`, a
"convention over wiring" chaining model borrowed from `runtime-python`.

| Task | Input | Output | Where it runs |
|---|---|---|---|
| `tvm+build`   | source model (ONNX) | Relax IR, published as a Model of kind **`tvm-ir`** | one K8s **Job** on the `tvm-toolkit` image |
| `tvm+compile` | Relax IR (`tvm-ir` Model) + target arch | native `model.so`, published as a Model of kind **`tvm-so`** | one K8s **Job** on the `tvm-toolkit` image (runs `compiler.py`) |
| `tvm+serve`   | compiled `tvm-so` Model | KServe v2 inference endpoint | K8s **Deployment + Service** running a swappable serve image |

Design principles:

- **Portable IR.** One `tvm+build` → many `tvm+compile` runs, one per target platform
  (cpu, x86, arm64), without re-parsing the source model.
- **S3-first via the SDK.** Build and compile Jobs publish their result as a **Model entity**
  on S3 (MinIO) using the `digitalhub` Python SDK, then write the Model key back into the
  run status; CORE copies it onto the function spec on completion.
- **Model-centric serving.** `tvm+serve` does *not* use a baked per-model image. An init
  container downloads the `tvm-so` Model's S3 folder (`model.so` + `metadata.json`) into a
  **generic, swappable base serve image**. This mirrors `runtime-python`'s serve path
  (fixed ports, framework-default Service, no custom Service object).
- **Selectable serve runtime.** The default serve image is a native **Rust** TVM runtime
  (`digitalhub-tvm-rust`), but any image implementing the same contract can be plugged in
  per task or per deployment (a native Go runtime in `digitalhub-serverless` is also available).

```
                     ┌──────────────────────────────────────────────────────────┐
                     │                     Function (kind "tvm")                  │
                     │  spec.model  spec.format  spec.ir_model  spec.so_model     │
                     └──────────────────────────────────────────────────────────┘
   source model            │ (build writes ir_model)   │ (compile writes so_model)
   onnx source             ▼                           ▼
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

| Field (JSON) | Type | User input? | Meaning |
|---|---|---|---|
| `model`      | string (`@NotNull`) | yes | Source model reference: an `s3://` / `https://` path, a `store://` model key, or a bare file path (`.onnx`). |
| `format`     | enum `TvmFormat`    | yes | `auto` (default) or `onnx`. `auto` lets the build task infer the frontend from the `.onnx` file extension. |
| `ir_model`   | string              | **no — set by build**   | `store://` key of the built Relax IR Model (kind `tvm-ir`). Consumed by `tvm+compile`. |
| `so_model`   | string              | **no — set by compile** | `store://` key of the compiled Model (kind `tvm-so`). Consumed by `tvm+serve`. |

`ir_model`/`so_model` implement the chaining: after a build Job succeeds, `TvmRuntime`
records its Model key on `function.spec.ir_model`; a later `tvm+compile` picks it up
automatically (no manual wiring). Same for `so_model` after compile.

---

## 3. The three tasks

Each task has three spec classes: a **task spec** (`K8sFunctionTaskBaseSpec` subclass, the
run template), a **run spec** (flattens function spec + task spec via `@JsonUnwrapped`), and a
run kind of the form `<task>:run`.

| Task kind | Run kind | Task spec | Run spec |
|---|---|---|---|
| `tvm+build`   | `tvm+build:run`   | `TvmBuildTaskSpec`   | `TvmBuildRunSpec`   |
| `tvm+compile` | `tvm+compile:run` | `TvmCompileTaskSpec` | `TvmCompileRunSpec` |
| `tvm+serve`   | `tvm+serve:run`   | `TvmServeTaskSpec`   | `TvmServeRunSpec`   |

### 3.1 `tvm+build` — source → Relax IR

Converts the source model to TVM Relax IR and publishes it as a `tvm-ir` Model. A single
K8s Job runs on the `tvm-toolkit` image; the frontend (and therefore the injected
`builder_*.py`) is chosen by format.

Task fields (`TvmBuildTaskSpec`) — forwarded to the ONNX builder script as env vars:

| Field | Type | Applies to | Effect |
|---|---|---|---|
| `image`                  | string  | all     | Override the per-format builder image (default: `runtime.tvm.builders[<format>]`). |
| `simplify`               | bool    | ONNX    | Run `onnxsim.simplify` before conversion. |
| `target_opset`           | int     | ONNX    | Convert the model to this opset (`onnx.version_converter`) first. |
| `opset_override`         | int     | ONNX    | Opset passed to `from_onnx`, overriding the model's declared opset. |
| `strict_shape_inference` | bool    | ONNX    | Strict mode during ONNX shape inference. |
| `data_prop`              | bool    | ONNX    | Enable data propagation during ONNX shape inference. |
| `keep_params_in_input`   | bool    | ONNX    | Keep weights as graph inputs instead of folding them into constants; produces a `params.bin`. |
| `sanitize_input_names`   | bool    | ONNX    | Rewrite input tensor names to valid Relax identifiers. |

### 3.2 `tvm+compile` — Relax IR → `model.so`

Lowers the Relax IR to a native shared library for a chosen hardware target and publishes it
as a `tvm-so` Model. It is a **plain K8s Job** running `compiler.py` on the `tvm-toolkit`
image — there is **no Kaniko** and no image build. The IR to compile is taken from
`task.model_path` (explicit) or, if unset, from `function.spec.ir_model` (written by a prior
build).

Task fields (`TvmCompileTaskSpec`) map directly to `compiler.py` arguments:

| Field | Type | Default | Effect |
|---|---|---|---|
| `model_path`          | string             | → `function.spec.ir_model` | Explicit `store://` IR Model key to compile. |
| `target_architecture` | enum `TvmTargetArchitecture` | `cpu` | Target arch. **One field is enough**: each enum value expands to a full `tvm.target.Target` string (see §4.5). The JSON key is deliberately `target_architecture`, **not** `target` — a form field literally named `target` breaks the console run-create form. |
| `opt_level`           | int ≥ 0            | 3       | TVM optimization level (0–3). |
| `cross_cc`            | string            | —       | Cross C++ compiler used by `export_library` when cross-compiling (e.g. `aarch64-linux-gnu-g++`). |
| `exec_mode`           | string            | `bytecode` | Relax VM execution mode: `bytecode` or `compiled`. |
| `relax_pipeline`      | string            | `default` | Named Relax optimization pipeline. |
| `tir_pipeline`        | string            | `default` | Named TIR optimization pipeline. |
| `system_lib`          | bool              | false   | Build a system-lib style module (advanced). |
| `params_path`         | string            | auto    | Explicit `params.bin` to bind into the IR; otherwise auto-detected in the IR dir. |
| `tag`                 | string            | `so`    | Free-form tag recorded in the compiled model metadata and appended to the produced Model name (`<function>-<tag>`). |
| `image`               | string            | `runtime.tvm.compiler` | Override the compiler image. |

### 3.3 `tvm+serve` — deploy the `tvm-so` Model

Deploys the compiled model behind the TVM serve server (Open Inference v2: REST on `8080`,
gRPC on `9000`). **Model-centric**: the `tvm-so` Model to serve comes from
`task.model_path` (explicit) or `function.spec.so_model`. An init container downloads the
Model's S3 folder into `TVM_MODEL_DIR`; a swappable serve image (`runtime.tvm.serve`) loads
it. Ports are hardcoded in the runner and the Service follows the framework default (like
`runtime-python`), so no custom Service is built.

Task fields (`TvmServeTaskSpec`):

| Field | Type | Default | Effect |
|---|---|---|---|
| `model_path`   | string (pattern `store://…/model/…`) | → `function.spec.so_model` | Explicit `tvm-so` Model key to serve. |
| `served_name`  | string      | function name (cleaned) | Model name exposed at `/v2/models/<served_name>`. |
| `image`        | string      | `runtime.tvm.serve`     | Override the serve image. |
| `replicas`     | int ≥ 0     | —       | Deployment replica count (horizontal scaling). |
| `workers`      | int ≥ 1     | —       | In-process inference workers **per replica** (`TVM_SERVE_WORKERS`), read identically by the Rust and Go backends; each worker loads its own copy of the model (vertical scaling). |
| `service_type` | enum `CoreServiceType` | `ClusterIP` | `ClusterIP` / `NodePort` / `LoadBalancer`. |
| `service_name` | string      | —       | Extra Service alias `<funcName>-<service_name>`. |

---

## 4. Model kinds: `tvm-ir` and `tvm-so`

The two derived artifacts are stored as typed **Model** entities. Both extend a shared base
that captures the model's call signature.

### 4.1 `TvmModelSpec` (base)

| Field | Type | Meaning |
|---|---|---|
| `entry`      | string | Relax entry function to invoke, e.g. `main`. |
| `inputs`     | `List<TvmTensorSpec>`  | Input tensor signatures. |
| `outputs`    | `List<TvmTensorSpec>`  | Output tensor signatures. |
| `parameters` | `Map<String,Serializable>` | Free-form extra metadata (opset, model_name, shape/dtype overrides…). |

### 4.2 `TvmTensorSpec`

A single input/output tensor: `name` (string), `dtype` (element type, e.g. `float32`),
`shape` (`List<Long>`, e.g. `[1, 3, 640, 640]`).

### 4.3 `TvmIrModelSpec` (`@SpecType kind = "tvm-ir"`)

Produced by `tvm+build`. Adds, on top of the base signature, how the IR was derived:

| Field | Type | Meaning |
|---|---|---|
| `source_format`       | enum `TvmFormat` | Original source format. |
| `keep_params_in_input`| bool | Whether ONNX initializers were kept as graph inputs vs folded to constants. |
| `sanitize_input_names`| bool | Whether input names were rewritten to valid Relax identifiers. |

Published S3 layout: `model.relax.json` (canonical, round-trip safe), `model.relax.ir`
(debug Relax IR text dump), `metadata.json`, and optionally `params.bin`.

### 4.4 `TvmSoModelSpec` (`@SpecType kind = "tvm-so"`)

Produced by `tvm+compile`. Adds the compile settings:

| Field | Type | Meaning |
|---|---|---|
| `target`    | string | Full `tvm.target.Target` string the library was built for. |
| `opt_level` | int    | Optimization level used at compile time. |
| `manifest`  | `Map<String,Serializable>` | Parsed `metadata.json` emitted alongside the library. |

Published S3 layout: `model.so` + `metadata.json`.

### 4.5 Enums

**`TvmFormat`** — source model format for `tvm+build`: `auto`, `onnx`.

**`TvmTargetArchitecture`** — target for `tvm+compile`. Each constant carries the **full**
`tvm.target.Target` string (TVM 0.25 dropped the CLI target syntax, so specialized targets
use the JSON-dict form). The constant name equals the schema value so the console renders a
proper select dropdown; the legacy value `llvm` is still accepted as an alias for `cpu`.

| Constant | `getValue()` (→ `TVM_TARGET`) |
|---|---|
| `cpu`   | `llvm` |
| `x86`   | `{"kind":"llvm","mcpu":"x86-64-v2"}` |
| `arm64` | `{"kind":"llvm","mtriple":"aarch64-linux-gnu"}` |

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
   │                    • pick frontend by format (or auto-detect by extension)
   │                    • Frontend.produce() → TvmBuildFrontendRunner.buildJobRunnable()
   ▼
K8sJobRunnable   (image = tvm-toolkit, args = /bin/bash <home>/entrypoint.sh)
   │
   ▼  ── Pod ──────────────────────────────────────────────────────────────────
   init container   downloads source (s3/https) into  <home>/input/
   entrypoint.sh    reads TVM_TASK_KIND=tvm+build → builds CLI args → python task.py
   builder_<fmt>.py load → convert → model.relax.json + metadata.json [+ params.bin]
   _dh_publish.py   dh.log_tvm_ir(...)  → creates tvm-ir Model + S3 upload
                    → PATCH run.status.outputs.ir_module = <model.key>
   ── /Pod ─────────────────────────────────────────────────────────────────────
   │
   ▼
TvmRuntime.onComplete() → onBuildComplete()
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
   ▼
K8sJobRunnable   (image = tvm-toolkit, args = /bin/bash <home>/entrypoint.sh)
   │
   ▼  ── Pod ──────────────────────────────────────────────────────────────────
   init container   downloads the whole IR dir into  <home>/input/
   entrypoint.sh    reads TVM_TASK_KIND=tvm+compile → CLI args → python task.py
   compiler.py      load_json → [bind params] → relax.build(target) → export_library(model.so)
                    → metadata.json (target, opt_level, …)
   _dh_publish.py   dh.log_tvm_so(...) → creates tvm-so Model + S3 upload
                    → optional CONSUMES relationship to the source IR Model
                    → PATCH run.status.outputs.compiled_so = <model.key>
   ── /Pod ─────────────────────────────────────────────────────────────────────
   │
   ▼
TvmRuntime.onComplete() → onCompileComplete()
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
   • env: TVM_MODEL_DIR=<home>/model, TVM_MODEL_NAME=<served_name>
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
- **`afterPropertiesSet()`** builds the `TvmBuildRunner` from the injected list of
  `TvmFrontend` beans (the compile/serve runners are `@Autowired` components).

Lifecycle methods:

| Method | Behavior |
|---|---|
| `build(function, task, run)` | Assembles the run spec. Merge precedence: **run spec first**, task fills only unset keys, then **function spec overrides everything** (the function is the source of truth). Returns the reconfigured `TvmRunSpec`. |
| `run(run)` | Dispatches by task kind to `buildRunner` / `compileRunner` / `serveRunner`, then attaches user `Credentials` and `Configurations` to the runnable. |
| `onBuilt(run)` | Records **CONSUMES** lineage: each declared `run.spec.inputs` entry becomes a `RelationshipDetail(CONSUMES, run, input)` in the run's `RelationshipsMetadata`. |
| `onComplete(run, runnable)` | For `tvm+build:run` → `onBuildComplete`; for `tvm+compile:run` → `onCompileComplete`; serve returns null. |
| `onBuildComplete(run)` | Reads `status.outputs.ir_module`, writes it to `function.spec.ir_model`, returns a `TvmRunStatus` with `modelKey`. |
| `onCompileComplete(run)` | Reads `status.outputs.compiled_so`, writes it to `function.spec.so_model`, returns a `TvmRunStatus` with `modelKey`. |
| `isSupported(run)` | `run.kind ∈ KINDS`. |

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
 ├─ resolves uid/gid/homeDir/volumeSize/bucket from TvmProperties (falls back to TvmRuntime defaults)
 ├─ loads entrypoint.sh from classpath
 ├─ createEnvList()  → PROJECT_NAME, RUN_ID, TVM_HOME_DIR, TVM_INPUT_DIR, TVM_OUTPUT_DIR,
 │                     TVM_OUTPUT_S3_BUCKET, + task envs (appended last so they can override)
 ├─ createSecrets()  → secret data as CoreEnv list
 └─ createVolumes()  → task volumes + a shared scratch volume (sized from task disk or default)
     │
     ├── TvmBuildFrontendRunner  (abstract; buildJobRunnable() assembles the build Job)
     │     └── OnnxFrontend       (@Component; format "onnx", default input model.onnx)
     ├── TvmCompileRunner  (@Component; assembles the compile Job)
     └── TvmServeRunner    (@Component; assembles the serve Deployment)

TvmBuildRunner   (plain class, built in afterPropertiesSet)
   dispatches tvm+build to a TvmFrontend by explicit format or extension auto-detect
```

**`TvmFrontend`** is a strategy interface (`getFormat`, `canHandle`, `produce`). To add a
format: implement it as a `@Component` and add a `builders` entry in `runtime-tvm.yml` — **no
changes to `TvmRuntime`**. Today the only frontend is `OnnxFrontend`; it delegates Job assembly
to `TvmBuildFrontendRunner.buildJobRunnable`, contributing its format name, default input
filename, and format-specific env vars.

**Auto-detect.** `TvmBuildRunner.resolveFormat` uses the explicit `format` if set and not
`auto`; otherwise it asks each frontend `canHandle(path, null)`. ONNX auto-detects by the
`.onnx` extension.

**`TvmRunnerHelper`** (stateless utilities):

| Method | Purpose |
|---|---|
| `resolveModelPath(path, modelService)` | `store://` model key → the Model entity's concrete `spec.path` (`s3://…`); direct `s3://`/`https://` pass through. |
| `resolveSourcePath(path)` | Returns the path as-is (already-resolved concrete URI; the init container has no `store` protocol). |
| `buildOutputPrefix(...)` | `s3://<bucket>/<project>/model/<algorithm>/<funcName>-<suffix>/<runId>/`. |
| `inputContextRef(uri, dest)` | `ContextRef` telling the init container to pre-download an S3/HTTP source into the pod. |
| `createContextSources(entrypoint, taskScript)` | The files injected into every Job pod (see §8). |
| `cleanName(name)` | Last segment of a function name without the `function/tvm/` prefix or `:id` — used for `served_name` and Service names. |
| `extractFileName(uri)` | Last path segment (falls back to the format's default input filename). |
| `jsonEnv(name, value)` | Serialize a structured value to a single JSON-valued env var. |

---

## 8. Pod scripts

The pod scripts are **not** Java. They live in `src/main/resources/runtime-tvm/docker/` and
are injected into the Job pods as base64 `ContextSource` objects (mounted under `<home>/`).
Every build/compile pod receives three files: `entrypoint.sh`, the per-task script (always
mounted as `task.py`), and the shared publish helper `_dh_publish.py`.

| File | Role |
|---|---|
| `entrypoint.sh` | Pod orchestrator. Reads `TVM_TASK_KIND`, translates the `TVM_*` env contract into CLI flags, and runs `python <home>/task.py …`. Handles both `tvm+build` and `tvm+compile`. |
| `builder_onnx.py` | ONNX → Relax IR. `onnx.load` → (opset convert / `onnxsim.simplify` / shape inference) → `from_onnx` → `model.relax.json` + `metadata.json` [+ `params.bin`]. Extracts input/output tensor specs. Publishes as `tvm-ir`. |
| `compiler.py` | Relax IR → `model.so`. `tvm.ir.load_json` → optional `BindParams` for `keep_params_in_input` builds → `relax.build(target)` → `export_library(model.so)` → updated `metadata.json`. Publishes as `tvm-so`, with an optional CONSUMES link to the source IR. |
| `_dh_publish.py` | Shared SDK helper. `publish_model_and_register_output()` calls the typed logger (`dh.log_tvm_ir` / `dh.log_tvm_so`, falling back to generic `dh.log_model`), optionally adds a CONSUMES relationship, and PATCH-writes the Model key into `run.status.outputs[<key>]`. |

Two implementation details worth knowing:

- **`RUN_ID` is popped before importing `digitalhub`.** The SDK's run-context loader would try
  to load a run of kind `tvm+*:run`, but there is no Python builder for the TVM runtime (it's
  Java-only), which would raise `BuilderError`. `_dh_publish.py` keeps `RUN_ID` locally and
  updates status via a direct REST **read-modify-write PUT** of the whole run (with retry on
  409/412/5xx), because there is no PATCH endpoint.
- **Output keys.** Builders write `status.outputs.ir_module`; the compiler writes
  `status.outputs.compiled_so`. `TvmRuntime` reads exactly those keys.

---

## 9. Images and configuration

Configuration is bound from `src/main/resources/runtime-tvm.yml` (prefix `runtime.tvm`) into
`TvmProperties` by `TvmConfig`.

```yaml
runtime:
  tvm:
    user-id:     ${RUNTIME_TVM_USER_ID:${kubernetes.security.user}}
    group-id:    ${RUNTIME_TVM_GROUP_ID:${kubernetes.security.group}}
    home-dir:    ${RUNTIME_TVM_HOME_DIR:/shared}
    volume-size: ${RUNTIME_TVM_VOLUME_SIZE:4Gi}
    bucket:      ${RUNTIME_TVM_BUCKET:digitalhub}

    # format -> builder image for tvm+build
    builders:
      onnx:      ${RUNTIME_TVM_BUILDER_ONNX:ghcr.io/scc-digitalhub/tvm-toolkit:0.25}

    # image running compiler.py for tvm+compile (IR -> model.so)
    compiler:    ${RUNTIME_TVM_COMPILER:ghcr.io/scc-digitalhub/tvm-toolkit:0.25}

    # base serving image for tvm+serve (native tvm-serve; selectable)
    serve:       ${RUNTIME_TVM_SERVE:ghcr.io/scc-digitalhub/tvm-runtime-rust:0.25}

    entrypoint: classpath:/runtime-tvm/docker/entrypoint.sh
    builder-scripts:
      onnx:      classpath:/runtime-tvm/docker/builder_onnx.py
```

| Property | Used by | Notes |
|---|---|---|
| `builders.<format>` | `tvm+build` | Per-format builder image. Overridable per task via `image`. |
| `compiler` | `tvm+compile` | Image running `compiler.py`. Overridable per task via `image`. |
| `serve` | `tvm+serve` | Base serve image; the `tvm-so` Model is downloaded into it at deploy time. Overridable per task via `image`. |
| `user-id` / `group-id` / `home-dir` / `volume-size` | all | Pod identity + scratch volume defaults. |
| `bucket` | build/compile | S3 bucket for the output prefix. |
| `entrypoint` / `builder-scripts` | build/compile | Classpath locations of the injected pod scripts. |

Only **two container images** are involved: `tvm-toolkit` (build + compile) and a serve
runtime image (default `tvm-runtime-rust`). Both default to the GHCR `scc-digitalhub`
registry at tag `0.25`.

---

## 10. Model-centric serving

Serving is intentionally **model-centric** rather than image-centric:

- The **model is the artifact**: the compiled `tvm-so` Model lives on S3 and is downloaded at
  deploy time by an init container into `TVM_MODEL_DIR` (`<home>/model`). There is no baked
  per-model image and no Kaniko build in the serve (or compile) path.
- The **serve image is generic and swappable**. The same base image can serve any compiled
  model; you select it with `runtime.tvm.serve` (global default), `RUNTIME_TVM_SERVE` (env),
  or the task `image` field (per deployment).
  - Default: a **native Rust** runtime (`digitalhub-tvm-rust`) — loads `model.so`, runs the
    Relax VM, exposes Open Inference v2 (REST `8080` + gRPC `9000`).
  - Alternative: a **native Go** runtime shipped in `digitalhub-serverless` (available, same contract).
- **Contract** the serve image must honor: read `TVM_MODEL_DIR` (a folder with `model.so` +
  `metadata.json`) and `TVM_MODEL_NAME` (the served name), and expose Open Inference v2 on
  ports `8080`/`9000`. The base image's `ENTRYPOINT` starts the server, so the runner sets no
  command/args.
- Ports are **hardcoded** (`HTTP_PORT = 8080`, `GRPC_PORT = 9000`) and the Kubernetes
  Service is left to the framework — no custom `Service` object is created, mirroring
  `runtime-python`. A best-effort `<funcName>-latest` Service alias is added only when the run
  belongs to the function's current latest version (the lookup is wrapped in try/catch so an
  inconsistent "latest" index never fails the serve).

```
tvm+serve:run
   │
   ▼  TvmServeRunner.produce()  →  K8sServeRunnable
   │     image = task.image | runtime.tvm.serve   (swappable base)
   │     contextRef: init container ← s3://…/<tvm-so>/  →  <home>/model/
   │     env: TVM_MODEL_DIR=<home>/model  TVM_MODEL_NAME=<served_name>
   ▼
 ── Pod ─────────────────────────────────────────────
   init container   downloads model.so + metadata.json into <home>/model/
   serve image      ENTRYPOINT loads <home>/model → Relax VM → KServe v2
 ── /Pod ────────────────────────────────────────────
   Client → POST /v2/models/<served_name>/infer  (REST 8080 / gRPC 9000)
```

---

## 11. Examples

### 11.1 Register a function and run the three tasks

```yaml
# 1) Function: an ONNX model on S3
kind: tvm
spec:
  model: "s3://digitalhub/models/yolov8n.onnx"
  format: onnx        # or "auto" (detected from the .onnx extension)
```

```yaml
# 2) tvm+build task → produces a tvm-ir Model, sets function.spec.ir_model
kind: tvm+build
spec:
  simplify: true
  resources: { cpu: "2", mem: "4Gi" }
```

```yaml
# 3) tvm+compile task → produces a tvm-so Model, sets function.spec.so_model
kind: tvm+compile
spec:
  target_architecture: cpu      # cpu | x86 | arm64
  opt_level: 3
  # model_path omitted → uses function.spec.ir_model from the build
  resources: { cpu: "4", mem: "8Gi" }   # compile is memory-hungry
```

```yaml
# 4) tvm+serve task → deploys the tvm-so Model behind the serve image
kind: tvm+serve
spec:
  served_name: yolov8n
  service_type: NodePort
  # model_path omitted → uses function.spec.so_model from the compile
  # image omitted → uses runtime.tvm.serve (default: tvm-runtime-rust)
  resources: { cpu: "4" }
```

### 11.2 Inference request (Open Inference v2)

```
POST http://<host>:<port>/v2/models/yolov8n/infer
{
  "id": "req-1",
  "inputs": [
    { "name": "images", "datatype": "FP32", "shape": [1, 3, 640, 640], "data": [ ... ] }
  ]
}

→ { "model_name": "yolov8n",
    "outputs": [ { "name": "output0", "datatype": "FP32", "shape": [1, 84, 8400], "data": [ ... ] } ],
    "parameters": { "inference_time_ms": 42 } }
```

---

## 12. Parameter reference

Legend: **R** = required, *(x)* = default.

### 12.1 Function (`kind: tvm`)

| Field | Type | R / default | Effect |
|---|---|---|---|
| `model`  | string | **R** | Source: `s3://`, `https://`, `store://`, or a file path. |
| `format` | enum   | *(auto)* | `auto` / `onnx`. |
| `ir_model` | string | *(output)* | Set by `tvm+build`. |
| `so_model` | string | *(output)* | Set by `tvm+compile`. |

### 12.2 Task `tvm+build`

Consumes `function.spec.model` (downloaded to `<home>/input/`) + `format`. See §3.1 for the
full field list. Produces a `tvm-ir` Model (`algorithm = tvm-relax-ir`) → `function.spec.ir_model`.

### 12.3 Task `tvm+compile`

Consumes the IR (`model_path` **or** `function.spec.ir_model`) + `target_architecture`
*(default cpu)*. See §3.2. Produces a `tvm-so` Model (`algorithm = tvm-compiled-so`) →
`function.spec.so_model`.

### 12.4 Task `tvm+serve`

Consumes the compiled model (`model_path` **or** `function.spec.so_model`). See §3.3.
Produces a Deployment + Service (Open Inference v2, REST 8080 + gRPC 9000).

### 12.5 Common to all tasks (`K8sFunctionTaskBaseSpec`)

| Field | Effect |
|---|---|
| `resources` | `{cpu, mem, gpu, disk}` (strings, e.g. `{"cpu":"2","mem":"4Gi"}`). |
| `envs`      | Custom env `[{name,value}]` injected into the pod. |
| `secrets`   | Secret names → injected as env. |
| `volumes`   | Extra volumes. |
| `profile`   | Pod template/profile. |
| + standard k8s fields | node selector, tolerations, affinity, … |

> **Minimum viable input:** build = only `function.model`; compile = nothing beyond the
> defaults (IR comes from `ir_model`, target defaults to `cpu`); serve = nothing (the model
> comes from `so_model`, the image from `runtime.tvm.serve`).

---

## 13. Related projects

| Project | Role |
|---|---|
| **`digitalhub-core`** | This repository. Hosts `runtime-tvm` (the Java/Spring integration) alongside the other runtimes and the platform core. |
| **`tvm-toolkit`** (`ghcr.io/scc-digitalhub/tvm-toolkit`) | Builder/compiler image: Apache TVM + LLVM + native g++ + ONNX + the `digitalhub` SDK. Runs the build and compile Jobs. |
| **`digitalhub-tvm-rust`** (`ghcr.io/scc-digitalhub/tvm-runtime-rust`) | Default serve runtime: a native **Rust** server that loads `model.so`, runs the Relax VM, and exposes Open Inference v2 (REST + gRPC). No Python. |
| **`digitalhub-serverless`** | Home of the **native Go** serve runtime (alternative serve image, available) implementing the same `TVM_MODEL_DIR` / Open Inference v2 contract. |
| **`digitalhub` Python SDK** | Used inside the build/compile pods (`dh.log_tvm_ir` / `dh.log_tvm_so`) to create the Model entities and upload artifacts to S3. |

---

## Build and test

This module is built as part of `digitalhub-core`. Use **JDK 21** (Lombok annotation
processing is disabled by `javac` 23+, causing spurious `cannot find symbol` errors on
JDK 25):

```bash
export JAVA_HOME=~/.sdkman/candidates/java/21.0.4-graal
mvn -o -pl runtimes/runtime-tvm -DskipTests install
```
