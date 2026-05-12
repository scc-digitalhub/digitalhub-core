/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Common run spec for Ray runs. Holds optional inputs and configuration
 * parameters surfaced to the running ray job (via env / runtime env YAML).
 */
@Getter
@Setter
@NoArgsConstructor
public class RayRunSpec extends RunBaseSpec {

    private Map<String, String> inputs = new HashMap<>();

    private Map<String, Serializable> parameters = new HashMap<>();

    @JsonProperty("init_parameters")
    private Map<String, Serializable> initParameters = new HashMap<>();

    public RayRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RayRunSpec spec = mapper.convertValue(data, RayRunSpec.class);

        this.inputs = spec.getInputs();
        this.parameters = spec.getParameters();
        this.initParameters = spec.getInitParameters();
    }
}
