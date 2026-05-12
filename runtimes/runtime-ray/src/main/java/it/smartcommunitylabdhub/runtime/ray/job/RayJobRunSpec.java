/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.job;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.ray.RayRuntime;
import it.smartcommunitylabdhub.runtime.ray.specs.RayFunctionSpec;
import it.smartcommunitylabdhub.runtime.ray.specs.RayRunSpec;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = RayRuntime.RUNTIME, kind = RayJobRunSpec.KIND, entity = Run.class)
public final class RayJobRunSpec extends RayRunSpec {

    public static final String KIND = RayJobTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private RayFunctionSpec functionSpec;

    @JsonUnwrapped
    private RayJobTaskSpec taskJobSpec;

    public RayJobRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RayJobRunSpec spec = mapper.convertValue(data, RayJobRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskJobSpec = spec.getTaskJobSpec();
    }

    public void setFunctionSpec(RayFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskJobSpec(RayJobTaskSpec taskJobSpec) {
        this.taskJobSpec = taskJobSpec;
    }
}
