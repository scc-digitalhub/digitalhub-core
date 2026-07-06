/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.build;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Task spec for tvm+build: source ML model -> Relax IR. Beyond the base K8s job
// settings it carries the frontend conversion knobs; most only apply to the ONNX
// path and are passed through to the builder script.
@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = TvmRuntime.RUNTIME, kind = TvmBuildTaskSpec.KIND, entity = Task.class)
public class TvmBuildTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+build";

    // Override the per-format builder image (default: runtime.tvm.builders[<format>]).
    @JsonProperty("image")
    @Schema(title = "fields.tvm.builderImage.title", description = "fields.tvm.builderImage.description")
    private String image;

    // ONNX only: run onnxsim.simplify on the graph before converting to Relax IR.
    @JsonProperty("simplify")
    @Schema(title = "fields.tvm.simplify.title", description = "fields.tvm.simplify.description")
    private Boolean simplify;

    // ONNX only: upgrade/downgrade the model to this opset (onnx.version_converter)
    // before conversion.
    @JsonProperty("target_opset")
    @Schema(title = "fields.tvm.targetOpset.title", description = "fields.tvm.targetOpset.description")
    private Integer targetOpset;

    // ONNX only: opset passed to from_onnx, overriding the model's declared opset.
    @JsonProperty("opset_override")
    @Schema(title = "fields.tvm.opsetOverride.title", description = "fields.tvm.opsetOverride.description")
    private Integer opsetOverride;

    // ONNX only: use strict mode during ONNX shape inference.
    @JsonProperty("strict_shape_inference")
    @Schema(title = "fields.tvm.strictShapeInference.title", description = "fields.tvm.strictShapeInference.description")
    private Boolean strictShapeInference;

    // ONNX only: enable data propagation during ONNX shape inference.
    @JsonProperty("data_prop")
    @Schema(title = "fields.tvm.dataProp.title", description = "fields.tvm.dataProp.description")
    private Boolean dataProp;

    // Keep model params as graph inputs rather than folding them into constants
    // (from_onnx/from_pytorch keep_params_in_input).
    @JsonProperty("keep_params_in_input")
    @Schema(title = "fields.tvm.keepParamsInInput.title", description = "fields.tvm.keepParamsInInput.description")
    private Boolean keepParamsInInput;

    // Sanitize input tensor names during conversion to Relax IR.
    @JsonProperty("sanitize_input_names")
    @Schema(title = "fields.tvm.sanitizeInputNames.title", description = "fields.tvm.sanitizeInputNames.description")
    private Boolean sanitizeInputNames;

    // PyTorch-only: from_exported_program flags. Ignored by the ONNX and TVMScript
    // frontends. When left null the TVM defaults apply (unwrap=false, no_bind=false,
    // run_ep_decomposition=true).
    @JsonProperty("unwrap_unit_return_tuple")
    @Schema(
        title = "fields.tvm.unwrapUnitReturnTuple.title",
        description = "fields.tvm.unwrapUnitReturnTuple.description"
    )
    private Boolean unwrapUnitReturnTuple;

    @JsonProperty("no_bind_return_tuple")
    @Schema(title = "fields.tvm.noBindReturnTuple.title", description = "fields.tvm.noBindReturnTuple.description")
    private Boolean noBindReturnTuple;

    @JsonProperty("run_ep_decomposition")
    @Schema(
        title = "fields.tvm.runEpDecomposition.title",
        description = "fields.tvm.runEpDecomposition.description"
    )
    private Boolean runEpDecomposition;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        TvmBuildTaskSpec spec = mapper.convertValue(data, TvmBuildTaskSpec.class);
        this.image = spec.getImage();
        this.simplify = spec.getSimplify();
        this.targetOpset = spec.getTargetOpset();
        this.opsetOverride = spec.getOpsetOverride();
        this.strictShapeInference = spec.getStrictShapeInference();
        this.dataProp = spec.getDataProp();
        this.keepParamsInInput = spec.getKeepParamsInInput();
        this.sanitizeInputNames = spec.getSanitizeInputNames();
        this.unwrapUnitReturnTuple = spec.getUnwrapUnitReturnTuple();
        this.noBindReturnTuple = spec.getNoBindReturnTuple();
        this.runEpDecomposition = spec.getRunEpDecomposition();
    }

    public static TvmBuildTaskSpec with(Map<String, Serializable> data) {
        TvmBuildTaskSpec spec = new TvmBuildTaskSpec();
        spec.configure(data);
        return spec;
    }
}
