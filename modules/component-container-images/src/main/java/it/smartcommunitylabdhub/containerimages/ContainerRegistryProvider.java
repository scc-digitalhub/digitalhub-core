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

package it.smartcommunitylabdhub.containerimages;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.containerimages.config.ContainerImagesProperties;
import it.smartcommunitylabdhub.containerimages.model.ContainerRegistryConfig;
import it.smartcommunitylabdhub.containerimages.model.ContainerRegistryCredentials;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

@Slf4j
@Service
public class ContainerRegistryProvider
    implements CredentialsProvider<ContainerRegistryCredentials>, ConfigurationProvider
{

    private final ContainerImagesProperties.Credentials properties;

    private final ContainerRegistryConfig config;
    private final ContainerRegistryCredentials credentials;

    public ContainerRegistryProvider(ContainerImagesProperties properties) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties.getUser();

        if (this.properties.registry() != null) {
            this.config = ContainerRegistryConfig.builder().registry(this.properties.registry()).build();
        } else {
            this.config = null;
        }

        if (this.properties.username() != null && this.properties.password() != null) {
            this.credentials = ContainerRegistryCredentials.builder()
                .username(this.properties.username())
                .password(this.properties.password())
                .build();
        } else {
            this.credentials = null;
        }
    }

    @Override
    public ContainerRegistryCredentials get(@NotNull UserAuthentication<?> auth) {
        log.debug("use shared credentials for user authentication to registry {}", auth.getName());

        //static credentials shared
        return credentials;
    }

    @Override
    public ContainerRegistryConfig getConfig() {
        return config;
    }
}
