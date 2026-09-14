/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.framework.k8s.provider;

import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.config.KubernetesProperties;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesModule;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
@ConditionalOnKubernetes
public class K8sConfigProvider implements ConfigurationProvider, InitializingBean {

    private K8sConfig config;
    private KubernetesModule module;

    public K8sConfigProvider(KubernetesProperties properties) {
        Assert.notNull(properties, "properties can not be null");

        log.debug("Build configuration for provider...");
        K8sConfig.K8sConfigBuilder builder = K8sConfig.builder().namespace(properties.getNamespace());

        this.config = builder.build();

        if (log.isTraceEnabled()) {
            log.trace("config: {}", config.toJson());
        }
    }

    @Autowired(required = false)
    public void setModule(KubernetesModule module) {
        this.module = module;
    }

    @Autowired(required = false)
    public void setUser(@Value("${kubernetes.security.user}") String user) {
        if (this.config != null) {
            this.config.setUser(user);
        }
    }

    @Autowired(required = false)
    public void setGroup(@Value("${kubernetes.security.group}") String group) {
        if (this.config != null) {
            this.config.setGroup(group);
        }
    }

    @Autowired(required = false)
    public void setTemplates(Collection<K8sTemplate<K8sRunnable>> templates) {
        if (templates != null && !templates.isEmpty()) {
            this.config.setProfiles(templates.stream().map(K8sTemplate::getId).toList());
        } else {
            this.config.setProfiles(null);
        }
    }

    @Override
    public K8sConfig getConfig() {
        return config;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (module != null && config != null && config.getProfiles() == null) {
            Collection<K8sTemplate<K8sRunnable>> templates = module.getTemplates();
            setTemplates(templates);
        }
    }
}
