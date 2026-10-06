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

// Task spec for tvm+build: source model (ONNX or TFLite) -> Relax IR. Most options tune the
// ONNX conversion; the TFLite builder ignores them.
@Getter
@Setter
@NoArgsConstructor
@SpecType(
    runtime = TvmRuntime.RUNTIME,
    kind = TvmBuildTaskSpec.KIND,
    entity = Task.class,
    uiSchema = "runtime-tvm/tvm-build/uiSchema.json"
)
public class TvmBuildTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+build";

    // Override the per-format builder image (default: runtime.tvm.builders[<format>]).
    @JsonProperty("image")
    @Schema(
        title = "Container Image",
        description = "Image of the build Job; empty uses the TVM toolkit image configured on the platform."
    )
    private String image;

    // ONNX only: run onnxsim.simplify on the graph before converting to Relax IR. build_onnx.py
    // also runs it by itself when the converted outputs have no shape.
    @JsonProperty("simplify")
    @Schema(
        title = "Simplify ONNX Model",
        description = "Runs ONNX Simplifier to pre-compute constant operations and remove redundant nodes before the conversion. When off, it still runs by itself if the converted model has outputs without a shape, which TVM cannot compile."
    )
    private Boolean simplify;

    // ONNX only: upgrade/downgrade to this opset (onnx.version_converter) before conversion.
    @JsonProperty("target_opset")
    @Schema(
        title = "Target Opset Version",
        description = "Converts the ONNX model to this opset version first; leave empty to keep the original."
    )
    private Integer targetOpset;

    // ONNX only: opset passed to from_onnx, overriding the model's declared opset.
    @JsonProperty("opset_override")
    @Schema(
        title = "Opset Override",
        description = "Makes the TVM importer use this opset instead of the one declared by the model, without changing the model."
    )
    private Integer opsetOverride;

    // ONNX only: use strict mode during ONNX shape inference.
    @JsonProperty("strict_shape_inference")
    @Schema(
        title = "Strict Shape Inference",
        description = "Runs the ONNX shape inference in strict mode: an error skips the whole inference instead of single nodes."
    )
    private Boolean strictShapeInference;

    // ONNX only: enable data propagation during ONNX shape inference.
    @JsonProperty("data_prop")
    @Schema(
        title = "Data Propagation",
        description = "Lets the shape inference compute values for a limited set of operators, to work out more tensor shapes."
    )
    private Boolean dataProp;

    // Keep model params as graph inputs rather than folding to constants (from_onnx keep_params_in_input).
    @JsonProperty("keep_params_in_input")
    @Schema(
        title = "Keep Params in Input",
        description = "Treats the weights as input variables saved in params.bin instead of folding them into the graph; tvm+compile embeds them again."
    )
    private Boolean keepParamsInInput;

    // Sanitize input tensor names during conversion to Relax IR.
    @JsonProperty("sanitize_input_names")
    @Schema(
        title = "Sanitize Input Names",
        description = "Rewrites the input names so they are valid Relax identifiers."
    )
    private Boolean sanitizeInputNames;

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
    }

    public static TvmBuildTaskSpec with(Map<String, Serializable> data) {
        TvmBuildTaskSpec spec = new TvmBuildTaskSpec();
        spec.configure(data);
        return spec;
    }
}
