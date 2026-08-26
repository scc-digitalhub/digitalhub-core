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

package it.smartcommunitylabdhub.trinodb;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.providers.AccessCredentials;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.trinodb.config.TrinoDbProperties;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class TrinoDbProvider implements CredentialsProvider, ConfigurationProvider, InitializingBean {

    private final TrinoDbProperties properties;
    private TrinoDbConfiguration config;

    public TrinoDbProvider(TrinoDbProperties properties) {
        Assert.notNull(properties, "trino properties can not be null");

        if (log.isTraceEnabled()) {
            log.trace("db properties: {}", properties);
        }

        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (properties.isEnabled()) {
            //check if host is set as uri, if not build from host+port
            if (!StringUtils.hasText(properties.getHost())) {
                throw new IllegalArgumentException("trino host is not set");
            }

            String host = properties.getHost();
            String scheme = properties.getScheme();
            Integer port = properties.getPort();
            if (host.toLowerCase().startsWith("http")) {
                UriComponents uri = UriComponentsBuilder.fromUriString(host).build();

                host = uri.getHost();
                if (uri.getPort() != -1) {
                    port = uri.getPort();
                }

                if (StringUtils.hasText(uri.getScheme()) && !StringUtils.hasText(scheme)) {
                    scheme = uri.getScheme();
                }
            }

            this.config = TrinoDbConfiguration.builder()
                .scheme(scheme)
                .host(host)
                .port(port)
                .url(properties.getUrl())
                .catalog(properties.getCatalog())
                .build();

            if (log.isTraceEnabled()) {
                log.trace("config: {}", config.toJson());
            }
        }
    }

    @Override
    @Nullable
    public Configuration getConfig() {
        return this.config;
    }

    @Override
    public <T extends AbstractAuthenticationToken> Credentials process(@NotNull T token) {
        //if username+password is set, provide as static credentials
        if (StringUtils.hasText(properties.getUser()) && StringUtils.hasText(properties.getPassword())) {
            return new TrinoDbCredentials(properties.getUser(), properties.getPassword(), null);
        }

        //if core token is set, provider as user+token credentials
        if (Boolean.TRUE.equals(properties.getUseCoreCredentials()) && token instanceof UserAuthentication<?> auth) {
            AccessCredentials credentials = auth
                .getCredentials()
                .stream()
                .filter(c -> c instanceof AccessCredentials)
                .findFirst()
                .map(c -> (AccessCredentials) c)
                .orElse(null);

            if (credentials != null && credentials.getAccessTokenAsString() != null) {
                return new TrinoDbCredentials(auth.getUsername(), null, credentials.getAccessTokenAsString());
            }
        }

        //if auth is a jwt token, provide as user+token credentials
        if (Boolean.TRUE.equals(properties.getUseJwtToken()) && token instanceof JwtAuthenticationToken jwtToken) {
            String tokenValue = jwtToken.getToken().getTokenValue();
            if (StringUtils.hasText(tokenValue)) {
                return new TrinoDbCredentials(null, null, tokenValue);
            }
        }

        return null;
    }

    @Override
    public Credentials get(@NotNull UserAuthentication<?> auth) {
        //nothing to do
        return null;
    }
}
