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

package it.smartcommunitylabdhub.files.provider;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.files.config.S3Properties;
import it.smartcommunitylabdhub.files.provider.S3Config.S3ConfigBuilder;
import it.smartcommunitylabdhub.files.s3.S3FilesStore;
import it.smartcommunitylabdhub.files.service.FilesService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public class S3Provider implements ConfigurationProvider, CredentialsProvider, InitializingBean {

    private final S3Properties properties;
    private final FilesService filesService;
    private S3Config config;

    public S3Provider(FilesService filesService, S3Properties s3Properties) {
        Assert.notNull(filesService, "files service is required");
        Assert.notNull(s3Properties, "s3 properties are required");

        if (log.isTraceEnabled()) {
            log.trace("s3 properties: {}", s3Properties);
        }
        this.filesService = filesService;
        this.properties = s3Properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("Build configuration for provider...");

        //build config
        S3ConfigBuilder builder = S3Config
            .builder()
            .endpoint(properties.getEndpoint())
            .bucket(properties.getBucket())
            .region(properties.getRegion())
            .signatureVersion(properties.getSignatureVersion())
            .pathStyle(properties.getPathStyleAccess());

        this.config = builder.build();

        if (log.isTraceEnabled()) {
            log.trace("config: {}", config.toJson());
        }

        //build a file store
        S3FilesStore store = new S3FilesStore(config);

        //register with service
        if (StringUtils.hasText(properties.getBucket())) {
            filesService.registerStore("s3://" + properties.getBucket(), store);
            filesService.registerStore("zip+s3://" + properties.getBucket(), store);
        } else {
            filesService.registerStore("s3://", store);
            filesService.registerStore("zip+s3://", store);
        }
    }

    @Override
    public Credentials get(@NotNull UserAuthentication<?> auth) {
        if (config == null) {
            return null;
        }

        log.debug("generate credentials for user authentication {} via STS service", auth.getName());

        //static credentials shared
        return S3Credentials
            .builder()
            .accessKey(properties.getAccessKey())
            .secretKey(properties.getSecretKey())
            .build();
    }

    @Override
    public S3Config getConfig() {
        return config;
    }

    @Override
    public <T extends AbstractAuthenticationToken> Credentials process(@NotNull T token) {
        //nothing to do, this provider is static
        return null;
    }
}
