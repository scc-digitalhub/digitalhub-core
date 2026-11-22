/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.build;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmRunSpec;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(
    runtime = TvmRuntime.RUNTIME,
    kind = TvmBuildRunSpec.KIND,
    entity = Run.class,
    uiSchema = "runtime-tvm/tvm-build-run/uiSchema.json"
)
public final class TvmBuildRunSpec extends TvmRunSpec {

    public static final String KIND = TvmBuildTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private TvmFunctionSpec functionSpec;

    @JsonUnwrapped
    private TvmBuildTaskSpec taskBuildSpec;


    @JsonProperty("image")
    @Schema(
        title = "Container Image",
        description = "Image of the build Job; empty uses the TVM toolkit image configured on the platform."
    )
    private String image;

    @JsonProperty("simplify")
    @Schema(
        title = "Simplify ONNX Model",
        description = "Runs ONNX Simplifier to pre-compute constant operations and remove redundant nodes before the conversion. When off, it still runs by itself if the converted model has outputs without a shape, which TVM cannot compile."
    )
    private Boolean simplify;

    @JsonProperty("target_opset")
    @Schema(
        title = "Target Opset Version",
        description = "Converts the ONNX model to this opset version first; leave empty to keep the original."
    )
    private Integer targetOpset;

    @JsonProperty("opset_override")
    @Schema(
        title = "Opset Override",
        description = "Makes the TVM importer use this opset instead of the one declared by the model, without changing the model."
    )
    private Integer opsetOverride;

    @JsonProperty("strict_shape_inference")
    @Schema(
        title = "Strict Shape Inference",
        description = "Runs the ONNX shape inference in strict mode: an error skips the whole inference instead of single nodes."
    )
    private Boolean strictShapeInference;

    @JsonProperty("data_prop")
    @Schema(
        title = "Data Propagation",
        description = "Lets the shape inference compute values for a limited set of operators, to work out more tensor shapes."
    )
    private Boolean dataProp;

    @JsonProperty("keep_params_in_input")
    @Schema(
        title = "Keep Params in Input",
        description = "Treats the weights as input variables saved in params.bin instead of folding them into the graph; tvm+compile embeds them again."
    )
    private Boolean keepParamsInInput;

    @JsonProperty("sanitize_input_names")
    @Schema(
        title = "Sanitize Input Names",
        description = "Rewrites the input names so they are valid Relax identifiers."
    )
    private Boolean sanitizeInputNames;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        TvmBuildRunSpec spec = mapper.convertValue(data, TvmBuildRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskBuildSpec = spec.getTaskBuildSpec();
        this.image = spec.getImage();
        this.simplify = spec.getSimplify();
        this.targetOpset = spec.getTargetOpset();
        this.opsetOverride = spec.getOpsetOverride();
        this.strictShapeInference = spec.getStrictShapeInference();
        this.dataProp = spec.getDataProp();
        this.keepParamsInInput = spec.getKeepParamsInInput();
        this.sanitizeInputNames = spec.getSanitizeInputNames();
    }

    public static TvmBuildRunSpec with(Map<String, Serializable> data) {
        TvmBuildRunSpec spec = new TvmBuildRunSpec();
        spec.configure(data);
        return spec;
    }
}
