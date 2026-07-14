/*
   * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
   *
   * SPDX-License-Identifier: Apache-2.0
   */

package it.smartcommunitylabdhub.runtime.tvm.specs.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String name;

    // Element type, e.g. "float32".
    @JsonProperty("dtype")
    private String dtype;

    // Dimensions, e.g. [1, 3, 640, 640].
    @JsonProperty("shape")
    private List<Long> shape;
}
