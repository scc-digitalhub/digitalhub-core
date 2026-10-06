/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmFormat;
import jakarta.validation.constraints.NotNull;
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
    kind = TvmRuntime.RUNTIME,
    entity = Function.class,
    uiSchema = "runtime-tvm/tvm/uiSchema.json"
)
public class TvmFunctionSpec extends FunctionBaseSpec {

    // Source model: an s3:// or https:// path, or the store:// key of a Model. Upload the
    // file as a Model of kind onnx or tflite and tvm+build knows its format right away.
    @JsonProperty("model")
    @NotNull
    @Schema(
        title = "Source Model",
        description = "The ONNX or TFLite model to convert: the store:// key of a Model, or an s3:// or https:// path."
    )
    private String model;

    // Source format (onnx, tflite). "auto" takes it from the kind of the referenced Model,
    // or from the file extension for generic models and plain paths.
    @JsonProperty("format")
    @Schema(
        title = "Model Format",
        description = "Format of the source model: auto takes it from the Model kind or the file extension; onnx or tflite set it.",
        defaultValue = "auto"
    )
    private TvmFormat format;

    // store:// key of the built Relax IR model (tvm-ir), set on build completion.
    @JsonProperty("ir_model")
    @Schema(
        title = "Relax IR Model",
        description = "Key of the tvm-ir Model written by tvm+build and compiled by tvm+compile. Leave it empty."
    )
    private String irModel;

    // store:// key of the compiled model.so (tvm-so), set on compile completion.
    @JsonProperty("so_model")
    @Schema(
        title = "Compiled Model",
        description = "Key of the tvm-so Model written by tvm+compile and deployed by tvm+serve. Leave it empty."
    )
    private String soModel;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TvmFunctionSpec spec = mapper.convertValue(data, TvmFunctionSpec.class);
        this.model = spec.getModel();
        this.format = spec.getFormat();
        this.irModel = spec.getIrModel();
        this.soModel = spec.getSoModel();
    }

    public static TvmFunctionSpec with(Map<String, Serializable> data) {
        TvmFunctionSpec spec = new TvmFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
