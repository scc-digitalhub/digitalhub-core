/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.models.Model;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = "tvm-ir", entity = Model.class, uiSchema = "runtime-tvm/tvm-ir/uiSchema.json")
public class TvmIrModelSpec extends TvmModelSpec {

    @JsonProperty("source_format")
    @Schema(title = "Source Format", description = "Format of the model the IR was converted from.")
    private TvmFormat sourceFormat;

    // Whether ONNX initializers (weights) were kept as graph inputs instead of folded to constants.
    @JsonProperty("keep_params_in_input")
    @Schema(title = "Keep Params in Input", description = "Whether the weights are kept apart in params.bin.")
    private Boolean keepParamsInInput;

    // Whether ONNX input names were rewritten to valid Relax identifiers.
    @JsonProperty("sanitize_input_names")
    @Schema(
        title = "Sanitize Input Names",
        description = "Whether the input names were rewritten into valid identifiers."
    )
    private Boolean sanitizeInputNames;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TvmIrModelSpec spec = mapper.convertValue(data, TvmIrModelSpec.class);
        this.sourceFormat = spec.getSourceFormat();
        this.keepParamsInInput = spec.getKeepParamsInInput();
        this.sanitizeInputNames = spec.getSanitizeInputNames();
    }
}
