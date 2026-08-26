/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.build;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.ray.RayRuntime;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Task spec for the {@code ray+build} task.
 *
 * <p>Drives a Kaniko build that bakes the function source code, requirements
 * and any extra Dockerfile {@code RUN} instructions on top of the Ray base
 * image. The resulting image is recorded back into the function spec and
 * reused by subsequent {@code ray+job} runs.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = RayRuntime.RUNTIME, kind = RayBuildTaskSpec.KIND, entity = Task.class)
public class RayBuildTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "ray+build";

    /**
     * Optional list of extra Dockerfile {@code RUN} instructions executed after
     * staging the source code and before installing requirements.
     */
    @Schema(title = "fields.ray.instructions.title", description = "fields.ray.instructions.description")
    private List<String> instructions;

    public RayBuildTaskSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RayBuildTaskSpec spec = mapper.convertValue(data, RayBuildTaskSpec.class);
        this.instructions = spec.getInstructions();
    }

    public static RayBuildTaskSpec with(Map<String, Serializable> data) {
        RayBuildTaskSpec spec = new RayBuildTaskSpec();
        spec.configure(data);
        return spec;
    }
}
