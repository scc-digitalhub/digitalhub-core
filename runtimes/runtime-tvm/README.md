# runtime-tvm

CORE converts ONNX or TFLite models to TVM Relax IR, compiles a Linux CPU library
and serves it through Open Inference v2 (REST `8080`, gRPC `9000`). This module
contains the Java integration, form schemas and Python build/compile scripts.

| Action | Result | Execution |
| --- | --- | --- |
| `tvm+build` | `tvm-ir` Model; updates `function.spec.ir_model` | Job using the toolkit image |
| `tvm+compile` | `tvm-so` Model; updates `function.spec.so_model` | Job using the toolkit image |
| `tvm+serve` | Inference service | Deployment using the Go or Rust image |

For all options, defaults and tuning examples, see the
[TVM user guide](https://scc-digitalhub.github.io/docs/0.16/runtimes/tvm/).
The [SDK reference](https://scc-digitalhub.github.io/docs/0.16/sdk/reference/runtimes/tvm/overview/)
shows Python usage.

## Spec structure

| Entity | Settings |
| --- | --- |
| Function (`tvm`) | Required source `model`; optional `format` (`auto`, `onnx`, `tflite`); generated `ir_model` and `so_model` references |
| Task (`tvm+build`, `tvm+compile`, `tvm+serve`) | Function reference and common Kubernetes settings: `profile`, `resources`, `envs`, `secrets`, `volumes` |
| Build Run (`tvm+build:run`) | `image` and ONNX conversion options |
| Compile Run (`tvm+compile:run`) | `model_path`, `target_architecture` or `target`, `tag`, `image`, `optimization`, `tuning` |
| Serve Run (`tvm+serve:run`) | `model_path`, `served_name`, `image`, `replicas`, `workers`, `service_type`, `service_name` |

Runs can override common Task settings. TVM options belong to the Run and are not
inherited from old Tasks or previous Runs. Triggers put them in `spec.template`.
The console shows them in the Run step; compile uses the **Advanced compilation**
(`optimization`) and **MetaSchedule tuning** (`tuning`) groups.

These are distinct choices:

- `profile`: platform scheduling/resource profile, such as a Raspberry Pi node.
- `target_architecture`: a named CPU preset from `runtime-tvm.yaml`.
- `target`: a standard LLVM target object for one compile; use it with the preset
  omitted or set to `auto`.

### Quick start

Upload a source Model, then create a Function:

```yaml
kind: tvm
name: yolo
spec:
  model: store://my-project/model/onnx/yolo:<model-id>
  format: auto
```

Create a Task for each action. A compile Task, for example:

```yaml
kind: tvm+compile
spec:
  function: tvm://my-project/yolo:<function-id>
  resources: {cpu: "4", mem: 8Gi}
```

Start a build Run and wait for `COMPLETED`:

```yaml
kind: tvm+build:run
spec:
  task: tvm+build://my-project/<build-task-id>
  simplify: true
```

Then compile, and wait for `COMPLETED`:

```yaml
kind: tvm+compile:run
spec:
  task: tvm+compile://my-project/<compile-task-id>
  target_architecture: amd64
  tag: amd64
  optimization:
    opt_level: 3
    benchmark_runs: 10
  tuning:
    tuning_mode: "off"
```

Finally start a serve Run and wait for `RUNNING`:

```yaml
kind: tvm+serve:run
spec:
  task: tvm+serve://my-project/<serve-task-id>
  served_name: yolo
  workers: 1
  resources: {cpu: "4", mem: 2Gi}
```

Use the service address reported by CORE; query `/v2/models/yolo` for the input
signature and POST matching tensors to `/v2/models/yolo/infer`. `service_name`
adds a Kubernetes Service name; it does not change this model URL.

### Option groups

Build accepts `simplify`, `target_opset`, `opset_override`, `strict_shape_inference`,
`data_prop`, `keep_params_in_input` and `sanitize_input_names`. They apply to ONNX;
TFLite conversion reads its configuration from the model.

Compile groups contain:

| Group | Fields |
| --- | --- |
| `optimization` | `target_num_cores`, `opt_level`, `cross_cc`, `cross_cc_options`, `exec_mode`, `relax_pipeline`, `tir_pipeline`, `system_lib`, `benchmark_runs`, `params_path` |
| `tuning` | `tuning_mode`, `tuning_model_path`, `tuning_trials`, `max_trials_per_task`, `tuning_trials_per_iter`, `tuning_ops`, `allow_partial_tuning` |
| `tuning` measurement settings | `tuning_runner`, `tuning_workers`, `tuning_seed`, `tuning_number`, `tuning_repeat`, `tuning_min_repeat_ms`, `tuning_alloc_repeat`, `tuning_enable_cpu_cache_flush`, `tuning_builder_timeout_sec`, `tuning_runner_timeout_sec` |
| `tuning` RPC settings | `rpc_tracker_host`, `rpc_tracker_port`, `rpc_tracker_key`, `rpc_session_timeout_sec` |

Flat run options remain accepted; grouped values win if both forms specify a field.
CORE saves the grouped form. Tuning defaults to `off`: inactive values are preserved,
but not converted, validated or used. `apply` uses the database and coverage settings;
`tune` also uses search/measurement settings. RPC fields are active only for `tune`
with `tuning_runner: rpc`; that mode ignores the local runner timeout.

## Model references and files

Compile uses Run `model_path`, otherwise the Function's `ir_model`. Serve uses Run
`model_path`, otherwise its `so_model`. These are references on the **exact Function
version** associated with the Run; CORE does not search compile history.

Build changes only `ir_model`, compile changes only `so_model`, without creating a
Function version. A new build keeps the old SO usable. With concurrent compiles,
the last successful reference update wins. Set a versioned `model_path` to select
a specific architecture/build. Existing services keep their selected Model; clear
`model_path` when copying a serve Run if the new Run should use the Function default.

| Model | Files |
| --- | --- |
| `tvm-ir` | `model.relax.json`, `metadata.json`, optional readable `model.relax.ir` and detached `params.bin` |
| `tvm-so` | `model.so`, `metadata.json`, plus `tuning/` when tuning is used |

Tensor metadata includes `name`, `dtype`, `shape` and optional quantization fields
`scale`, `zero_point`, `quantized_dimension`. Compiled metadata records the effective
`target`, `target_triple`, TVM version/commit, compiler options, tuning and benchmarks.
`target_requested`, when present, is the target before Python resolves host defaults
and `mcpu: native`; it is diagnostic, not another Run option.

Publication uses direct HTTP calls to CORE and signed storage URLs, including multipart
uploads. No DigitalHub SDK is installed or called by the toolkit. Output registration
and Function writeback each retry transient failures up to three attempts. Missing
outputs or an unconfirmed writeback fail the Run; uploaded Models remain recoverable
from the log and, if registered, `run.status.outputs`.

Writebacks are serialized within one CORE instance. This does not provide distributed
locking against other CORE instances or external Function edits.

## Target configuration

The catalogue is [runtime-tvm.yaml](src/main/resources/runtime-tvm.yaml).

| Preset | Model requirements |
| --- | --- |
| `auto` | Generic CPU code for the compile node's architecture, not native optimization |
| `amd64` | Intel/AMD 64-bit, full x86-64-v2 feature set |
| `amd64_v3` | Intel/AMD 64-bit, full x86-64-v3 feature set (including AVX2/FMA) |
| `amd64_native` | CPU features of the AMD64 compile node |
| `arm64` | AArch64 Linux, for example Pi 3/4/5 with a 64-bit OS |
| `arm64_pi5` | Cortex-A76, as on Pi 5 with a 64-bit OS |
| `arm32` | ARMv7 Linux, hard-float ABI and NEON |

Toolkit images run on AMD64 or ARM64; both include linkers for AMD64, ARM64 and ARM32.
Only `amd64_native` restricts the compiler to AMD64. Go/Rust runtime builds cover all
three destinations. CORE's default init image is AMD64/ARM64 only: ARM32 serving
through CORE also needs a compatible init image. Standalone ARM32 serving can mount
the Model directly. CPU instructions, ABI and TVM build must match the runtime.
CUDA/GPU execution is not integrated.

### Add or override a preset

Mount a file, set `RUNTIME_TVM_CONFIG=file:/etc/digitalhub/runtime-tvm.yaml` and
restart CORE. For example:

```yaml
runtime:
  tvm:
    targets:
      arm64_pi4:
        label: Raspberry Pi 4 - ARM 64-bit (Cortex-A72)
        target:
          kind: llvm
          mtriple: aarch64-linux-gnu
          mcpu: cortex-a72
          mattr: [+neon]
          num-cores: 4
        linker:
          command: aarch64-linux-gnu-g++
          options: []
```

Select it with `target_architecture: arm64_pi4`. Each entry has:

| Field | Meaning |
| --- | --- |
| `label` | Console display text |
| `target` | Non-empty mapping of standard TVM options: `kind`, triple, CPU, features, etc. |
| `linker.command` | C++ compiler that links `model.so` |
| `linker.options` | Separate compiler arguments, such as `["--sysroot=/opt/sysroots/arm64"]` |
| `aliases` | Optional alternative names; use lowercase, unique names |
| `compiler-architecture` | Optional compile-node architecture constraint, as for `amd64_native` |

Omitted profiles and fields retain their bundled defaults. Supplying `target`
replaces the whole target, preventing old CPU features from leaking into the override.
Run `optimization.cross_cc` and `optimization.cross_cc_options` override linker
settings; an explicit `[]` clears the arguments. Paths refer to the compile container.
An explicit Run `target` does not inherit a preset's linker settings.

`optimization.target_num_cores` takes precedence over the Job CPU request; either
one overrides `target.num-cores`. Without either, the target value remains, including
with `mcpu: native`. Native CPU detection supplies a core count only when none is
configured. Tuning and serving should use the same threads per worker.

Older API aliases remain configured: `cpu`/`llvm` → `auto`, `x86` → `amd64`,
`x86_v3` → `amd64_v3`, `x86_native` → `amd64_native`, `armv7l` → `arm32`.
Old YAML configuration forms are rejected: expand quoted JSON targets into mappings
and move `cross-cc`/`cross-cc-options` to `linker.command`/`linker.options`.

### How targets reach TVM

1. `TvmConfig` loads the bundled YAML and, if configured, the external override.
2. `TvmYamlPropertySourceFactory` uses Spring's `YamlProcessor` to read each file
   once. It stores each target mapping as JSON so Spring binding preserves lists,
   numbers and nested options as one complete value.
3. `TvmProperties` binds the catalogue and resolves names/aliases.
   `TvmCompileSchemaModule` uses the same catalogue for the console choices.
4. `TvmCompileRunner` selects the preset or explicit target and sends it as
   `TVM_TARGET`, with the linker and thread options, to the Job.
5. `compile_model.py` resolves the effective target, checks the toolchain, calls
   `relax.build` and links `model.so` through `export_library`.
6. Go/Rust read the Model metadata, not the YAML catalogue or preset name.

Adding a preset needs no Java/SDK enumeration change. Adding an unsupported CPU
family still needs its LLVM backend, linker/sysroot, runtime build and tests.

## Platform configuration

| CORE environment variable | Default |
| --- | --- |
| `RUNTIME_TVM_CONFIG` | No external file; bundled YAML only |
| `RUNTIME_TVM_BUILDER_ONNX`, `RUNTIME_TVM_BUILDER_TFLITE`, `RUNTIME_TVM_COMPILER` | `ghcr.io/scc-digitalhub/tvm-toolkit:0.26.0` |
| `RUNTIME_TVM_SERVE` | `ghcr.io/scc-digitalhub/tvm-runtime-go:0.26.0` |
| `RUNTIME_TVM_HOME_DIR` | `/shared` |
| `RUNTIME_TVM_VOLUME_SIZE` | `4Gi` |
| `RUNTIME_TVM_USER_ID`, `RUNTIME_TVM_GROUP_ID` | Platform Kubernetes user/group |

Each Run can override its image. Set `RUNTIME_TVM_SERVE` to the Rust image to use
Rust globally. Storage uses the platform's default store; there is no TVM bucket setting.

## Code map and development

| Location | Responsibility |
| --- | --- |
| `TvmRuntime` | Run lifecycle and Function reference updates |
| `runners/` | Build/compile Jobs and serve Deployment/Service |
| `specs/`, `src/main/resources/runtime-tvm/*/uiSchema.json` | Function, Task, Run and Model schemas |
| `config/` | YAML catalogue loading and binding |
| `scripts/build_onnx.py`, `build_tflite.py` | Source import to Relax IR |
| `scripts/compile_model.py`, `toolchain.py` | Target resolution, compilation and linking |
| `scripts/tuning.py`, `benchmark.py` | MetaSchedule and CPU inference timing |
| `scripts/publish.py`, `common.py`, `entrypoint.sh` | Artifact publication and shared Job utilities |

Script paths above are relative to `src/main/resources/runtime-tvm/`. CORE mounts
these scripts into Jobs; the toolkit image supplies TVM, LLVM and dependencies.
An init container downloads Models for serving. TVM's local `InferenceV2Service`
exposes inference metadata using the base URL from the platform's Kubernetes
service metadata. Other runtimes keep their own inference metadata classes.

Build from the repository root with **JDK 21**:

```bash
mvn -pl runtimes/runtime-tvm -am install -DskipTests
```

Local tests under `src/test/` and `test_infer/` are not distributed in this repository.
The public [tutorial](https://scc-digitalhub.github.io/docs/0.16/tutorials/tvm/intro/)
contains a standalone inference client.

Related components: [toolkit](https://github.com/scc-digitalhub/digitalhub-tvm-toolkit),
[Go runtime](https://github.com/scc-digitalhub/digitalhub-serverless/tree/serverless-tvm/pkg/processor/runtime/tvm),
[Rust runtime](https://github.com/scc-digitalhub/digitalhub-tvm-rust),
[SDK TVM extension](https://github.com/scc-digitalhub/digitalhub-sdk-runtime-tvm).

## Copyright and license

Copyright © 2025 DSLab – Fondazione Bruno Kessler and individual contributors.

This project is licensed under the Apache License, Version 2.0.
You may not use this file except in compliance with the License. Ownership of contributions remains with the original authors and is governed by the terms of the Apache 2.0 License, including the requirement to grant a license to the project.
