# runtime-tvm

Runs **[Apache TVM](https://tvm.apache.org/)** models on DigitalHub. It takes an ONNX or
TFLite model, compiles it into a native library for a CPU target (optionally tuned for
speed) and serves it with the **Open Inference Protocol v2** over REST and gRPC.

```
 source model          tvm+build            tvm+compile            tvm+serve
 onnx / tflite  ────►  tvm-ir Model  ────►  tvm-so Model  ────►  REST :8080 · gRPC :9000
```

| Task          | What it does                              | Runs as                             | Result                                      |
| ------------- | ----------------------------------------- | ----------------------------------- | ------------------------------------------- |
| `tvm+build`   | converts the source model to TVM Relax IR | Kubernetes Job, `tvm-toolkit` image | Model of kind `tvm-ir`, saved in `ir_model` |
| `tvm+compile` | compiles the IR into `model.so`           | Kubernetes Job, `tvm-toolkit` image | Model of kind `tvm-so`, saved in `so_model` |
| `tvm+serve`   | serves the compiled model                 | Deployment + Service, serve image   | Open Inference v2 endpoint                  |

The tasks are chained through the function: build writes `ir_model`, compile reads it and
writes `so_model`, serve reads that. Any task can also be pointed at a specific Model with
`model_path`, so one IR can be compiled for many targets.

---

## Quick start

**1. Upload the model** as a Model of kind `onnx` (or `tflite`):

```yaml
kind: onnx
name: yolov8n
spec:
  path: s3://digitalhub/models/yolov8n.onnx
```

**2. Create the function:**

```yaml
kind: tvm
name: yolov8n
spec:
  model: store://my-project/model/onnx/yolov8n
```

**3. Run the three tasks**, one after the other:

```yaml
# build: source -> tvm-ir
kind: tvm+build
spec:
  resources: { cpu: "2", mem: 4Gi }
```

```yaml
# compile: tvm-ir -> tvm-so
kind: tvm+compile
spec:
  target_architecture: x86_v3
  resources: { cpu: "4", mem: 8Gi }
```

```yaml
# serve: tvm-so -> endpoint
kind: tvm+serve
spec:
  service_type: NodePort
  resources: { cpu: "4" }
```

**4. Call the model:**

```bash
curl -X POST http://<host>:<port>/v2/models/yolov8n/infer \
  -H 'Content-Type: application/json' \
  -d '{"inputs":[{"name":"images","datatype":"FP32","shape":[1,3,640,640],"data":[...]}]}'
```

---

## Function `tvm`

| Field      | Default | Description                                                                                  |
| ---------- | ------- | -------------------------------------------------------------------------------------------- |
| `model`    | —       | **Required.** Source model: the `store://` key of a Model, or an `s3://` / `https://` path.  |
| `format`   | `auto`  | `auto`, `onnx` or `tflite`. `auto` uses the kind of the Model, otherwise the file extension. |
| `ir_model` | —       | Set by `tvm+build`: the `tvm-ir` Model to compile.                                           |
| `so_model` | —       | Set by `tvm+compile`: the `tvm-so` Model to serve.                                           |

## Model kinds

**Source models**, uploaded by the user. All fields are optional and only describe the
model: the builders read the real signature from the file. A generic `model` Model still
works, with the format taken from the file extension.

| Kind     | Fields                                                            |
| -------- | ----------------------------------------------------------------- |
| `onnx`   | `inputs`, `outputs`, `parameters`, `opset` (opset used at export) |
| `tflite` | `inputs`, `outputs`, `parameters`                                 |

**Produced models**, created by the tasks:

| Kind     | Files                                                                                          | Fields                                                                                        |
| -------- | ---------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| `tvm-ir` | `model.relax.json`, `model.relax.ir` (readable dump), `metadata.json`, `params.bin` (optional) | `entry`, `inputs`, `outputs`, `source_format`, `keep_params_in_input`, `sanitize_input_names` |
| `tvm-so` | `model.so`, `metadata.json`, `tuning/` (tuned models only)                                     | `entry`, `inputs`, `outputs`, `target`, `opt_level`, `manifest`                               |

Each tensor in `inputs` / `outputs` has `name`, `dtype` and `shape`. Quantized tensors
(`int8` / `uint8`) also have `scale`, `zero_point` and, for per-axis quantization,
`quantized_dimension` (`real = (q - zero_point) * scale`).

---

## Options

Every option is optional unless marked as required.

### Common to all tasks

| Option      | Description                                                                           |
| ----------- | ------------------------------------------------------------------------------------- |
| `resources` | `cpu`, `mem`, `gpu`, `disk`. The CPU request also sets the thread counts (see below). |
| `envs`      | Extra environment variables for the container.                                        |
| `secrets`   | Secrets exposed to the container as environment variables.                            |
| `volumes`   | Extra volumes to mount.                                                               |
| `profile`   | Resource profile defined by the platform.                                             |
| `image`     | Use another image for this run instead of the configured one.                         |

### `tvm+build`

The conversion options apply to **ONNX** only; the TFLite builder ignores them.

| Option                   | Default | Description                                                                                                     |
| ------------------------ | ------- | --------------------------------------------------------------------------------------------------------------- |
| `simplify`               | `false` | Simplify the graph with onnxsim before converting.                                                              |
| `target_opset`           | —       | Convert the model to this opset first. Fails when ONNX has no converter for an operator (e.g. `Split` 18 → 17). |
| `opset_override`         | model   | Opset the TVM importer uses instead of the one declared by the model.                                           |
| `strict_shape_inference` | `false` | Strict ONNX shape inference: an error skips the whole inference (logged) instead of single nodes.               |
| `data_prop`              | `false` | Propagate constant values during shape inference, to resolve more shapes.                                       |
| `keep_params_in_input`   | `false` | Keep the weights out of the graph, in `params.bin`, instead of embedding them as constants.                     |
| `sanitize_input_names`   | `true`  | Rewrite the input names into valid identifiers.                                                                 |

### `tvm+compile`

**Model and build**

| Option                | Default                | Description                                                                                                                                |
| --------------------- | ---------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `model_path`          | function `ir_model`    | `store://` key of the `tvm-ir` Model to compile.                                                                                           |
| `target_architecture` | `cpu`                  | Hardware target, see [Targets](#targets).                                                                                                  |
| `target_num_cores`    | `resources.cpu`        | Cores the generated code is optimized for. Use the same number when serving.                                                               |
| `opt_level`           | `3`                    | TVM optimization level, 0 to 3.                                                                                                            |
| `exec_mode`           | `bytecode`             | How the model graph runs: `bytecode` (interpreted by the Relax VM) or `compiled` (native code).                                            |
| `relax_pipeline`      | `default`              | Name of the Relax optimization pipeline.                                                                                                   |
| `tir_pipeline`        | `default`              | Name of the TIR optimization pipeline.                                                                                                     |
| `cross_cc`            | set by target          | Cross C++ compiler. Filled in for the ARM targets; set it only to use another one.                                                         |
| `system_lib`          | `false`                | Advanced: build a system-lib module. It cannot be served by the serve images.                                                              |
| `params_path`         | `params.bin` of the IR | `params.bin` to embed, as a path inside the pod.                                                                                           |
| `tag`                 | `so`                   | The Model is named `<function>-<tag>`; the tag is also saved in `metadata.json`.                                                           |
| `benchmark_runs`      | `10`                   | Timed inferences of the finished `model.so`, saved in `metadata.json` (`benchmark`). `0` turns it off. Skipped for cross-compiled targets. |

**Tuning** (MetaSchedule, see [Making models fast](#making-models-fast))

| Option                          | Default         | Description                                                                                      |
| ------------------------------- | --------------- | ------------------------------------------------------------------------------------------------ |
| `tuning_mode`                   | `off`           | `off`, `tune` (search the best code) or `apply` (reuse an earlier search).                       |
| `tuning_trials`                 | —               | Total number of candidates to measure. **Required** with `tune`.                                 |
| `max_trials_per_task`           | `16`            | Most candidates a single task may use.                                                           |
| `tuning_trials_per_iter`        | `64`            | Candidates measured for each task in one round.                                                  |
| `tuning_ops`                    | all             | Tune only the tasks whose name contains one of these words, e.g. `[conv2d]`.                     |
| `tuning_model_path`             | —               | Earlier `tvm-so` Model: `tune` continues its search, `apply` uses it. **Required** with `apply`. |
| `tuning_workers`                | `resources.cpu` | Candidates compiled in parallel.                                                                 |
| `tuning_seed`                   | `0`             | Random seed, for repeatable searches.                                                            |
| `tuning_number`                 | `3`             | Runs averaged in one measurement.                                                                |
| `tuning_repeat`                 | `1`             | Measurements taken for each candidate.                                                           |
| `tuning_min_repeat_ms`          | `100`           | Minimum length of one measurement, in milliseconds.                                              |
| `tuning_alloc_repeat`           | `1`             | Input buffers rotated between measurements, to reduce cache effects.                             |
| `tuning_enable_cpu_cache_flush` | `false`         | Flush the CPU caches before each measurement.                                                    |
| `tuning_builder_timeout_sec`    | `30`            | Time limit to compile one candidate.                                                             |
| `tuning_runner_timeout_sec`     | `30`            | Time limit to run one candidate.                                                                 |
| `allow_partial_tuning`          | `false`         | Accept a budget or a database that does not cover every task. For quick tests only.              |

**Tuning on a device**

| Option                    | Default | Description                                                                     |
| ------------------------- | ------- | ------------------------------------------------------------------------------- |
| `tuning_runner`           | `local` | `local` measures on the compile Job, `rpc` on a device registered with TVM RPC. |
| `rpc_tracker_host`        | —       | Host of the TVM RPC tracker. **Required** with `rpc`.                           |
| `rpc_tracker_port`        | —       | Port of the TVM RPC tracker. **Required** with `rpc`.                           |
| `rpc_tracker_key`         | —       | Key the device is registered with. **Required** with `rpc`.                     |
| `rpc_session_timeout_sec` | `60`    | Time limit of one RPC session.                                                  |

Settings that cannot work are rejected when the run is submitted: for example `tune`
without `tuning_trials`, `apply` without `tuning_model_path`, or local tuning of an ARM
target.

### `tvm+serve`

| Option         | Default             | Description                                                                   |
| -------------- | ------------------- | ----------------------------------------------------------------------------- |
| `model_path`   | function `so_model` | `store://` key of the `tvm-so` Model to serve.                                |
| `served_name`  | function name       | Model name in the URLs, `/v2/models/<served_name>`.                           |
| `replicas`     | `1`                 | Number of pods.                                                               |
| `workers`      | `1`                 | Inferences run in parallel in each pod; each worker loads its own model copy. |
| `service_type` | `ClusterIP`         | `ClusterIP`, `NodePort` or `LoadBalancer`.                                    |
| `service_name` | —                   | Extra Service name, `<function>-<service_name>`.                              |

The serve pod exposes REST on `8080` and gRPC on `9000`. When the task requests CPUs,
each worker gets `resources.cpu / workers` TVM threads (`TVM_NUM_THREADS`); set
`TVM_NUM_THREADS` in `envs` to choose it yourself.

The serve images only load models compiled with the same TVM build and for their CPU
architecture: recompile the models after upgrading the images.

## Targets

| `target_architecture` | Runs on                                                         |
| --------------------- | --------------------------------------------------------------- |
| `cpu`                 | Generic code for the architecture of the compile Job.           |
| `x86`                 | x86-64 CPUs with SSE4.2 (x86-64-v2, about 2009 and newer).      |
| `x86_v3`              | x86-64 CPUs with AVX2 (Intel Haswell, AMD Excavator and newer). |
| `x86_native`          | Exactly the CPU model of the compile Job.                       |
| `arm64`               | Any 64-bit ARM (aarch64).                                       |
| `arm64_pi5`           | Raspberry Pi 5 (Cortex-A76, 4 cores).                           |
| `armv7l`              | 32-bit ARM hard-float (Raspberry Pi OS 32-bit).                 |

The more specific the target, the faster the code, but it only runs on that kind of CPU.
The serve images exist for `linux/amd64`, `linux/arm64` and `linux/arm/v7`.

---

## Making models fast

On CPU, TVM has no ready-made optimized code for the operators: an untuned model works, but
it can be many times slower than ONNX Runtime. Tuning searches the best code for each
operator on the real hardware and keeps the result in the `tuning/` folder of the model.
The weights are also rearranged for that code.

1. **Tune once**, on the same kind of CPU used for serving:

   ```yaml
   kind: tvm+compile
   spec:
     target_architecture: x86_native
     target_num_cores: 4
     tuning_mode: tune
     tuning_trials: 8000
     max_trials_per_task: 256
     resources: { cpu: "4", mem: 8Gi }
   ```

2. **Reuse the result** to recompile the same IR for the same target, without measuring
   again:

   ```yaml
   kind: tvm+compile
   spec:
     target_architecture: x86_native
     target_num_cores: 4
     tuning_mode: apply
     tuning_model_path: store://my-project/model/tvm-so/yolov8n-so:<id>
   ```

Tips:

- **Budget.** Give at least `tasks × min(64, max_trials_per_task)` trials, so every task is
  measured once. The log of the compile prints the number of tasks and this minimum; 64 to
  256 trials per task is a good range.
- **Threads.** Serve with the same number of threads per worker as `target_num_cores`.
- **Compare.** `metadata.json` of every compiled model has a `benchmark` section with its
  timings.
- **ARM devices.** A compile Job on x86 cannot measure ARM code: use `tuning_runner: rpc`
  with the device, or `apply` a database tuned on the device.

---

## Configuration

Set on CORE with environment variables:

| Variable                                       | Default                                        | Description                                                              |
| ---------------------------------------------- | ---------------------------------------------- | ------------------------------------------------------------------------ |
| `RUNTIME_TVM_BUILDER_ONNX`                     | `ghcr.io/scc-digitalhub/tvm-toolkit:0.26.0`    | Image of `tvm+build` for ONNX.                                           |
| `RUNTIME_TVM_BUILDER_TFLITE`                   | `ghcr.io/scc-digitalhub/tvm-toolkit:0.26.0`    | Image of `tvm+build` for TFLite.                                         |
| `RUNTIME_TVM_COMPILER`                         | `ghcr.io/scc-digitalhub/tvm-toolkit:0.26.0`    | Image of `tvm+compile`.                                                  |
| `RUNTIME_TVM_SERVE`                            | `ghcr.io/scc-digitalhub/tvm-runtime-go:0.26.0` | Image of `tvm+serve` (Go; the Rust image `tvm-runtime-rust` also works). |
| `RUNTIME_TVM_HOME_DIR`                         | `/shared`                                      | Working folder inside the pods.                                          |
| `RUNTIME_TVM_VOLUME_SIZE`                      | `4Gi`                                          | Size of the working volume.                                              |
| `RUNTIME_TVM_USER_ID` / `RUNTIME_TVM_GROUP_ID` | platform user and group                        | User and group the pods run as.                                          |

The files are stored in the default S3 store of the platform; the runtime has no bucket
setting of its own. The defaults live in `src/main/resources/runtime-tvm.yml`.

## How it works

- **Java** (`src/main/java`): `TvmRuntime` receives the runs and writes `ir_model` /
  `so_model` back to the function; one runner per task (`TvmBuildRunner`,
  `TvmCompileRunner`, `TvmServeRunner`) turns a run into a Kubernetes Job or Deployment;
  `specs/` holds the function, task and Model kind definitions.
- **Pod scripts** (`src/main/resources/runtime-tvm/docker`), injected into the Jobs at run
  time and not baked into the image:
  - `entrypoint.sh` turns the `TVM_*` variables set by CORE into command-line flags;
  - `builder_onnx.py` and `builder_tflite.py` convert the source model;
  - `compiler.py` compiles, tunes and benchmarks the model;
  - `_dh_publish.py` uploads the result as a Model with the DigitalHub SDK.
- **Serving**: an init container downloads the `tvm-so` Model into the serve pod, and the
  serve image loads it. No image is built per model.

## Related projects

| Project                  | Image                                     | Role                                                           |
| ------------------------ | ----------------------------------------- | -------------------------------------------------------------- |
| `digitalhub-tvm-toolkit` | `ghcr.io/scc-digitalhub/tvm-toolkit`      | Image of `tvm+build` and `tvm+compile` (TVM, LLVM, compilers). |
| `digitalhub-serverless`  | `ghcr.io/scc-digitalhub/tvm-runtime-go`   | Default serve image (Go).                                      |
| `digitalhub-tvm-rust`    | `ghcr.io/scc-digitalhub/tvm-runtime-rust` | Alternative serve image (Rust).                                |

All images of a release share the same Apache TVM version, which is also their tag. A change
to what the projects exchange (the `TVM_*` variables, `metadata.json`, the Model kinds) must
be made in all of them.

## Development

Build with **JDK 21** (newer JDKs skip Lombok):

```bash
export JAVA_HOME=~/.sdkman/candidates/java/21.0.4-graal
mvn -pl runtimes/runtime-tvm install
```

Test the pod scripts inside the toolkit image:

```bash
docker run --rm -v "$PWD/runtimes/runtime-tvm/src":/src -w /src/test/python \
  ghcr.io/scc-digitalhub/tvm-toolkit:0.26.0 python3 -m unittest -v test_compiler
```

## Copyright and license

Copyright © 2025 DSLab – Fondazione Bruno Kessler and individual contributors.

This project is licensed under the Apache License, Version 2.0.
You may not use this file except in compliance with the License. Ownership of contributions remains with the original authors and is governed by the terms of the Apache 2.0 License, including the requirement to grant a license to the project.
