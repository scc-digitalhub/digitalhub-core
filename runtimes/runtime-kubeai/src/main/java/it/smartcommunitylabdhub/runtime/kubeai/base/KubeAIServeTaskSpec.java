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

package it.smartcommunitylabdhub.runtime.kubeai.base;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.models.function.FunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.base.K8sResourceProfileAware;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KubeAIServeTaskSpec extends FunctionTaskBaseSpec implements K8sResourceProfileAware {

    @Schema(title = "fields.kubeai.resourceprofile.title", description = "fields.kubeai.resourceprofile.description")
    private String profile;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        KubeAIServeTaskSpec spec = mapper.convertValue(data, KubeAIServeTaskSpec.class);
        this.profile = spec.getProfile();
    }

    public static KubeAIServeTaskSpec with(Map<String, Serializable> data) {
        KubeAIServeTaskSpec spec = new KubeAIServeTaskSpec();
        spec.configure(data);
        return spec;
    }
}
