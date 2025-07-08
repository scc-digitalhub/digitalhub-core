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

package it.smartcommunitylabdhub.framework.k8s.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnCloudPlatform;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.cloud.CloudPlatform;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesConfig {

    @Bean
    @ConditionalOnCloudPlatform(CloudPlatform.KUBERNETES)
    public ApiClient kubeApiClusterClient() {
        try {
            return ClientBuilder.cluster().build();
        } catch (IOException e1) {
            throw new UnsupportedOperationException("Could not initialize connection to kubernetes.");
        }
    }

    @Bean
    @ConditionalOnMissingBean(ApiClient.class)
    @ConditionalOnKubernetes
    public ApiClient kubeApiStandardClient() {
        try {
            return ClientBuilder.standard().build();
        } catch (IOException e1) {
            throw new UnsupportedOperationException("Could not initialize connection to kubernetes.");
        }
    }
}
