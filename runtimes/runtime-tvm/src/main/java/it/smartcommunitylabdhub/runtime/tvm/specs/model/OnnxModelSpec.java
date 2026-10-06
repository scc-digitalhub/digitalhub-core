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
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

/**
 * Model kind "onnx": an ONNX file uploaded as the source of a tvm function.
 *
 * When a tvm function points at a model of this kind, tvm+build picks the ONNX builder
 * without looking at the file extension.
 */
@Getter
@Setter
@SpecType(kind = OnnxModelSpec.KIND, entity = Model.class, uiSchema = "runtime-tvm/onnx/uiSchema.json")
public class OnnxModelSpec extends TvmSourceModelSpec {

    public static final String KIND = "onnx";

    // Default ONNX operator set version the model was exported with, e.g. 17.
    @JsonProperty("opset")
    @Min(1)
    @Schema(title = "Opset Version", description = "ONNX operator set version the model was exported with, e.g. 17.")
    private Integer opset;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        OnnxModelSpec spec = mapper.convertValue(data, OnnxModelSpec.class);
        this.opset = spec.getOpset();
    }
}
