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
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Task spec for tvm+compile: Relax IR -> model.so via compiler.py. Fields map
// directly to compiler arguments; all are optional and fall back to the runner's
// defaults.
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
    @Schema(title = "fields.tvm.compile.targetArchitecture.title", description = "fields.tvm.compile.targetArchitecture.description", defaultValue = "llvm")
    private TvmTargetArchitecture targetArchitecture;

    // TVM optimization level 0-3 (runner default 3).
    @JsonProperty("opt_level")
    @Min(0)
    @Schema(title = "fields.tvm.compile.optLevel.title", description = "fields.tvm.compile.optLevel.description")
    private Integer optLevel;

    // Cross C++ compiler used by export_library to link the .so when
    // cross-compiling
    // (e.g. aarch64-linux-gnu-g++).
    @JsonProperty("cross_cc")
    @Schema(title = "fields.tvm.compile.crossCc.title", description = "fields.tvm.compile.crossCc.description")
    private String crossCc;

    // Relax VM execution mode: "bytecode" (default) or "compiled".
    @JsonProperty("exec_mode")
    @Schema(title = "fields.tvm.compile.execMode.title", description = "fields.tvm.compile.execMode.description")
    private String execMode;

    // Named Relax optimization pipeline (default "default").
    @JsonProperty("relax_pipeline")
    @Schema(title = "fields.tvm.compile.relaxPipeline.title", description = "fields.tvm.compile.relaxPipeline.description")
    private String relaxPipeline;

    // Named TIR optimization pipeline (default "default").
    @JsonProperty("tir_pipeline")
    @Schema(title = "fields.tvm.compile.tirPipeline.title", description = "fields.tvm.compile.tirPipeline.description")
    private String tirPipeline;

    // Build a system-lib style module (advanced; default false).
    @JsonProperty("system_lib")
    @Schema(title = "fields.tvm.compile.systemLib.title", description = "fields.tvm.compile.systemLib.description")
    private Boolean systemLib;

    // Explicit params file to bind into the IR; otherwise a sibling params.bin in
    // the IR dir is auto-detected (the common case). NOTE: this is an IN-POD path
    // (e.g. /shared/input/myparams.bin) — store:// or s3:// values are NOT resolved
    // or downloaded for this field, unlike model_path.
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
        this.optLevel = spec.getOptLevel();
        this.crossCc = spec.getCrossCc();
        this.execMode = spec.getExecMode();
        this.relaxPipeline = spec.getRelaxPipeline();
        this.tirPipeline = spec.getTirPipeline();
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
