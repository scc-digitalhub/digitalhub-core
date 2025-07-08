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

package it.smartcommunitylabdhub.authorization.services;

import com.nimbusds.jwt.SignedJWT;
import it.smartcommunitylabdhub.authorization.model.PersonalAccessToken;
import it.smartcommunitylabdhub.authorization.model.RefreshToken;
import it.smartcommunitylabdhub.authorization.model.TokenResponse;
import it.smartcommunitylabdhub.authorization.model.TokenResponse.TokenResponseBuilder;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.providers.AccessCredentialsProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CredentialsServiceImpl implements CredentialsService, TokenService {

    private final List<CredentialsProvider> providers;
    private JwtTokenService jwtTokenService;

    @Autowired(required = false)
    public void setJwtTokenService(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    public CredentialsServiceImpl(Collection<CredentialsProvider> providers) {
        log.debug("Initialize service with providers");

        if (providers != null) {
            if (log.isTraceEnabled()) {
                log.trace("providers: {}", providers);
            }

            this.providers = Collections.unmodifiableList(new ArrayList<>(providers));
        } else {
            this.providers = Collections.emptyList();
        }
    }

    public List<Credentials> getCredentials(@NotNull UserAuthentication<?> auth) {
        log.debug("get credentials from providers for user {}", auth.getName());
        List<Credentials> credentials = providers.stream().map(p -> p.get(auth)).filter(c -> c != null).toList();

        if (log.isTraceEnabled()) {
            log.trace("credentials: {}", credentials);
        }

        return credentials;
    }

    //DISABLED: do not store credentials in user auth because we may serialize it
    // public List<Credentials> getCredentials(@NotNull UserAuthentication<?> auth, boolean refresh) {
    //     if (refresh || auth.getCredentials() == null || auth.getCredentials().isEmpty()) {
    //         log.debug("get credentials from providers for user {}", auth.getName());
    //         List<Credentials> credentials = providers.stream().map(p -> p.get(auth)).toList();

    //         //cache
    //         auth.setCredentials(credentials);

    //         return credentials;
    //     }

    //     //use cached values
    //     //note: we don't evaluate duration because core is stateless, so max duration of cache is call duration
    //     log.debug("use cached credentials for user {}", auth.getName());

    //     return auth.getCredentials();
    // }

    @Override
    public TokenResponse generateAccessToken(
        @NotNull UserAuthentication<?> authentication,
        boolean withCredentials,
        boolean withRefresh
    ) { //by default tokens are  invalid for exchange
        return generateAccessToken(authentication, withCredentials, withRefresh, false);
    }

    @Override
    public TokenResponse generateAccessToken(
        @NotNull UserAuthentication<?> authentication,
        boolean withCredentials,
        boolean withRefresh,
        boolean withExchange
    ) {
        log.info("generate credentials for user {}", authentication.getName());

        List<CredentialsProvider> credentialsProviders = providers
            .stream()
            //skip access credentials, we generate tokens separately
            .filter(p -> !(p instanceof AccessCredentialsProvider))
            .toList();

        if (withCredentials) {
            //refresh credentials before token generation
            List<Credentials> credentials = credentialsProviders
                .stream()
                .map(p -> p.process(authentication))
                .filter(c -> c != null)
                .collect(Collectors.toList());
            authentication.setCredentials(credentials);
        }

        //response
        TokenResponseBuilder response = TokenResponse.builder();

        if (jwtTokenService != null) {
            List<String> audiences = withExchange
                ? List.of(jwtTokenService.getAudience(), jwtTokenService.getExchangeAudience())
                : List.of(jwtTokenService.getAudience());
            SignedJWT accessToken = jwtTokenService.generateAccessToken(authentication, audiences);

            //build token response
            response.accessToken(accessToken.serialize());
            response.idToken(accessToken.serialize());
            response.clientId(jwtTokenService.getClientId());
            response.expiration(jwtTokenService.getAccessTokenDuration());
            response.issuer(jwtTokenService.getIssuer());

            if (withRefresh) {
                String refreshToken = jwtTokenService.generateRefreshToken(authentication, accessToken);
                response.refreshToken(refreshToken);
            }
        }

        if (withCredentials) {
            //derive full credentials as map
            response.credentials(
                credentialsProviders
                    .stream()
                    .map(p -> p.get(authentication))
                    .filter(c -> c != null)
                    .flatMap(c -> c.toMap().entrySet().stream())
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()))
            );
        }

        return response.build();
    }

    @Override
    public TokenResponse generatePersonalAccessToken(
        @NotNull UserAuthentication<?> authentication,
        @Nullable List<String> scopes
    ) {
        return generatePersonalAccessToken(authentication, null, scopes);
    }

    @Override
    public TokenResponse generatePersonalAccessToken(
        @NotNull UserAuthentication<?> authentication,
        @Nullable String name,
        @Nullable List<String> scopes
    ) {
        //response
        TokenResponseBuilder response = TokenResponse.builder();

        if (jwtTokenService != null) {
            String accessToken = jwtTokenService.generatePersonalAccessToken(
                authentication,
                name,
                scopes != null ? Set.copyOf(scopes) : null
            );

            //build token response
            response.accessToken(accessToken);
            response.clientId(jwtTokenService.getClientId());
            response.expiration(jwtTokenService.getPersonalTokenDuration());
            response.issuer(jwtTokenService.getIssuer());
        }

        return response.build();
    }

    @Override
    public PersonalAccessToken getPersonalAccessToken(
        @NotNull UserAuthentication<?> authentication,
        @NotNull String tokenId
    ) {
        if (jwtTokenService == null) {
            return null;
        }

        return jwtTokenService.findPersonalAccessToken(authentication.getName(), tokenId);
    }

    @Override
    public List<PersonalAccessToken> getPersonalAccessTokens(@NotNull UserAuthentication<?> authentication) {
        if (jwtTokenService == null) {
            return Collections.emptyList();
        }

        return jwtTokenService.findPersonalAccessTokens(authentication.getName());
    }

    @Override
    public void revokePersonalAccessToken(@NotNull UserAuthentication<?> authentication, @NotNull String tokenId) {
        if (jwtTokenService != null) {
            jwtTokenService.deletePersonalAccessToken(authentication.getName(), tokenId);
        }
    }

    @Override
    public RefreshToken getRefreshToken(@NotNull UserAuthentication<?> authentication, @NotNull String tokenId) {
        if (jwtTokenService == null) {
            return null;
        }

        return jwtTokenService.findRefreshToken(authentication.getName(), tokenId);
    }

    @Override
    public List<RefreshToken> getRefreshTokens(@NotNull UserAuthentication<?> authentication) {
        if (jwtTokenService == null) {
            return Collections.emptyList();
        }

        return jwtTokenService.findRefreshTokens(authentication.getName());
    }

    @Override
    public void revokeRefreshToken(@NotNull UserAuthentication<?> authentication, @NotNull String tokenId) {
        if (jwtTokenService != null) {
            jwtTokenService.deleteRefreshToken(authentication.getName(), tokenId);
        }
    }
}
