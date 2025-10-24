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

package it.smartcommunitylabdhub.authorization.controllers;

import it.smartcommunitylabdhub.authorization.model.OAuth2EnvConfig;
import it.smartcommunitylabdhub.authorization.model.OpenIdConfig;
import it.smartcommunitylabdhub.authorization.model.OpenIdConfig.OpenIdConfigBuilder;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import java.io.Serializable;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OAuth2ConfigurationEndpoint implements ConfigurationProvider {

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private SecurityProperties securityProperties;

    @Value("${jwt.cache-control}")
    private String cacheControl;

    private OpenIdConfig config = null;

    @GetMapping(value = { "/.well-known/openid-configuration", "/.well-known/oauth-authorization-server" })
    public ResponseEntity<Map<String, Serializable>> getConfiguration() {
        if (!securityProperties.isRequired()) {
            throw new UnsupportedOperationException();
        }

        if (config == null) {
            config = generate();
        }

        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, cacheControl).body(config.toMap());
    }

    private OpenIdConfig generate() {
        /*
         * OpenID Provider Metadata
         * https://openid.net/specs/openid-connect-discovery-1_0.html#ProviderMetadata
         */

        String baseUrl = applicationProperties.getEndpoint();
        OpenIdConfigBuilder builder = OpenIdConfig.builder();

        builder.issuer(baseUrl);
        builder.jwksUri(baseUrl + JWKSEndpoint.JWKS_URL);
        builder.responseTypesSupported(Set.of("code"));

        List<String> grantTypes = Stream
            .of(
                AuthorizationGrantType.CLIENT_CREDENTIALS,
                AuthorizationGrantType.REFRESH_TOKEN,
                AuthorizationGrantType.TOKEN_EXCHANGE
            )
            .map(t -> t.getValue())
            .toList();

        if (securityProperties.isOidcAuthEnabled()) {
            grantTypes =
                Stream
                    .of(
                        AuthorizationGrantType.CLIENT_CREDENTIALS,
                        AuthorizationGrantType.REFRESH_TOKEN,
                        AuthorizationGrantType.AUTHORIZATION_CODE,
                        AuthorizationGrantType.TOKEN_EXCHANGE
                    )
                    .map(t -> t.getValue())
                    .toList();

            builder.authorizationEndpoint(baseUrl + AuthorizationEndpoint.AUTHORIZE_URL);
            builder.userinfoEndpoint(baseUrl + UserInfoEndpoint.USERINFO_URL);
        }

        builder.grantTypesSupported(new HashSet<>(grantTypes));
        builder.scopesSupported(Set.of("openid", "profile", "credentials", "offline_access"));

        builder.tokenEndpoint(baseUrl + TokenEndpoint.TOKEN_URL);
        Set<String> authMethods = Set.of("client_secret_basic", "client_secret_post", "none");
        builder.tokenEndpointAuthMethodsSupported(authMethods);

        return builder.build();
    }

    @Override
    @Nullable
    public Configuration getConfig() {
        if (config == null) {
            config = generate();
        }

        return OAuth2EnvConfig.from(config);
    }
}
