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

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIFile;
import it.smartcommunitylabdhub.runtime.kubeai.models.KubeAIScaling;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KubeAIServeRunSpec extends RunBaseSpec {

    // execution
    @Schema(title = "fields.kubeai.args.title", description = "fields.kubeai.args.description")
    private List<String> args;

    @Schema(title = "fields.kubeai.env.title", description = "fields.kubeai.env.description")
    private Map<String, String> env;

    @Schema(title = "fields.kubeai.files.title", description = "fields.kubeai.files.description")
    private List<KubeAIFile> files;

    private Set<String> secrets;

    // resources

    @Schema(title = "fields.kubeai.cacheprofile.title", description = "fields.kubeai.cacheprofile.description")
    @JsonProperty("cache_profile")
    private String cacheProfile;

    @Schema(title = "fields.kubeai.processors.title", description = "fields.kubeai.processors.description")
    private Integer processors;

    @Schema(title = "fields.kubeai.scaling.title", description = "fields.kubeai.scaling.description")
    private KubeAIScaling scaling = new KubeAIScaling();

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        KubeAIServeRunSpec spec = mapper.convertValue(data, KubeAIServeRunSpec.class);
        this.args = spec.getArgs();
        this.env = spec.getEnv();
        this.files = spec.getFiles();
        this.secrets = spec.getSecrets();
        this.cacheProfile = spec.getCacheProfile();
        this.processors = spec.getProcessors();
        this.scaling = spec.getScaling();
    }

    public static KubeAIServeRunSpec with(Map<String, Serializable> data) {
        KubeAIServeRunSpec spec = new KubeAIServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
