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

package it.smartcommunitylabdhub.authorization.grants;

import it.smartcommunitylabdhub.authorization.model.TokenResponse;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.TokenService;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class ClientCredentialsGranter implements TokenGranter {

    private String clientId;
    private String clientSecret;

    private final TokenService tokenService;

    public ClientCredentialsGranter(TokenService jwtTokenService) {
        Assert.notNull(jwtTokenService, "token service is required");
        this.tokenService = jwtTokenService;
    }

    @Autowired
    public void setClientId(@Value("${jwt.client-id}") String clientId) {
        this.clientId = clientId;
    }

    @Autowired
    public void setClientSecret(@Value("${jwt.client-secret}") String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @Override
    public TokenResponse grant(@NotNull Map<String, String> parameters, Authentication authentication) {
        //client credentials *requires* basic auth or form auth
        if (
            authentication == null ||
            (!(authentication instanceof UsernamePasswordAuthenticationToken) &&
                !(authentication instanceof AnonymousAuthenticationToken))
        ) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        //for client credentials to mimic admin user client *must* match authenticated user
        String cId = null;
        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) authentication;
            //validate only name
            cId = auth.getName();
        } else if (authentication instanceof AnonymousAuthenticationToken) {
            //validate client and secret
            cId = parameters.get(OAuth2ParameterNames.CLIENT_ID);
            String cSecret = parameters.get(OAuth2ParameterNames.CLIENT_SECRET);

            if (clientSecret == null || !clientSecret.equals(cSecret)) {
                throw new InsufficientAuthenticationException("Invalid client authentication");
            }
        }

        if (clientId != null && !clientId.equals(cId)) {
            throw new InsufficientAuthenticationException("Invalid client authentication");
        }

        //sanity check
        String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        if (!type().getValue().equals(grantType)) {
            throw new IllegalArgumentException("invalid grant type");
        }

        Set<String> scopes = Set.of(
            StringUtils.delimitedListToStringArray(
                parameters.getOrDefault(OAuth2ParameterNames.SCOPE, "openid profile"),
                " "
            )
        );

        boolean withCredentials = scopes != null && scopes.contains("credentials");

        log.debug("client token request for {}", authentication.getName());

        //generate as admin user == client
        UserAuthentication<UsernamePasswordAuthenticationToken> user = new UserAuthentication<>(
            new UsernamePasswordAuthenticationToken(clientId, null, authentication.getAuthorities()),
            clientId,
            Collections.singleton(new SimpleGrantedAuthority("ROLE_ADMIN"))
        );

        //full credentials without refresh
        return tokenService.generateAccessToken(user, withCredentials, false, false);
    }

    @Override
    public AuthorizationGrantType type() {
        return AuthorizationGrantType.CLIENT_CREDENTIALS;
    }
}
