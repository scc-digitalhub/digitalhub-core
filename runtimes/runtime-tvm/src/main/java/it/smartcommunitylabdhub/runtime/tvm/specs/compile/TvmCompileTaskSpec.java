/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.compile;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = TvmRuntime.RUNTIME, kind = TvmCompileTaskSpec.KIND, entity = Task.class, uiSchema = "runtime-tvm/tvm-compile/uiSchema.json")
public class TvmCompileTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+compile";

    // Retain inactive values for editing, outside typed execution and validation.
    @JsonIgnore
    private Map<String, Serializable> inactiveTuning = Map.of();

    @JsonProperty("model_path")
    @Schema(title = "IR Model", description = "Explicit IR override. Empty in both run and task uses function.ir_model. Example: store://my-project/model/tvm-ir/xinet-ir:version.")
    private String modelPath;

    @JsonProperty("target_architecture")
    @Schema(title = "Target", description = "Hardware the library is built for: cpu (generic, node of the Job), x86 (x86-64-v2), x86_v3 (AVX2), x86_native (CPU of the Job), arm64, arm64_pi5 (Raspberry Pi 5).", defaultValue = "cpu")
    private TvmTargetArchitecture targetArchitecture;

    @JsonProperty("target_num_cores")
    @Min(1)
    @Schema(title = "Target Number of Cores", description = "Cores of the target CPU; empty uses the Job CPU request. Example: 4 for a Raspberry Pi 5.")
    private Integer targetNumCores;

    @JsonProperty("opt_level")
    @Min(0)
    @Max(3)
    @Schema(title = "Optimization Level", description = "TVM optimization level, from 0 to 3. Example: 3.", defaultValue = "3")
    private Integer optLevel;

    @JsonProperty("cross_cc")
    @Schema(title = "Cross Compiler", description = "C++ compiler used to link model.so; empty selects it from the target. ARM64 example: aarch64-linux-gnu-g++.")
    private String crossCc;

    @JsonProperty("exec_mode")
    @Schema(title = "Execution Mode", description = "Relax VM graph execution: bytecode or compiled. Example: compiled generates native graph code.", defaultValue = "bytecode", allowableValues = {
            "bytecode", "compiled" })
    private String execMode;

    @JsonProperty("relax_pipeline")
    @Schema(title = "Relax Pipeline", description = "Named graph optimization pipeline. Example: default. Enable MetaSchedule with tuning_mode.", defaultValue = "default")
    private String relaxPipeline;

    @JsonProperty("tir_pipeline")
    @Schema(title = "TIR Pipeline", description = "Named low-level compilation pipeline. Example: default.", defaultValue = "default")
    private String tirPipeline;

    @JsonProperty("tuning_mode")
    @Schema(title = "Tuning Mode", description = "off ignores tuning settings without deleting them; tune searches schedules; apply reuses a database.", defaultValue = "off")
    private TvmTuningMode tuningMode;

    @JsonProperty("tuning_trials")
    @Min(1)
    @Schema(title = "Max Trials (Global)", description = "Global search budget, required with tune. Example: 2000; the required budget depends on the model.")
    private Integer tuningTrials;

    @JsonProperty("max_trials_per_task")
    @Min(1)
    @Schema(title = "Max Trials Per Task", description = "Maximum candidates measured per task. Example: 16.", defaultValue = "16")
    private Integer maxTrialsPerTask;

    @JsonProperty("tuning_trials_per_iter")
    @Min(1)
    @Schema(title = "Trials Per Iteration", description = "Candidates measured per task in one round. Example: 64, capped by max_trials_per_task.", defaultValue = "64")
    private Integer tuningTrialsPerIter;

    @JsonProperty("tuning_ops")
    @Schema(title = "Operator Names", description = "Optional filters on task names; empty tunes all. Example item: conv2d.")
    private List<String> tuningOps;

    @JsonProperty("tuning_model_path")
    @Schema(title = "Tuning Database Source", description = "Compiled Model containing the tuning database; required with apply. Example: store://my-project/model/tvm-so/xinet-tuned:version.")
    private String tuningModelPath;

    @JsonProperty("tuning_runner")
    @Schema(title = "Runner", description = "local measures on the Job; rpc measures on a remote target. Example: rpc for a Raspberry Pi cross-compile.", defaultValue = "local")
    private TvmTuningRunner tuningRunner;

    @JsonProperty("tuning_workers")
    @Min(1)
    @Schema(title = "Max Workers", description = "Parallel compiler workers; empty uses requested Job cores. Example: 2 with a 2-core Job.")
    private Integer tuningWorkers;

    @JsonProperty("tuning_seed")
    @Min(0)
    @Schema(title = "Random Seed", description = "Random seed for the search. Example: 0.", defaultValue = "0")
    private Integer tuningSeed;

    @JsonProperty("tuning_number")
    @Min(1)
    @Schema(title = "Runs Per Repeat", description = "Inference runs averaged into each measurement. Example: 3.", defaultValue = "3")
    private Integer tuningNumber;

    @JsonProperty("tuning_repeat")
    @Min(1)
    @Schema(title = "Measurement Repeats", description = "Independent measurements per candidate. Example: 1.", defaultValue = "1")
    private Integer tuningRepeat;

    @JsonProperty("tuning_min_repeat_ms")
    @Min(0)
    @Schema(title = "Min Repeat Time (ms)", description = "Minimum measurement duration in milliseconds. Example: 100.", defaultValue = "100")
    private Integer tuningMinRepeatMs;

    @JsonProperty("tuning_alloc_repeat")
    @Min(1)
    @Schema(title = "Allocation Repeats", description = "Random input allocations per candidate. Example: 1.", defaultValue = "1")
    private Integer tuningAllocRepeat;

    @JsonProperty("tuning_enable_cpu_cache_flush")
    @Schema(title = "Flush CPU Cache", description = "Flush CPU caches before measurements. Example: false.", defaultValue = "false")
    private Boolean tuningEnableCpuCacheFlush;

    @JsonProperty("tuning_builder_timeout_sec")
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(title = "Builder Timeout (s)", description = "Maximum seconds to compile one candidate. Example: 30.", defaultValue = "30")
    private Double tuningBuilderTimeoutSec;

    @JsonProperty("tuning_runner_timeout_sec")
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(title = "Runner Timeout (s)", description = "Maximum seconds to measure locally; RPC uses its session timeout. Example: 30.", defaultValue = "30")
    private Double tuningRunnerTimeoutSec;

    @JsonProperty("rpc_tracker_host")
    @Schema(title = "RPC Tracker Host", description = "TVM RPC tracker address. Example: 192.168.1.50; use your tracker host.")
    private String rpcTrackerHost;

    @JsonProperty("rpc_tracker_port")
    @Min(1)
    @Max(65535)
    @Schema(title = "RPC Tracker Port", description = "TVM RPC tracker port. Example: 9190; must match your tracker.")
    private Integer rpcTrackerPort;

    @JsonProperty("rpc_tracker_key")
    @Schema(title = "RPC Tracker Key", description = "Device key registered with the tracker. Example: raspberry-pi.")
    private String rpcTrackerKey;

    @JsonProperty("rpc_session_timeout_sec")
    @Min(1)
    @Schema(title = "RPC Session Timeout (s)", description = "Maximum seconds per remote measurement session. Example: 60.", defaultValue = "60")
    private Integer rpcSessionTimeoutSec;

    @JsonProperty("allow_partial_tuning")
    @Schema(title = "Allow Partial Tuning", description = "Allow incomplete task coverage, for quick tests only. Example: false.", defaultValue = "false")
    private Boolean allowPartialTuning;

    @JsonProperty("system_lib")
    @Schema(title = "System Library", description = "Self-registering library for static linking; not supported by the serve images. Example: false.", defaultValue = "false")
    private Boolean systemLib;

    @JsonProperty("benchmark_runs")
    @Min(0)
    @Schema(title = "Benchmark Runs", description = "Timed inferences after compilation; 0 disables them. Example: 10.", defaultValue = "10")
    private Integer benchmarkRuns;

    @JsonProperty("params_path")
    @Schema(title = "Parameters File", description = "Optional weights file inside the Job. Example: /shared/input/params.bin; empty detects it next to the IR.")
    private String paramsPath;

    @JsonProperty("tag")
    @Schema(title = "Tag", description = "Suffix of the compiled Model name. Example: x86 or pi5; use one per target.")
    private String tag;

    @JsonProperty("image")
    @Schema(title = "Container Image", description = "Image of the compile Job; empty uses the TVM toolkit image configured on the platform.")
    private String image;

    @Override
    public void configure(Map<String, Serializable> data) {
        Map<String, Serializable> active = TvmCompileOptions.active(data);
        this.inactiveTuning = TvmCompileOptions.inactive(data, active);
        data = active;
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

    @Override
    public Map<String, Serializable> toMap() {
        return TvmCompileOptions.stored(super.toMap(), inactiveTuning);
    }

    public static TvmCompileTaskSpec with(Map<String, Serializable> data) {
        TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
        spec.configure(data);
        return spec;
    }
}
