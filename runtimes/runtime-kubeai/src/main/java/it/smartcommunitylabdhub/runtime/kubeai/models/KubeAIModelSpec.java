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

package it.smartcommunitylabdhub.runtime.kubeai.models;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KubeAIModelSpec {

    private String url;
    private List<KubeAIAdapter> adapters;
    private String engine;
    private List<String> features;
    private String image;

    private List<String> args;
    private String resourceProfile;
    private String cacheProfile;
    private Map<String, String> env;
    private List<KubeAiEnvFrom> envFrom;
    private Integer replicas;
    private Integer minReplicas;
    private Integer maxReplicas;
    private Boolean autoscalingDisabled;
    private Integer targetRequests;
    private Integer scaleDownDelaySeconds;
    private KubeAILoadBalancing loadBalancing;
    private List<KubeAIFile> files;
}
