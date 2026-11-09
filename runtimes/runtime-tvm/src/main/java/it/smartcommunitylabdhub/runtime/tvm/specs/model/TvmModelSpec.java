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

@Getter
@Setter
public class TvmModelSpec extends ModelBaseSpec {

    @JsonProperty("entry")
    @Schema(title = "Entry Function", description = "Function of the model to call, usually main.")
    private String entry;

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

        TvmModelSpec spec = mapper.convertValue(data, TvmModelSpec.class);
        this.entry = spec.getEntry();
        this.inputs = spec.getInputs();
        this.outputs = spec.getOutputs();
        this.parameters = spec.getParameters();
    }
}
