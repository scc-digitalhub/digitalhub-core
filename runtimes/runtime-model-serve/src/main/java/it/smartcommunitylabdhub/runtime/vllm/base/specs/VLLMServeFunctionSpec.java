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

package it.smartcommunitylabdhub.runtime.vllm.base.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.vllm.base.models.VLLMAdapter;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VLLMServeFunctionSpec extends FunctionBaseSpec {



    @JsonProperty("model_name")
    @Schema(
        title = "fields.modelserve.modelname.title",
        description = "fields.modelserve.modelname.description",
        defaultValue = "model"
    )
    private String modelName;

    @JsonProperty("image")
    @Pattern(regexp = "^vllm\\/vllm-openai(:.*)?$")
    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    @JsonProperty("adapters")
    @Schema(title = "fields.vllm.adapters.title", description = "fields.vllm.adapters.description")
    private List<VLLMAdapter> adapters;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        VLLMServeFunctionSpec spec = mapper.convertValue(data, VLLMServeFunctionSpec.class);
        this.modelName = spec.getModelName();
        this.image = spec.getImage();
        this.adapters = spec.getAdapters();
    }
}
