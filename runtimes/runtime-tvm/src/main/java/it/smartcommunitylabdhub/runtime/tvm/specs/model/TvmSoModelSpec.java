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
@SpecType(kind = "tvm-so", entity = Model.class, uiSchema = "runtime-tvm/tvm-so/uiSchema.json")
public class TvmSoModelSpec extends TvmModelSpec {

    // TVM target the library was built for: "llvm" (cpu) or a JSON target dict for x86/arm64/armv7l.
    @JsonProperty("target")
    @Schema(title = "Target", description = "TVM target the library was compiled for.")
    private String target;

    // TVM optimization level used at compile time (0-3).
    @JsonProperty("opt_level")
    @Schema(title = "Optimization Level", description = "Optimization level used by the compile.")
    private Integer optLevel;

    // Parsed manifest.json emitted alongside the compiled library.
    @JsonProperty("manifest")
    @Schema(title = "Manifest", description = "The metadata.json of the compiled library.")
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
