/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// A single input/output tensor of a TVM model: name, element type, and shape.
@Getter
@Setter
@NoArgsConstructor
public class TvmTensorSpec implements Serializable {

    @JsonProperty("name")
    @Schema(title = "Name", description = "Tensor name.")
    private String name;

    // Element type, e.g. "float32".
    @JsonProperty("dtype")
    @Schema(title = "Data Type", description = "Element type, e.g. float32 or int8.")
    private String dtype;

    // Dimensions, e.g. [1, 3, 640, 640].
    @JsonProperty("shape")
    @Schema(title = "Shape", description = "Dimensions, e.g. [1, 3, 640, 640].")
    private List<Long> shape;

    // Affine quantization params, present only for quantized tensors (int8/uint8):
    // real = (q - zero_point) * scale. Independent of the source format — a QDQ ONNX
    // carries them exactly like a TFLite full-integer model. Per-axis quantization
    // yields more than one entry, indexed by quantizedDimension.
    @JsonProperty("scale")
    @Schema(title = "Scale", description = "Quantization scale: real = (quantized - zero point) * scale.")
    private List<Double> scale;

    @JsonProperty("zero_point")
    @Schema(title = "Zero Point", description = "Quantization zero point.")
    private List<Long> zeroPoint;

    @JsonProperty("quantized_dimension")
    @Schema(title = "Quantized Dimension", description = "Axis of per-axis quantization.")
    private Integer quantizedDimension;
}
