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

package it.smartcommunitylabdhub.framework.k8s.runnables;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurableRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.SecuredRunnable;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreAffinity;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreImagePullPolicy;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLog;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreMetric;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreNodeSelector;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResourceDefinition;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResources;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreToleration;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.util.StringUtils;

@SuperBuilder(toBuilder = true)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class K8sRunnable implements RunRunnable, SecuredRunnable, ConfigurableRunnable, CredentialsContainer {

    private String id;

    private String user;

    private String project;

    private String runtime;

    private String task;

    private String image;

    private String command;

    private String[] args;

    private List<CoreEnv> envs;

    private List<CoreEnv> secrets;

    private CoreResources resources;

    private List<CoreVolume> volumes;

    @JsonProperty("node_selector")
    private List<CoreNodeSelector> nodeSelector;

    private CoreAffinity affinity;

    private List<CoreToleration> tolerations;

    private String runtimeClass;

    private String priorityClass;

    private CoreImagePullPolicy imagePullPolicy;

    //securityContext
    private Integer runAsUser;
    private Integer runAsGroup;
    private Integer fsGroup;

    private List<CoreLabel> labels;

    private String template;

    private String state;

    private String error;

    private String message;

    private Map<String, Serializable> results;
    private List<Map<String, Serializable>> events;

    @JsonIgnore
    @ToString.Exclude
    private List<CoreLog> logs;

    @JsonIgnore
    @ToString.Exclude
    private List<CoreMetric> metrics;

    @ToString.Exclude
    private Map<String, String> credentialsMap;

    @ToString.Exclude
    private Map<String, String> configurationMap;

    @JsonProperty("context_refs")
    private List<ContextRef> contextRefs;

    @JsonProperty("context_sources")
    private List<ContextSource> contextSources;

    @Override
    public String getFramework() {
        return "k8s";
    }

    @Override
    public void eraseCredentials() {
        this.credentialsMap = null;
    }

    @Override
    public void setCredentials(Collection<Credentials> credentials) {
        if (credentials != null) {
            //export to map
            this.credentialsMap =
                credentials
                    .stream()
                    .flatMap(c -> c.toMap().entrySet().stream())
                    //filter empty
                    .filter(e -> StringUtils.hasText(e.getValue()))
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        }
    }

    @Override
    public void setConfigurations(Collection<Configuration> configurations) {
        if (configurations != null) {
            //export to map
            this.configurationMap =
                configurations
                    .stream()
                    .flatMap(c -> c.toStringMap().entrySet().stream())
                    //filter empty
                    .filter(e -> StringUtils.hasText(e.getValue()))
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        }
    }
    // @Override
    // public void setCredentials(Collection<Credentials> credentials) {
    //     if (credentials != null) {
    //         //try to coerce into map
    //         HashMap<String, Object> map = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
    //             credentials,
    //             JacksonMapper.typeRef
    //         );

    //         this.credentials =
    //             map
    //                 .entrySet()
    //                 .stream()
    //                 .filter(e -> e.getValue() != null)
    //                 .map(e -> {
    //                     if (e.getValue() instanceof String) {
    //                         return Map.entry(e.getKey(), (String) e.getValue());
    //                     }

    //                     try {
    //                         String value = JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(e.getValue());
    //                         return Map.entry(e.getKey(), value);
    //                     } catch (JsonProcessingException je) {
    //                         return null;
    //                     }
    //                 })
    //                 .filter(e -> e.getValue() != null)
    //                 .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (o1, o2) -> o1, HashMap::new));
    //     }
    // }
}
