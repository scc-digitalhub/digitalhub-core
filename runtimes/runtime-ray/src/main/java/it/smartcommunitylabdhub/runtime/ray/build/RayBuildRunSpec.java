/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.build;

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
@SpecType(runtime = RayRuntime.RUNTIME, kind = RayBuildRunSpec.KIND, entity = Run.class)
public final class RayBuildRunSpec extends RayRunSpec {

    public static final String KIND = RayBuildTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private RayFunctionSpec functionSpec;

    @JsonUnwrapped
    private RayBuildTaskSpec taskBuildSpec;

    public RayBuildRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RayBuildRunSpec spec = mapper.convertValue(data, RayBuildRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskBuildSpec = spec.getTaskBuildSpec();
    }

    public void setFunctionSpec(RayFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskBuildSpec(RayBuildTaskSpec buildTaskSpec) {
        this.taskBuildSpec = buildTaskSpec;
    }
}
