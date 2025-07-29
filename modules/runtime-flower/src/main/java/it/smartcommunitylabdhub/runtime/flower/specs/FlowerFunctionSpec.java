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

package it.smartcommunitylabdhub.runtime.flower.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.flower.FlowerRuntime;
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
@SpecType(runtime = FlowerRuntime.RUNTIME, kind = FlowerRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class FlowerFunctionSpec extends FunctionBaseSpec {

    @JsonProperty("client_source")
    @Schema(title = "fields.flower.client.sourceCode.title", description = "fieldsflower.client.sourceCode.description")
    private FlowerSourceCode clientSource;

    @JsonProperty("server_source")
    @Schema(title = "fields.flower.server.sourceCode.title", description = "fields.flower.server.sourceCode.description")
    private FlowerSourceCode serverSource;

    @JsonProperty("client_image")
    @Schema(title = "fields.flower.client.image.title", description = "fields.flower.client.image.description")
    private String clientImage;

    @JsonProperty("base_client_image")
    @Schema(title = "fields.flower.client.baseImage.title", description = "fields.flower.client.baseImage.description")
    private String baseClientImage;

    @JsonProperty("server_image")
    @Schema(title = "fields.flower.server.image.title", description = "fields.flower.server.image.description")
    private String serverImage;

    @JsonProperty("base_server_image")
    @Schema(title = "fields.flower.server.baseImage.title", description = "fields.flower.server.baseImage.description")
    private String baseServerImage;

    @Schema(title = "fields.python.requirements.title", description = "fields.python.requirements.description")
    private List<String> requirements;

    public FlowerFunctionSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FlowerFunctionSpec spec = mapper.convertValue(data, FlowerFunctionSpec.class);
        this.requirements = spec.getRequirements();
        this.clientSource = spec.getClientSource();
        this.serverSource = spec.getServerSource();
        this.clientImage = spec.getClientImage();
        this.baseClientImage = spec.getBaseClientImage();
        this.serverImage = spec.getServerImage();
        this.baseServerImage = spec.getBaseServerImage();
    }
}
