# runtime-tvm

CORE converts ONNX or TFLite models to TVM Relax IR, compiles a Linux CPU or optional CUDA library
and serves it through Open Inference v2 (REST `8080`, gRPC `9000`). This module
contains the Java integration, form schemas and Python build/compile scripts.

| Action | Result | Execution |
| --- | --- | --- |
| `tvm+build` | `tvm-ir` Model; updates `function.spec.ir_model` | Job using the toolkit image |
| `tvm+compile` | `tvm-so` Model; records `run.status.outputs.compiled_so` | Job using the toolkit image |
| `tvm+serve` | Inference service | Deployment using the Go or Rust image |

For all options, defaults and tuning examples, see the
[TVM user guide](https://scc-digitalhub.github.io/docs/0.16/runtimes/tvm/).
The [SDK reference](https://scc-digitalhub.github.io/docs/0.16/sdk/reference/runtimes/tvm/overview/)
shows Python usage.

## Spec structure

| Entity | Settings |
| --- | --- |
| Function (`tvm`) | Required source `model`; optional `format` (`auto`, `onnx`, `tflite`); generated `ir_model` reference |
| Task (`tvm+build`, `tvm+compile`, `tvm+serve`) | Function reference and common Kubernetes settings: `profile`, `resources`, `envs`, `secrets`, `volumes` |
| Build Run (`tvm+build:run`) | `image` and ONNX conversion options |
| Compile Run (`tvm+compile:run`) | `model_path`, `target_architecture` or `target`, `tag`, `image`, `optimization`, `tuning` |
| Serve Run (`tvm+serve:run`) | Required versioned `model_path` from the selected Compile; `served_name`, `image`, `replicas`, `workers`, `service_type`, `service_name` |

Runs can override common Task settings. TVM options belong to the Run and are not
inherited from old Tasks or previous Runs. Triggers put them in `spec.template`.
The console shows them in the Run step; compile uses the **Advanced compilation**
(`optimization`) and **MetaSchedule tuning** (`tuning`) groups.

These are distinct choices:

- `profile`: platform scheduling/resource profile, such as a Raspberry Pi node.
- `target_architecture`: a named target preset from `runtime-tvm.yaml`; the console
  labels it **Target profile**.
- `target`: a standard LLVM or CUDA target object for one compile; use it with the preset
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

Copy `status.outputs.compiled_so` from the completed Compile into the Serve
Run's `model_path`. Start the Run and wait for `RUNNING`.

```yaml
kind: tvm+serve:run
spec:
  task: tvm+serve://my-project/<serve-task-id>
  model_path: store://my-project/model/tvm-so/<compiled-model-name>:<compiled-model-id>
  served_name: yolo
  workers: 1
  resources: {cpu: "4", mem: 2Gi}
```

Use the service address reported by CORE; query `/v2/models/yolo` for the input
signature and POST matching tensors to `/v2/models/yolo/infer`. `service_name`
adds a Kubernetes Service name; it does not change this model URL.

### Option groups

Build accepts `sort_onnx`, `simplify`, `target_opset`, `opset_override`, `strict_shape_inference`,
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

Compile uses Run `model_path`, otherwise the Function's `ir_model`. Build updates
only `ir_model` on the exact Function version. Each Compile publishes its own
`tvm-so` Model and records its key in `status.outputs.compiled_so`, without changing
the Function.

Serve requires a versioned `model_path` containing the selected Compile's
`status.outputs.compiled_so`. Compiles for AMD64, ARM64 or other targets can therefore
share one IR without replacing each other's serving reference. CORE does not select
the latest Compile or use a legacy Function `so_model`. Existing services keep their
selected Model; changing the served build requires an explicit new `model_path`.
Existing stored `so_model` fields are ignored, not migrated or deleted.

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
uploads. No DigitalHub SDK is installed or called by the toolkit. The publisher follows
the SDK's Model lifecycle: `CREATED` → `UPLOADING` → `READY`, recording file information
before marking the Model ready. Upload failures mark the Model `ERROR`. A `READY`
Model is distinct from a `COMPLETED` Run: wait for the Run to finish before continuing.

Output registration and the Build's update of `function.spec.ir_model` each allow
up to three attempts on transient errors. If the monitor advances a Run from
`PENDING` to `RUNNING` during an output update, the publisher rereads the Run
and retries the merge. Other HTTP 400 responses
remain errors. Missing outputs or an unconfirmed writeback fail the Run; registered
Models remain recoverable from the log and, when recorded, `run.status.outputs`.

Registration errors include CORE's validation message. A Model name can have multiple
versions of the same kind, but cannot be reused for a different kind in the same
project. Generated artifact names are normalized to CORE's Model naming rules.

Build updates to the Function are serialized within one CORE instance. Other CORE
instances and clients can still edit the Function concurrently.

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
| `jetson_orin_cuda` | CUDA `sm_87` with an AArch64 Linux GNU LLVM host; optional CUDA images required |

Toolkit images run on AMD64 or ARM64; both include linkers for AMD64, ARM64 and ARM32.
Only `amd64_native` restricts the compiler to AMD64. Go/Rust CPU runtime builds cover all
three destinations. CORE's default init image is AMD64/ARM64 only: ARM32 serving
through CORE also needs a compatible init image. Standalone ARM32 serving can mount
the Model directly. CPU instructions, ABI and TVM build must match the runtime.
CUDA uses separate AMD64/ARM64 toolkit and runtime images; CPU defaults and ARM32
support remain independent of NVIDIA dependencies.

### Optional CUDA FP32

Orin deployment with the current ARM64 base requires NVIDIA Container Toolkit
**1.19.1 or newer** on the host; verify with `nvidia-ctk --version`. Version 1.19.0
fails in the CUDA compatibility hook before the container entrypoint. See the
[NVIDIA fix](https://github.com/NVIDIA/nvidia-container-toolkit/releases/tag/v1.19.1).
JetPack's version alone does not verify this requirement; real hardware checks remain pending.


The Orin preset is a base compilation target, with no fabricated hardware limits:

```json
{"kind":"cuda","arch":"sm_87","host":{"kind":"llvm","mtriple":"aarch64-linux-gnu"}}
```

The nested LLVM host determines `target_triple` and the host ELF architecture.
The preset supplies `aarch64-linux-gnu-g++`; an explicit target needs its own
`optimization.cross_cc` when the native linker is unsuitable. AMD64 NVIDIA targets
use an explicit `host.mtriple: x86_64-linux-gnu` and the actual GPU architecture.
There is no separate top-level `target_host` or serving device field.

An AMD64 CUDA toolkit can compile for AMD64 or ARM64 without a GPU. This offline
Run explicitly opts into the toolkit's isolated driver stub:

```yaml
kind: tvm+compile:run
spec:
  task: tvm+compile://my-project/<compile-task-id>
  target_architecture: jetson_orin_cuda
  tag: orin-cuda
  profile: amd64-compiler # Replace with the configured profile for an AMD64 node.
  image: local/tvm-toolkit:0.26.0-cuda13.2.1-amd64
  envs:
    - name: LD_LIBRARY_PATH
      value: /opt/tvm/cuda-driver-stubs:/opt/tvm/lib:/usr/local/cuda/lib64
  resources: {cpu: "4", mem: 8Gi}
  optimization: {benchmark_runs: 0}
  tuning: {tuning_mode: "off"}
```

`local/...` names are development examples, not published CUDA tags. Build and
load them on your nodes or replace them with your own registry references.
`apply` can also compile offline using a compatible database. Never carry the
driver-stub loader path into real GPU measurement or serving processes.
Reusing a database requires the exact effective tuned target, including GPU limits
and LLVM host settings; the base preset alone may not match it.

For `tune`, query the actual measurement GPU with `tvm.target.Target.from_device`
and supply its positive `max_shared_memory_per_block`, `max_threads_per_block`
and `thread_warp_size` in an explicit target. Local tuning requires GPU access;
the existing RPC runner can measure on a remote GPU with its supplied profile.
CUDA builder workers default to one; explicit workers may build candidates in
parallel, while GPU measurements remain sequential. CPU cache flushing is rejected
for active CUDA tuning. `target_num_cores` only configures the LLVM host.

To serve a CUDA artifact, copy `status.outputs.compiled_so` from the selected CUDA
Compile into the Serve Run's `model_path`. Choose a CUDA-enabled Go or Rust image
and use `profile` and `resources.gpu` to schedule the service on a GPU node. The runtime
uses visible CUDA device 0 and refuses a missing CUDA backend/device without CPU
fallback. A CPU artifact can still run on a compatible Jetson ARM64 CPU.

CUDA benchmarks distinguish synchronized `input_resident` device timing from
`input_transfer_inclusive` host-to-device-plus-inference timing; neither includes
output transfer or HTTP. Client end-to-end latency is separate. A missing GPU
produces a skipped benchmark, not a device performance result.

The intended Jetson baseline is JetPack 7.2 / L4T 39.2 / CUDA 13.2.1 / Ubuntu 24.04.
Real Jetson/AMD64 NVIDIA inference, accuracy, tuning and performance are **NOT_RUN**.
See the authoritative [CUDA guide](https://scc-digitalhub.github.io/docs/0.16/runtimes/tvm/#cuda-fp32-optional)
for image builds, complete serve examples and vendor compatibility information.

### Add or override a preset

Mount a file, set
`SPRING_CONFIG_ADDITIONAL_LOCATION=file:/etc/digitalhub/runtime-tvm.yaml` and restart
CORE. Spring Boot loads the external file alongside the usual application settings;
no TVM-specific loader is needed. For example:

```yaml
runtime:
  tvm:
    targets:
      arm64_pi4:
        label: Raspberry Pi 4 - ARM 64-bit (Cortex-A72)
        target: >-
          {"kind": "llvm", "mtriple": "aarch64-linux-gnu", "mcpu": "cortex-a72",
           "mattr": ["+neon"], "num-cores": 4}
        linker:
          command: aarch64-linux-gnu-g++
          options: []
```

Select it with `target_architecture: arm64_pi4`. Each entry has:

| Field | Meaning |
| --- | --- |
| `label` | Console display text |
| `target` | String containing a non-empty JSON object of standard TVM options: `kind`, triple, CPU, features, etc. |
| `linker.command` | C++ compiler that links `model.so` |
| `linker.options` | Separate compiler arguments, such as `["--sysroot=/opt/sysroots/arm64"]` |
| `aliases` | Optional alternative names; use lowercase, unique names |
| `compiler-architecture` | Optional compile-node architecture constraint, as for `amd64_native` |

The external file can also be named `runtime-tvm.yaml`: it does not replace the
bundled property source. Omitted profiles and fields retain their bundled defaults.
Lists replace lower-priority lists; `aliases: []` or `linker.options: []` clears them.
Supplying `target` replaces the whole target, preventing old CPU features from
leaking into the override.
Run `optimization.cross_cc` and `optimization.cross_cc_options` override linker
settings; an explicit `[]` clears the arguments. Paths refer to the compile container.
An explicit Run `target` remains an object and does not inherit a preset's linker
settings. Only the administrator's YAML catalogue stores it as a JSON string.

`optimization.target_num_cores` takes precedence over the Job CPU request; either
one overrides `target.num-cores`. Without either, the target value remains, including
with `mcpu: native`. Native CPU detection supplies a core count only when none is
configured. Tuning and serving should use the same threads per worker.
For CUDA, this precedence applies to the nested LLVM host, independently of GPU limits.

Older API aliases remain configured: `cpu`/`llvm` → `auto`, `x86` → `amd64`,
`x86_v3` → `amd64_v3`, `x86_native` → `amd64_native`, `armv7l` → `arm32`.

When migrating older override files:

- Replace `RUNTIME_TVM_CONFIG` with `SPRING_CONFIG_ADDITIONAL_LOCATION` and unset
  the old variable. The dedicated TVM configuration path is no longer read.
- Replace each `target` YAML mapping with a JSON string as shown above.
- Move profile-level `cross-cc` to `linker.command`.
- Move profile-level `cross-cc-options` to `linker.options`.

These changes apply only to the administrator's catalogue. An explicit Run `target`
remains an object; `optimization.cross_cc`/`optimization.cross_cc_options` remain
Run options.
Invalid target JSON or unknown configuration-file properties under `runtime.tvm`
prevent CORE from starting. As in standard Spring Boot binding, unknown environment
variables and JVM system properties are ignored. External files use standard
configuration precedence and profile rules. A configured file must exist unless its
location starts with `optional:`.

### How targets reach TVM

1. `TvmConfig` loads the bundled YAML through the shared
   `commons.YamlPropertySourceFactory`; Spring Boot loads any additional configuration.
2. Each target is a JSON string, preserving lists, numbers and nested options together.
3. Standard `@ConfigurationProperties` binding populates `TvmProperties`, which
   validates the catalogue and resolves names/aliases.
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
| `SPRING_CONFIG_ADDITIONAL_LOCATION` | No additional TVM file; bundled YAML supplies the catalogue |
| `RUNTIME_TVM_BUILDER_ONNX`, `RUNTIME_TVM_BUILDER_TFLITE`, `RUNTIME_TVM_COMPILER` | `ghcr.io/scc-digitalhub/tvm-toolkit:0.26.0-r2` |
| `RUNTIME_TVM_SERVE` | `ghcr.io/scc-digitalhub/tvm-runtime-go:0.26.0-r3` |
| `RUNTIME_TVM_HOME_DIR` | `/shared` |
| `RUNTIME_TVM_VOLUME_SIZE` | `4Gi` |
| `RUNTIME_TVM_USER_ID`, `RUNTIME_TVM_GROUP_ID` | Platform Kubernetes user/group |

Each Run can override its image. Set `RUNTIME_TVM_SERVE` to
`ghcr.io/scc-digitalhub/tvm-runtime-rust:0.26.0-r2` to use Rust globally.
These image revisions bundle Apache TVM 0.26.0; the `-rN` suffix identifies the
DigitalHub image revision. Storage uses the platform's default store; there is
no TVM bucket setting.

## Code map and development

| Location | Responsibility |
| --- | --- |
| `TvmRuntime` | Run lifecycle and Function reference updates |
| `runners/` | Build/compile Jobs and serve Deployment/Service |
| `specs/`, `src/main/resources/runtime-tvm/*/uiSchema.json` | Function, Task, Run and Model schemas |
| `config/` | YAML catalogue loading and binding |
| `scripts/build_onnx.py`, `build_tflite.py` | Source import to Relax IR |
| `scripts/compile_model.py`, `toolchain.py` | Target resolution, compilation and linking |
| `scripts/tuning.py`, `benchmark.py` | MetaSchedule and synchronized CPU/CUDA inference timing |
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
SDK validation reads the exported TVM schemas in `schemas/specs/` from CORE's `main`
branch. Keep those exports aligned with the specs and merge CORE before dependent
SDK changes.
The public [tutorial](https://scc-digitalhub.github.io/docs/0.16/tutorials/tvm/intro/)
contains a standalone inference client.

Related components: [toolkit](https://github.com/scc-digitalhub/digitalhub-tvm-toolkit),
[Go runtime](https://github.com/scc-digitalhub/digitalhub-serverless/tree/main/pkg/processor/runtime/tvm),
[Rust runtime](https://github.com/scc-digitalhub/digitalhub-tvm-rust),
[SDK TVM extension](https://github.com/scc-digitalhub/digitalhub-sdk-runtime-tvm).

## Copyright and license

Copyright © 2025 DSLab – Fondazione Bruno Kessler and individual contributors.

This project is licensed under the Apache License, Version 2.0.
You may not use this file except in compliance with the License. Ownership of contributions remains with the original authors and is governed by the terms of the Apache 2.0 License, including the requirement to grant a license to the project.

### ONNX normalization and exported contracts

The ONNX builder defaults `sort_onnx` to `false`. Setting it to `true` uses `onnx-ir`'s
standard topological sort before shape inference and again after transformations,
including automatic simplification. ONNX validation remains enabled when sorting is
disabled, so an unordered graph is rejected. The `simplify` option is independent.
If sorting alone fixes a rejected graph, the build error recommends `sort_onnx=True`.
Other invalid graphs retain the ONNX checker's reason.
Validation does not rewrite the source Model. Input dtypes reach the
TVM importer unchanged; unknown dimensions remain `-1` in metadata, and zero
length dimensions remain zero. Unsupported tensor dtypes raise an error.
FP16/BFLOAT16 server inputs and outputs remain unsupported; accepting an ONNX dtype
does not qualify its internal kernels or mixed precision accuracy on a GPU.

For numerical comparisons, check the execution precision as well as the model's
input and output dtypes. ONNX Runtime CPU can promote internal FP16 operators to
FP32 and remove intermediate casts, even with graph optimizations disabled
([ORT 1.31.1 cast transformation](https://github.com/microsoft/onnxruntime/blob/v1.31.1/onnxruntime/core/optimizer/insert_cast_transformer.cc)).
Compare matching precision or execution graphs at the original tolerance. Keep
any explicitly converted FP32 variant as a separate Model; the builder preserves
the source model's precision.

Compilation checks that reshapes preserve the number of elements, including
symbolic dimensions and runtime shape tensors with a known rank. Incompatible
sizes raise a native TVM error; subsequent valid requests remain usable. Unknown
reshape ranks are rejected during compilation. Runtime shape tensors containing
`-1` remain unsupported by pinned TVM; compile-time `-1` inference is supported.
Recompile existing artifacts to include this validation.

TVM options belong to the Run; Tasks contain common Kubernetes settings. The six
TVM Task/Run exports must accompany the runtime changes. Static exports leave
Kubernetes profile names open because deployments configure them; live schemas
retain the configured choices. SDK CI uses CORE `main` by default, so local contract
success does not make that CI compatible before the matching CORE schemas are integrated.
