/*
   * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
   *
   * SPDX-License-Identifier: Apache-2.0
   */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.models.Model;

import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = "tvm-ir", entity = Model.class)
public class TvmIrModelSpec extends TvmModelSpec {

    @JsonProperty("source_format")
    private TvmFormat sourceFormat;

    // Whether ONNX initializers (weights) were kept as graph inputs instead of folded to constants.
    @JsonProperty("keep_params_in_input")
    private Boolean keepParamsInInput;

    // Whether ONNX input names were rewritten to valid Relax identifiers.
    @JsonProperty("sanitize_input_names")
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
