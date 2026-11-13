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

package it.smartcommunitylabdhub.runtime.ollama.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.ollama.OllamaServeRuntime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SpecType(runtime = OllamaServeRuntime.RUNTIME, kind = OllamaServeRuntime.RUNTIME, entity = Function.class)
public class OllamaServeFunctionSpec extends FunctionBaseSpec {

    @JsonProperty("image")
    @Pattern(regexp = "^ollama\\/ollama?:.*-ollama$")
    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    @JsonProperty("path")
    @NotNull
    @Pattern(regexp = "^store://[^/]+/model/(ollama|huggingface)/.+$")
    @Schema(title = "fields.path.title", description = "fields.ollama.path.description")
    private String path;

    // @JsonProperty("adapters")
    // @Schema(title = "fields.vllm.adapters.title", description = "fields.vllm.adapters.description")
    // private List<LoRAAdapter> adapters;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        OllamaServeFunctionSpec spec = mapper.convertValue(data, OllamaServeFunctionSpec.class);
        this.image = spec.getImage();
        this.path = spec.getPath();
        // this.adapters = spec.getAdapters();
    }

    public static OllamaServeFunctionSpec with(Map<String, Serializable> data) {
        OllamaServeFunctionSpec spec = new OllamaServeFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
