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
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.authorization.services.TokenService;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class RefreshTokenGranter implements TokenGranter {

    private String clientId;

    private final TokenService tokenService;
    private final JwtTokenService jwtTokenService;

    public RefreshTokenGranter(TokenService tokenService, JwtTokenService jwtTokenService) {
        Assert.notNull(tokenService, "token service is required");
        Assert.notNull(jwtTokenService, "token service is required");
        this.tokenService = tokenService;
        this.jwtTokenService = jwtTokenService;
    }

    @Autowired
    public void setClientId(@Value("${jwt.client-id}") String clientId) {
        this.clientId = clientId;
    }

    @Override
    public TokenResponse grant(@NotNull Map<String, String> parameters, Authentication authentication) {
        //refresh token is usable without credentials
        //if provided client *must* match authenticated user
        if (authentication != null && authentication instanceof UsernamePasswordAuthenticationToken) {
            UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) authentication;
            if (clientId != null && !clientId.equals(auth.getName())) {
                throw new InsufficientAuthenticationException("Invalid client authentication");
            }
        }

        //sanity check
        String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        if (!type().getValue().equals(grantType)) {
            throw new IllegalArgumentException("invalid grant type");
        }

        String token = parameters.get(OAuth2ParameterNames.REFRESH_TOKEN);
        if (token == null) {
            throw new IllegalArgumentException("invalid or missing refresh_token");
        }

        String cid = parameters.get(OAuth2ParameterNames.CLIENT_ID);
        if (cid == null || !clientId.equals(cid)) {
            throw new IllegalArgumentException("invalid or missing client_id");
        }

        //TODO evaluate if scopes are valid against originally requested
        Set<String> scopes = Set.of(
            StringUtils.delimitedListToStringArray(
                parameters.getOrDefault(OAuth2ParameterNames.SCOPE, "openid profile"),
                " "
            )
        );

        boolean withCredentials = scopes != null && scopes.contains("credentials");

        log.debug("refresh token request for {}", cid);
        if (log.isTraceEnabled()) {
            log.trace("refresh token {}", token);
        }

        try {
            // Consume refresh token
            UserAuthentication<?> user = jwtTokenService.consume(token);
            if (user == null) {
                throw new IllegalArgumentException("invalid token");
            }
            if (!user.isAuthenticated()) {
                throw new IllegalArgumentException("invalid token");
            }

            //generate full credentials + new refresh
            return tokenService.generateAccessToken(user, withCredentials, true, false);
        } catch (AuthenticationException ae) {
            throw new IllegalArgumentException("invalid or missing refresh_token");
        }
    }

    @Override
    public AuthorizationGrantType type() {
        return AuthorizationGrantType.REFRESH_TOKEN;
    }
}
