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
@SpecType(runtime = TvmRuntime.RUNTIME, kind = TvmRuntime.RUNTIME, entity = Function.class)
public class TvmFunctionSpec extends FunctionBaseSpec {

    // Source model: a path or a store:// key to the ONNX model.
    @JsonProperty("model")
    @NotNull
    @Schema(title = "fields.tvm.model.title", description = "fields.tvm.model.description")
    private String model;

    // Source format; "auto" lets the build task infer it from the model.
    @JsonProperty("format")
    @Schema(title = "fields.tvm.format.title", description = "fields.tvm.format.description", defaultValue = "auto")
    private TvmFormat format;

    // store:// key of the built Relax IR model (kind tvm-ir), set on build
    // completion.
    @JsonProperty("ir_model")
    @Schema(title = "fields.tvm.irModel.title", description = "fields.tvm.irModel.description")
    private String irModel;

    // store:// key of the compiled model.so (kind tvm-so), set on compile
    // completion.
    @JsonProperty("so_model")
    @Schema(title = "fields.tvm.soModel.title", description = "fields.tvm.soModel.description")
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
