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

/**
 * Base spec for the source model kinds that tvm+build accepts (onnx, tflite).
 *
 * A source model is the file a user uploads before anything is converted. Every field
 * here is optional: tvm+build reads the real signature from the file itself, so these
 * fields only describe the model for people and tools browsing the catalog.
 */
@Getter
@Setter
public abstract class TvmSourceModelSpec extends ModelBaseSpec {

    // Input tensors as declared by the model (name, dtype, shape, quantization).
    @JsonProperty("inputs")
    @Schema(title = "fields.tvm.sourceModel.inputs.title", description = "fields.tvm.sourceModel.inputs.description")
    private List<TvmTensorSpec> inputs;

    // Output tensors as declared by the model.
    @JsonProperty("outputs")
    @Schema(title = "fields.tvm.sourceModel.outputs.title", description = "fields.tvm.sourceModel.outputs.description")
    private List<TvmTensorSpec> outputs;

    // Free-form extra information, e.g. the training dataset or the export tool.
    @JsonProperty("parameters")
    @Schema(
        title = "fields.tvm.sourceModel.parameters.title",
        description = "fields.tvm.sourceModel.parameters.description"
    )
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
