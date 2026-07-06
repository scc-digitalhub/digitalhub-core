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
@SpecType(kind = "tvm-so", entity = Model.class)
public class TvmSoModelSpec extends TvmModelSpec {

    // TVM hardware target the library was built for, e.g. "llvm", "llvm
    // -mcpu=skylake", "cuda".
    @JsonProperty("target")
    private String target;

    // TVM optimization level used at compile time (0-3).
    @JsonProperty("opt_level")
    private Integer optLevel;

    // Parsed manifest.json emitted alongside the compiled library.
    @JsonProperty("manifest")
    private Map<String, Serializable> manifest;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TvmSoModelSpec spec = mapper.convertValue(data, TvmSoModelSpec.class);
        this.target = spec.getTarget();
        this.optLevel = spec.getOptLevel();
        this.manifest = spec.getManifest();
    }
}
