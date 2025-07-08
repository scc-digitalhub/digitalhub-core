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

package it.smartcommunitylabdhub.runtime.kubeai.text.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.runtime.kubeai.base.KubeAIServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIEngine;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIFeature;
import it.smartcommunitylabdhub.runtime.kubeai.text.KubeAITextRuntime;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SpecType(runtime = KubeAITextRuntime.RUNTIME, kind = KubeAITextRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class KubeAITextFunctionSpec extends KubeAIServeFunctionSpec {

    @JsonProperty("features")
    @Schema(
        title = "fields.kubeai.features.title",
        description = "fields.kubeai.features.description",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    @Builder.Default
    private List<KubeAIFeature> features = List.of(KubeAIFeature.TextGeneration);

    @JsonProperty("engine")
    @Schema(
        title = "fields.kubeai.engine.title",
        description = "fields.kubeai.engine.description",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private KubeAIEngine engine;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        KubeAITextFunctionSpec spec = mapper.convertValue(data, KubeAITextFunctionSpec.class);
        this.engine = spec.getEngine();
        this.features = spec.getFeatures();
    }

    public static KubeAITextFunctionSpec with(Map<String, Serializable> data) {
        KubeAITextFunctionSpec spec = new KubeAITextFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
