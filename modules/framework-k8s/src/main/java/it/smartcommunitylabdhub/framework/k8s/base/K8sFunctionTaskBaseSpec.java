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

package it.smartcommunitylabdhub.framework.k8s.base;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.models.function.FunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreAffinity;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreNodeSelector;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreToleration;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
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
public class K8sFunctionTaskBaseSpec extends FunctionTaskBaseSpec implements K8sResourceProfileAware {

    private List<CoreVolume> volumes;

    @JsonProperty("node_selector")
    private List<CoreNodeSelector> nodeSelector;

    private List<CoreEnv> envs;

    private CoreResource resources;

    private Set<String> secrets;

    private CoreAffinity affinity;

    private List<CoreToleration> tolerations;

    @JsonProperty("runtime_class")
    private String runtimeClass;

    @JsonProperty("priority_class")
    private String priorityClass;

    private String profile;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        K8sFunctionTaskBaseSpec spec = mapper.convertValue(data, K8sFunctionTaskBaseSpec.class);

        this.volumes = spec.getVolumes();
        this.nodeSelector = spec.getNodeSelector();
        this.envs = spec.getEnvs();
        this.resources = spec.getResources();
        this.secrets = spec.getSecrets();
        this.affinity = spec.getAffinity();
        this.tolerations = spec.getTolerations();
        this.runtimeClass = spec.getRuntimeClass();
        this.priorityClass = spec.getPriorityClass();
        this.profile = spec.getProfile();
    }

    public static K8sFunctionTaskBaseSpec from(Map<String, Serializable> map) {
        K8sFunctionTaskBaseSpec spec = new K8sFunctionTaskBaseSpec();
        spec.configure(map);

        return spec;
    }
}
