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

package it.smartcommunitylabdhub.authorization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.infrastructure.AbstractConfiguration;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OAuth2EnvConfig extends AbstractConfiguration {

    @JsonProperty("oauth2_issuer")
    private String issuer;

    @JsonProperty("oauth2_jwks_uri")
    private String jwksUri;

    @JsonProperty("oauth2_authorization_endpoint")
    private String authorizationEndpoint;

    @JsonProperty("oauth2_userinfo_endpoint")
    private String userinfoEndpoint;

    @JsonProperty("oauth2_token_endpoint")
    private String tokenEndpoint;

    @JsonProperty("oauth2_token_endpoint_auth_methods_supported")
    private Set<String> tokenEndpointAuthMethodsSupported;

    @JsonProperty("oauth2_response_types_supported")
    private Set<String> responseTypesSupported;

    @JsonProperty("oauth2_grant_types_supported")
    private Set<String> grantTypesSupported;

    @JsonProperty("oauth2_scopes_supported")
    private Set<String> scopesSupported;

    public static OAuth2EnvConfig from(OpenIdConfig config) {
        return OAuth2EnvConfig
            .builder()
            .issuer(config.getIssuer())
            .jwksUri(config.getJwksUri())
            .authorizationEndpoint(config.getAuthorizationEndpoint())
            .userinfoEndpoint(config.getUserinfoEndpoint())
            .tokenEndpoint(config.getTokenEndpoint())
            .tokenEndpointAuthMethodsSupported(config.getTokenEndpointAuthMethodsSupported())
            .responseTypesSupported(config.getResponseTypesSupported())
            .grantTypesSupported(config.getGrantTypesSupported())
            .scopesSupported(config.getScopesSupported())
            .build();
    }
}
