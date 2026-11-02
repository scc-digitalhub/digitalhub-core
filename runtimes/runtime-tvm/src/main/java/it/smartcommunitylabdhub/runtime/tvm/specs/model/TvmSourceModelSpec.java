/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.models.specs.ModelBaseSpec;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

// Catalog metadata only; tvm+build reads the signature from the source file.
@Getter
@Setter
public abstract class TvmSourceModelSpec extends ModelBaseSpec {

    @JsonProperty("inputs")
    @Schema(
        title = "Inputs",
        description = "Input tensors of the model: name, data type, shape and, when quantized, scale and zero point."
    )
    private List<TvmTensorSpec> inputs;

    @JsonProperty("outputs")
    @Schema(title = "Outputs", description = "Output tensors of the model.")
    private List<TvmTensorSpec> outputs;

    @JsonProperty("parameters")
    @Schema(title = "Parameters", description = "Free-form extra information, e.g. the dataset or the export tool.")
    private Map<String, Serializable> parameters;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TvmSourceModelSpec spec = mapper.convertValue(data, getClass());
        this.inputs = spec.getInputs();
        this.outputs = spec.getOutputs();
        this.parameters = spec.getParameters();
    }
}
