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

package it.smartcommunitylabdhub.runtime.flower.app.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.flower.app.FlowerAppRuntime;
import it.smartcommunitylabdhub.runtime.flower.model.FlowerSourceCode;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = FlowerAppRuntime.RUNTIME, kind = FlowerAppRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class FlowerAppFunctionSpec extends FunctionBaseSpec {

    @JsonProperty("fab_source")
    @Schema(title = "fields.flower.sourceCode.title", description = "fields.flower.sourceCode.description")
    private FlowerSourceCode fabSource;

    @JsonProperty("image")
    @Schema(title = "fields.flower.image.title", description = "fields.flower.image.description")
    private String image;

    @JsonProperty("base_image")
    @Schema(title = "fields.flower.baseImage.title", description = "fields.flower.baseImage.description")
    private String baseImage;

    @Schema(title = "fields.python.requirements.title", description = "fields.python.requirements.description")
    private List<String> requirements;

    public FlowerAppFunctionSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FlowerAppFunctionSpec spec = mapper.convertValue(data, FlowerAppFunctionSpec.class);
        this.requirements = spec.getRequirements();
        this.fabSource = spec.getFabSource();
        this.image = spec.getImage();
        this.baseImage = spec.getBaseImage();
    }
}
