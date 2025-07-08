/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * https://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package it.smartcommunitylabdhub.runtime.python.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;
import it.smartcommunitylabdhub.runtime.python.model.PythonVersion;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = PythonRuntime.RUNTIME, kind = PythonRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class PythonFunctionSpec extends FunctionBaseSpec {

    @JsonProperty("source")
    @NotNull
    @Schema(title = "fields.sourceCode.title", description = "fields.sourceCode.description")
    private PythonSourceCode source;

    @JsonProperty("image")
    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    @JsonProperty("base_image")
    @Schema(title = "fields.container.baseImage.title", description = "fields.container.baseImage.description")
    private String baseImage;

    @JsonProperty("python_version")
    @Schema(title = "fields.python.version.title", description = "fields.python.version.description")
    @NotNull
    private PythonVersion pythonVersion;

    @Schema(title = "fields.python.requirements.title", description = "fields.python.requirements.description")
    private List<String> requirements;

    public PythonFunctionSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        PythonFunctionSpec spec = mapper.convertValue(data, PythonFunctionSpec.class);

        this.source = spec.getSource();
        this.image = spec.getImage();
        this.baseImage = spec.getBaseImage();
        this.pythonVersion = spec.getPythonVersion();
        this.requirements = spec.getRequirements();
    }
}
