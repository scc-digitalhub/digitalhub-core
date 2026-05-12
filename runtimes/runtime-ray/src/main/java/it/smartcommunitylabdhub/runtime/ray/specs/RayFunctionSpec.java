/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.ray.RayRuntime;
import it.smartcommunitylabdhub.runtime.ray.model.RayDependencyFormat;
import it.smartcommunitylabdhub.runtime.ray.model.RaySourceCode;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Function spec for a Ray job. Carries the Python source code that will be
 * executed by Ray and an optional declarative dependency definition.
 *
 * <p>Two equivalent ways to express dependencies are supported:</p>
 * <ul>
 *   <li>{@code requirements} — a simple list of pip-installable packages,
 *       which the runtime will translate into the configured
 *       {@code dependency_format} (defaults to {@code pip}); and</li>
 *   <li>{@code dependency_format} + {@code dependency_spec} — explicit form
 *       passed verbatim into the Ray runtime environment YAML, allowing
 *       advanced shapes such as {@code conda} environments or {@code pip}
 *       blocks with extra options.</li>
 * </ul>
 */
@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = RayRuntime.RUNTIME, kind = RayRuntime.RUNTIME, entity = Function.class)
public class RayFunctionSpec extends FunctionBaseSpec {

    @JsonProperty("source")
    @NotNull
    @Schema(title = "fields.sourceCode.title", description = "fields.sourceCode.description")
    private RaySourceCode source;

    @JsonProperty("image")
    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    @JsonProperty("base_image")
    @Schema(title = "fields.container.baseImage.title", description = "fields.container.baseImage.description")
    private String baseImage;

    @Schema(title = "fields.python.requirements.title", description = "fields.python.requirements.description")
    private List<String> requirements;

    @JsonProperty("dependency_format")
    @Schema(title = "fields.ray.dependencyFormat.title", description = "fields.ray.dependencyFormat.description")
    private RayDependencyFormat dependencyFormat;

    @JsonProperty("dependency_spec")
    @Schema(title = "fields.ray.dependencySpec.title", description = "fields.ray.dependencySpec.description")
    private Serializable dependencySpec;

    public RayFunctionSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RayFunctionSpec spec = mapper.convertValue(data, RayFunctionSpec.class);

        this.source = spec.getSource();
        this.image = spec.getImage();
        this.baseImage = spec.getBaseImage();
        this.requirements = spec.getRequirements();
        this.dependencyFormat = spec.getDependencyFormat();
        this.dependencySpec = spec.getDependencySpec();
    }

    public static RayFunctionSpec with(Map<String, Serializable> data) {
        RayFunctionSpec spec = new RayFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
