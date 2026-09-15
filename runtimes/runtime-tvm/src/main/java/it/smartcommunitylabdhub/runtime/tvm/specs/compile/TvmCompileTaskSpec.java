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
@SpecType(runtime = TvmRuntime.RUNTIME, kind = TvmCompileTaskSpec.KIND, entity = Task.class)
public class TvmCompileTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+compile";

    // Explicit IR model key (store://) to compile; overrides
    // function.spec.ir_model.
    @JsonProperty("model_path")
    @Schema(title = "fields.tvm.compile.modelPath.title", description = "fields.tvm.compile.modelPath.description")
    private String modelPath;

    @JsonProperty("target_architecture")
    @Schema(
        title = "fields.tvm.compile.targetArchitecture.title",
        description = "fields.tvm.compile.targetArchitecture.description",
        defaultValue = "cpu"
    )
    private TvmTargetArchitecture targetArchitecture;

    // Number of CPU cores assumed by generated schedules and used while tuning.
    @JsonProperty("target_num_cores")
    @Min(1)
    @Schema(title = "fields.tvm.compile.targetNumCores.title", description = "fields.tvm.compile.targetNumCores.description")
    private Integer targetNumCores;

    // TVM optimization level 0-3 (runner default 3).
    @JsonProperty("opt_level")
    @Min(0)
    @Schema(title = "fields.tvm.compile.optLevel.title", description = "fields.tvm.compile.optLevel.description")
    private Integer optLevel;

    // Cross C++ compiler export_library links the .so with (e.g.
    // aarch64-linux-gnu-g++).
    @JsonProperty("cross_cc")
    @Schema(title = "fields.tvm.compile.crossCc.title", description = "fields.tvm.compile.crossCc.description")
    private String crossCc;

    // Relax VM execution mode: "bytecode" (default) or "compiled".
    @JsonProperty("exec_mode")
    @Schema(title = "fields.tvm.compile.execMode.title", description = "fields.tvm.compile.execMode.description")
    private String execMode;

    // Named Relax optimization pipeline (default "default").
    @JsonProperty("relax_pipeline")
    @Schema(
        title = "fields.tvm.compile.relaxPipeline.title",
        description = "fields.tvm.compile.relaxPipeline.description"
    )
    private String relaxPipeline;

    // Named TIR optimization pipeline (default "default").
    @JsonProperty("tir_pipeline")
    @Schema(title = "fields.tvm.compile.tirPipeline.title", description = "fields.tvm.compile.tirPipeline.description")
    private String tirPipeline;

    // Standard Apache TVM MetaSchedule lifecycle. Off leaves the normal pipeline
    // unchanged.
    @JsonProperty("tuning_mode")
    @Schema(title = "fields.tvm.compile.tuningMode.title", description = "fields.tvm.compile.tuningMode.description", defaultValue = "off")
    private TvmTuningMode tuningMode;

    // Global MetaSchedule budget. Required when tuning_mode=tune.
    @JsonProperty("tuning_trials")
    @Min(1)
    @Schema(title = "fields.tvm.compile.tuningTrials.title", description = "fields.tvm.compile.tuningTrials.description")
    private Integer tuningTrials;

    // Prevent a single operator from consuming the complete global tuning budget.
    @JsonProperty("max_trials_per_task")
    @Min(1)
    @Schema(title = "fields.tvm.compile.maxTrialsPerTask.title", description = "fields.tvm.compile.maxTrialsPerTask.description", defaultValue = "16")
    private Integer maxTrialsPerTask;

    // Optional task-name substring filters applied to extracted MetaSchedule tasks,
    // e.g. ["conv2d"].
    @JsonProperty("tuning_ops")
    @Schema(title = "fields.tvm.compile.tuningOps.title", description = "fields.tvm.compile.tuningOps.description")
    private List<String> tuningOps;

    // Prior compiled Model containing tuning/database_*.json. Used for resume or
    // apply-only.
    @JsonProperty("tuning_model_path")
    @Schema(title = "fields.tvm.compile.tuningModelPath.title", description = "fields.tvm.compile.tuningModelPath.description")
    private String tuningModelPath;

    @JsonProperty("tuning_runner")
    @Schema(title = "fields.tvm.compile.tuningRunner.title", description = "fields.tvm.compile.tuningRunner.description", defaultValue = "local")
    private TvmTuningRunner tuningRunner;

    @JsonProperty("tuning_workers")
    @Min(1)
    @Schema(title = "fields.tvm.compile.tuningWorkers.title", description = "fields.tvm.compile.tuningWorkers.description")
    private Integer tuningWorkers;

    @JsonProperty("tuning_seed")
    @Min(0)
    @Schema(title = "fields.tvm.compile.tuningSeed.title", description = "fields.tvm.compile.tuningSeed.description", defaultValue = "0")
    private Integer tuningSeed;

    @JsonProperty("tuning_number")
    @Min(1)
    @Schema(title = "fields.tvm.compile.tuningNumber.title", description = "fields.tvm.compile.tuningNumber.description", defaultValue = "3")
    private Integer tuningNumber;

    @JsonProperty("tuning_repeat")
    @Min(1)
    @Schema(title = "fields.tvm.compile.tuningRepeat.title", description = "fields.tvm.compile.tuningRepeat.description", defaultValue = "1")
    private Integer tuningRepeat;

    @JsonProperty("tuning_min_repeat_ms")
    @Min(0)
    @Schema(title = "fields.tvm.compile.tuningMinRepeatMs.title", description = "fields.tvm.compile.tuningMinRepeatMs.description", defaultValue = "100")
    private Integer tuningMinRepeatMs;

    @JsonProperty("tuning_alloc_repeat")
    @Min(1)
    @Schema(title = "fields.tvm.compile.tuningAllocRepeat.title", description = "fields.tvm.compile.tuningAllocRepeat.description", defaultValue = "1")
    private Integer tuningAllocRepeat;

    @JsonProperty("tuning_enable_cpu_cache_flush")
    @Schema(title = "fields.tvm.compile.tuningEnableCpuCacheFlush.title", description = "fields.tvm.compile.tuningEnableCpuCacheFlush.description", defaultValue = "false")
    private Boolean tuningEnableCpuCacheFlush;

    @JsonProperty("tuning_builder_timeout_sec")
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(title = "fields.tvm.compile.tuningBuilderTimeoutSec.title", description = "fields.tvm.compile.tuningBuilderTimeoutSec.description", defaultValue = "30")
    private Double tuningBuilderTimeoutSec;

    @JsonProperty("tuning_runner_timeout_sec")
    @DecimalMin(value = "0.0", inclusive = false)
    @Schema(title = "fields.tvm.compile.tuningRunnerTimeoutSec.title", description = "fields.tvm.compile.tuningRunnerTimeoutSec.description", defaultValue = "30")
    private Double tuningRunnerTimeoutSec;

    @JsonProperty("rpc_tracker_host")
    @Schema(title = "fields.tvm.compile.rpcTrackerHost.title", description = "fields.tvm.compile.rpcTrackerHost.description")
    private String rpcTrackerHost;

    @JsonProperty("rpc_tracker_port")
    @Min(1)
    @Max(65535)
    @Schema(title = "fields.tvm.compile.rpcTrackerPort.title", description = "fields.tvm.compile.rpcTrackerPort.description")
    private Integer rpcTrackerPort;

    @JsonProperty("rpc_tracker_key")
    @Schema(title = "fields.tvm.compile.rpcTrackerKey.title", description = "fields.tvm.compile.rpcTrackerKey.description")
    private String rpcTrackerKey;

    @JsonProperty("rpc_session_timeout_sec")
    @Min(1)
    @Schema(title = "fields.tvm.compile.rpcSessionTimeoutSec.title", description = "fields.tvm.compile.rpcSessionTimeoutSec.description", defaultValue = "60")
    private Integer rpcSessionTimeoutSec;

    @JsonProperty("allow_partial_tuning")
    @Schema(title = "fields.tvm.compile.allowPartialTuning.title", description = "fields.tvm.compile.allowPartialTuning.description", defaultValue = "false")
    private Boolean allowPartialTuning;

    // Build a system-lib style module (advanced; default false).
    @JsonProperty("system_lib")
    @Schema(title = "fields.tvm.compile.systemLib.title", description = "fields.tvm.compile.systemLib.description")
    private Boolean systemLib;

    // Params file to bind (else sibling params.bin auto-detected). IN-POD path only
    // — store:///s3:// NOT resolved here.
    @JsonProperty("params_path")
    @Schema(title = "fields.tvm.compile.paramsPath.title", description = "fields.tvm.compile.paramsPath.description")
    private String paramsPath;

    // Free-form tag recorded in the compiled model metadata and appended to its
    // name.
    @JsonProperty("tag")
    @Schema(title = "fields.tvm.compile.tag.title", description = "fields.tvm.compile.tag.description")
    private String tag;

    // Override the compiler image (default: runtime.tvm.compiler).
    @JsonProperty("image")
    @Schema(title = "fields.tvm.compile.image.title", description = "fields.tvm.compile.image.description")
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
