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
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.ollama.OllamaServeRuntime;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = OllamaServeRuntime.RUNTIME, kind = OllamaServeRunSpec.KIND, entity = Run.class)
public class OllamaServeRunSpec extends RunBaseSpec {

    public static final String KIND = OllamaServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private OllamaServeFunctionSpec functionSpec;

    @JsonUnwrapped
    private OllamaServeTaskSpec taskServeSpec;

    @JsonProperty("path")
    @Pattern(regexp = "^store://[^/]+/model/(ollama|huggingface)/.+$")
    @Schema(title = "fields.path.title", description = "fields.ollama.path.description")
    private String path;

    @JsonProperty("model_name")
    @Schema(
        title = "fields.modelserve.modelname.title",
        description = "fields.modelserve.modelname.description",
        defaultValue = "model"
    )
    private String modelName;

    // @JsonProperty("adapters")
    // @Schema(title = "fields.vllm.adapters.title", description = "fields.vllm.adapters.description")
    // private List<LoRAAdapter> adapters;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        OllamaServeRunSpec spec = mapper.convertValue(data, OllamaServeRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.path = spec.getPath();
        this.modelName = spec.getModelName();
        // this.adapters = spec.getAdapters();
    }

    public void setFunctionSpec(OllamaServeFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(OllamaServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static OllamaServeRunSpec with(Map<String, Serializable> data) {
        OllamaServeRunSpec spec = new OllamaServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
