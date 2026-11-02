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

@Getter
@Setter
@NoArgsConstructor
public class TvmTensorSpec implements Serializable {

    @JsonProperty("name")
    @Schema(title = "Name", description = "Tensor name.")
    private String name;

    @JsonProperty("dtype")
    @Schema(title = "Data Type", description = "Element type, e.g. float32 or int8.")
    private String dtype;

    @JsonProperty("shape")
    @Schema(title = "Shape", description = "Dimensions, e.g. [1, 3, 640, 640].")
    private List<Long> shape;

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
