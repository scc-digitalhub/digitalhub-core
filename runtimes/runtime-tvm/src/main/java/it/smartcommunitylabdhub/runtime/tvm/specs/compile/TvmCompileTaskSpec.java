/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.compile;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmTargetArchitecture;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Task spec for tvm+compile: Relax IR -> model.so. All fields optional, with runner defaults.
@Getter
@Setter
@NoArgsConstructor
@SpecType(
    runtime = TvmRuntime.RUNTIME,
    kind = TvmCompileTaskSpec.KIND,
    entity = Task.class,
    uiSchema = "runtime-tvm/tvm-compile/uiSchema.json"
)
public class TvmCompileTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+compile";

    // Explicit IR model key (store://) to compile; overrides
    // function.spec.ir_model.
    @JsonProperty("model_path")
    @Schema(
        title = "IR Model",
        description = "The tvm-ir Model to compile; empty uses the one written by tvm+build in the function."
    )
    private String modelPath;

    @JsonProperty("target_architecture")
    @Schema(
        title = "Target",
        description = "Hardware the library is built for: cpu (generic, node of the Job), x86 (x86-64-v2), x86_v3 (AVX2), x86_native (CPU of the Job), arm64, arm64_pi5 (Raspberry Pi 5), armv7l (32-bit ARM).",
        defaultValue = "cpu"
    )
    private TvmTargetArchitecture targetArchitecture;

    // Number of CPU cores assumed by generated schedules and used while tuning.
    @JsonProperty("target_num_cores")
    @Min(1)
    @Schema(
        title = "Target Number of Cores",
        description = "Physical cores of the target: the tuned schedules and the tuning threads use this value; empty uses the CPU request of the Job."
    )
    private Integer targetNumCores;

    // TVM optimization level 0-3 (runner default 3).
    @JsonProperty("opt_level")
    @Min(0)
    @Schema(
        title = "Optimization Level",
        description = "TVM optimization level, from 0 to 3: higher values turn on more optimizations."
    )
    private Integer optLevel;

    // Cross C++ compiler export_library links the .so with (e.g.
    // aarch64-linux-gnu-g++).
    @JsonProperty("cross_cc")
    @Schema(
        title = "Cross Compiler",
        description = "C++ compiler that links model.so for another architecture; empty chooses it from the target."
    )
    private String crossCc;

    // Relax VM execution mode: "bytecode" (default) or "compiled".
    @JsonProperty("exec_mode")
    @Schema(
        title = "Execution Mode",
        description = "bytecode: the Relax VM interprets the model graph; compiled: the graph becomes native code."
    )
    private String execMode;

    // Named Relax optimization pipeline (default "default").
    @JsonProperty("relax_pipeline")
    @Schema(title = "Relax Pipeline", description = "Predefined graph-level optimization passes.")
    private String relaxPipeline;

    // Named TIR optimization pipeline (default "default").
    @JsonProperty("tir_pipeline")
    @Schema(title = "TIR Pipeline", description = "Predefined low-level passes run before code generation.")
    private String tirPipeline;

    // Standard Apache TVM MetaSchedule lifecycle. Off leaves the normal pipeline
    // unchanged.
    @JsonProperty("tuning_mode")
    @Schema(
        title = "Tuning Mode",
        description = "off skips tuning, tune searches the best schedules with MetaSchedule, apply reuses a database tuned earlier.",
        defaultValue = "off"
    )
    private TvmTuningMode tuningMode;

    // Global MetaSchedule budget. Required when tuning_mode=tune.
    @JsonProperty("tuning_trials")
    @Min(1)
    @Schema(
        title = "Max Trials (Global)",
        description = "Total candidates measured across all tasks; required with tune."
    )
    private Integer tuningTrials;

    // Prevent a single operator from consuming the complete global tuning budget.
    @JsonProperty("max_trials_per_task")
    @Min(1)
    @Schema(
        title = "Max Trials Per Task",
        description = "Most candidates measured for a single task.",
        defaultValue = "16"
    )
    private Integer maxTrialsPerTask;

    // Candidates measured for each task in one tuning round (MetaSchedule default 64). A
    // complete first round needs tasks x min(this, max_trials_per_task) trials.
    @JsonProperty("tuning_trials_per_iter")
    @Min(1)
    @Schema(
        title = "Trials Per Iteration",
        description = "Candidates built and measured for each task in one round.",
        defaultValue = "64"
    )
    private Integer tuningTrialsPerIter;

    // Optional task-name substring filters applied to extracted MetaSchedule tasks,
    // e.g. ["conv2d"].
    @JsonProperty("tuning_ops")
    @Schema(
        title = "Operator Names",
        description = "Tunes only the tasks whose name contains one of these words, e.g. conv2d; empty tunes all."
    )
    private List<String> tuningOps;

    // Prior compiled Model containing tuning/database_*.json. Used for resume or
    // apply-only.
    @JsonProperty("tuning_model_path")
    @Schema(
        title = "Tuning Database Source",
        description = "Compiled Model whose tuning database tune resumes or apply reuses; required with apply."
    )
    private String tuningModelPath;

    @JsonProperty("tuning_runner")
    @Schema(
        title = "Runner",
        description = "Where candidates are timed: local on the compile Job, rpc on a device registered with a TVM RPC tracker.",
        defaultValue = "local"
    )
    private TvmTuningRunner tuningRunner;

    @JsonProperty("tuning_workers")
    @Min(1)
    @Schema(
        title = "Max Workers",
        description = "Candidates compiled in parallel; with rpc also the parallel device connections."
    )
    private Integer tuningWorkers;

    @JsonProperty("tuning_seed")
    @Min(0)
    @Schema(title = "Random Seed", description = "Seed of the search, for reproducible results.", defaultValue = "0")
    private Integer tuningSeed;

    @JsonProperty("tuning_number")
    @Min(1)
    @Schema(title = "Runs Per Repeat", description = "Runs averaged into one measurement.", defaultValue = "3")
    private Integer tuningNumber;

    @JsonProperty("tuning_repeat")
    @Min(1)
    @Schema(
        title = "Measurement Repeats",
        description = "Independent measurements taken for each candidate.",
        defaultValue = "1"
    )
    private Integer tuningRepeat;

    @JsonProperty("tuning_min_repeat_ms")
    @Min(0)
    @Schema(
        title = "Min Repeat Time (ms)",
        description = "Fast kernels run again until one measurement lasts at least this long.",
        defaultValue = "100"
    )
    private Integer tuningMinRepeatMs;

    @JsonProperty("tuning_alloc_repeat")
    @Min(1)
    @Schema(
        title = "Allocation Repeats",
        description = "Sets of random inputs each candidate is timed on.",
        defaultValue = "1"
    )
    private Integer tuningAllocRepeat;

    @JsonProperty("tuning_enable_cpu_cache_flush")
    @Schema(
        title = "Flush CPU Cache",
        description = "Flushes the CPU caches before each measurement, for steadier results.",
        defaultValue = "false"
    )
    private Boolean tuningEnableCpuCacheFlush;

    @JsonProperty("tuning_builder_timeout_sec")
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(
        title = "Builder Timeout (s)",
        description = "Maximum seconds to compile one candidate.",
        defaultValue = "30"
    )
    private Double tuningBuilderTimeoutSec;

    @JsonProperty("tuning_runner_timeout_sec")
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(
        title = "Runner Timeout (s)",
        description = "Maximum seconds to time one candidate with the local runner; rpc uses the session timeout.",
        defaultValue = "30"
    )
    private Double tuningRunnerTimeoutSec;

    @JsonProperty("rpc_tracker_host")
    @Schema(title = "RPC Tracker Host", description = "Address of the TVM RPC tracker; required with the rpc runner.")
    private String rpcTrackerHost;

    @JsonProperty("rpc_tracker_port")
    @Min(1)
    @Max(65535)
    @Schema(title = "RPC Tracker Port", description = "Port of the TVM RPC tracker; required with the rpc runner.")
    private Integer rpcTrackerPort;

    @JsonProperty("rpc_tracker_key")
    @Schema(
        title = "RPC Tracker Key",
        description = "Key the target devices are registered with; required with the rpc runner."
    )
    private String rpcTrackerKey;

    @JsonProperty("rpc_session_timeout_sec")
    @Min(1)
    @Schema(
        title = "RPC Session Timeout (s)",
        description = "Maximum seconds of one remote measurement session.",
        defaultValue = "60"
    )
    private Integer rpcSessionTimeoutSec;

    @JsonProperty("allow_partial_tuning")
    @Schema(
        title = "Allow Partial Tuning",
        description = "Accepts a budget or a database that does not cover every task; for quick tests only.",
        defaultValue = "false"
    )
    private Boolean allowPartialTuning;

    // Build a system-lib style module (advanced; default false).
    @JsonProperty("system_lib")
    @Schema(
        title = "System Library",
        description = "Builds a library whose functions register themselves, for systems without dynamic loading; the serve images cannot load it."
    )
    private Boolean systemLib;

    // Timed runs of the finished model.so inside the compile Job, recorded in the model
    // metadata to compare builds; 0 disables it. Skipped when the Job cannot run the
    // library (cross-compiled targets, a CPU without the required instructions).
    @JsonProperty("benchmark_runs")
    @Min(0)
    @Schema(
        title = "Benchmark Runs",
        description = "Timed inferences of the finished library, saved in metadata.json; 0 turns the benchmark off.",
        defaultValue = "10"
    )
    private Integer benchmarkRuns;

    // Params file to bind (else sibling params.bin auto-detected). IN-POD path only
    // — store:///s3:// NOT resolved here.
    @JsonProperty("params_path")
    @Schema(
        title = "Parameters File",
        description = "Path, inside the Job, of a params.bin to embed; empty uses the one of the IR."
    )
    private String paramsPath;

    // Free-form tag recorded in the compiled model metadata and appended to its
    // name.
    @JsonProperty("tag")
    @Schema(title = "Tag", description = "Suffix of the compiled Model name, <function>-<tag>; use one tag per target.")
    private String tag;

    // Override the compiler image (default: runtime.tvm.compiler).
    @JsonProperty("image")
    @Schema(
        title = "Container Image",
        description = "Image of the compile Job; empty uses the TVM toolkit image configured on the platform."
    )
    private String image;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        TvmCompileTaskSpec spec = mapper.convertValue(data, TvmCompileTaskSpec.class);
        this.modelPath = spec.getModelPath();
        this.targetArchitecture = spec.getTargetArchitecture();
        this.targetNumCores = spec.getTargetNumCores();
        this.optLevel = spec.getOptLevel();
        this.crossCc = spec.getCrossCc();
        this.execMode = spec.getExecMode();
        this.relaxPipeline = spec.getRelaxPipeline();
        this.tirPipeline = spec.getTirPipeline();
        this.tuningMode = spec.getTuningMode();
        this.tuningTrials = spec.getTuningTrials();
        this.maxTrialsPerTask = spec.getMaxTrialsPerTask();
        this.tuningTrialsPerIter = spec.getTuningTrialsPerIter();
        this.tuningOps = spec.getTuningOps();
        this.tuningModelPath = spec.getTuningModelPath();
        this.tuningRunner = spec.getTuningRunner();
        this.tuningWorkers = spec.getTuningWorkers();
        this.tuningSeed = spec.getTuningSeed();
        this.tuningNumber = spec.getTuningNumber();
        this.tuningRepeat = spec.getTuningRepeat();
        this.tuningMinRepeatMs = spec.getTuningMinRepeatMs();
        this.tuningAllocRepeat = spec.getTuningAllocRepeat();
        this.tuningEnableCpuCacheFlush = spec.getTuningEnableCpuCacheFlush();
        this.tuningBuilderTimeoutSec = spec.getTuningBuilderTimeoutSec();
        this.tuningRunnerTimeoutSec = spec.getTuningRunnerTimeoutSec();
        this.rpcTrackerHost = spec.getRpcTrackerHost();
        this.rpcTrackerPort = spec.getRpcTrackerPort();
        this.rpcTrackerKey = spec.getRpcTrackerKey();
        this.rpcSessionTimeoutSec = spec.getRpcSessionTimeoutSec();
        this.allowPartialTuning = spec.getAllowPartialTuning();
        this.systemLib = spec.getSystemLib();
        this.benchmarkRuns = spec.getBenchmarkRuns();
        this.paramsPath = spec.getParamsPath();
        this.tag = spec.getTag();
        this.image = spec.getImage();
    }

    public static TvmCompileTaskSpec with(Map<String, Serializable> data) {
        TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
        spec.configure(data);
        return spec;
    }
}
