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
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.modelserve.models.LoRAAdapter;
import it.smartcommunitylabdhub.runtime.sglang.SGLangServeRuntime;
import jakarta.validation.constraints.NotNull;
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
@SpecType(
    runtime = SGLangServeRuntime.RUNTIME,
    kind = SGLangServeRuntime.RUNTIME,
    entity = Function.class,
    uiSchema = "runtime-sglang/uiSchema.json"
)
public class SGLangServeFunctionSpec extends FunctionBaseSpec {

    @JsonProperty("image")
    @Pattern(regexp = "^sglang\\/sglang?:.*-sglang$")
    @Schema(
        title = "Container image",
        description = "Container image for the SGLang runtime, leave blank to use default"
    )
    private String image;

    @JsonProperty("path")
    @NotNull
    @Pattern(regexp = "^store://[^/]+/model/[^/]+/.+$")
    @Schema(
        title = "Model path",
        description = "Store path for model to load. Add the id for the specific version, or use the name for latest"
    )
    private String path;

    @JsonProperty("adapters")
    @Schema(title = "LoRA Adapters", description = "List of LoRA adapters to add")
    private List<LoRAAdapter> adapters;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        SGLangServeFunctionSpec spec = mapper.convertValue(data, SGLangServeFunctionSpec.class);
        this.image = spec.getImage();
        this.path = spec.getPath();
        this.adapters = spec.getAdapters();
    }

    public static SGLangServeFunctionSpec with(Map<String, Serializable> data) {
        SGLangServeFunctionSpec spec = new SGLangServeFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
