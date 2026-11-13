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

package it.smartcommunitylabdhub.runtime.sglang.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.modelserve.models.LoRAAdapter;
import it.smartcommunitylabdhub.runtime.sglang.SGLangServeRuntime;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(
    runtime = SGLangServeRuntime.RUNTIME,
    kind = SGLangServeRunSpec.KIND,
    entity = Run.class,
    uiSchema = "runtime-sglang/uiSchema.json"
)
public class SGLangServeRunSpec extends RunBaseSpec {

    public static final String KIND = SGLangServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private SGLangServeFunctionSpec functionSpec;

    @JsonUnwrapped
    private SGLangServeTaskSpec taskServeSpec;

    @JsonProperty("path")
    @Pattern(regexp = "^store://[^/]+/model/[^/]+/.+$")
    @Schema(
        title = "Model path",
        description = "Store path for model to load. Add the id for the specific version, or use the name for latest"
    )
    private String path;

    @JsonProperty("model_name")
    @Schema(title = "Model name", description = "Name of the model to expose", defaultValue = "")
    private String modelName;

    @JsonProperty("adapters")
    @Schema(title = "LoRA Adapters", description = "List of LoRA adapters to add")
    private List<LoRAAdapter> adapters;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        SGLangServeRunSpec spec = mapper.convertValue(data, SGLangServeRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.path = spec.getPath();
        this.modelName = spec.getModelName();
        this.adapters = spec.getAdapters();
    }

    public void setFunctionSpec(SGLangServeFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(SGLangServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static SGLangServeRunSpec with(Map<String, Serializable> data) {
        SGLangServeRunSpec spec = new SGLangServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
