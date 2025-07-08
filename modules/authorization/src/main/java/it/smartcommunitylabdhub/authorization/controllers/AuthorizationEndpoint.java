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

package it.smartcommunitylabdhub.authorization.controllers;

import it.smartcommunitylabdhub.authorization.UserAuthenticationManager;
import it.smartcommunitylabdhub.authorization.UserAuthenticationManagerBuilder;
import it.smartcommunitylabdhub.authorization.model.AuthorizationRequest;
import it.smartcommunitylabdhub.authorization.model.AuthorizationResponse;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.providers.NoOpAuthenticationProvider;
import it.smartcommunitylabdhub.authorization.repositories.AuthorizationRequestStore;
import it.smartcommunitylabdhub.authorization.utils.SecureKeyGenerator;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

//TODO add error handler
@RestController
@Slf4j
public class AuthorizationEndpoint implements InitializingBean {

    public static final String AUTHORIZE_URL = "/auth/authorize";
    private static final int CODE_LENGTH = 12;
    private static final int MIN_STATE_LENGTH = 5;
    private static final int AUTH_REQUEST_DURATION = 300;

    @Value("${jwt.client-id}")
    private String jwtClientId;

    @Value("${jwt.redirect-uris}")
    private List<String> jwtRedirectUris;

    @Autowired
    private SecurityProperties securityProperties;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private AuthorizationRequestStore requestStore;

    @Autowired
    private UserAuthenticationManagerBuilder authenticationManagerBuilder;

    private UserAuthenticationManager authenticationManager;

    //keygen
    private StringKeyGenerator keyGenerator = new SecureKeyGenerator(CODE_LENGTH);
    private String issuer;

    private RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(keyGenerator, "code generator can not be null");
        Assert.notNull(applicationProperties, AUTHORIZE_URL);
        Assert.notNull(authenticationManagerBuilder, "auth manager builder is required");
        if (securityProperties.isOidcAuthEnabled()) {
            Assert.notNull(requestStore, "request store can not be null");
        }

        this.issuer = applicationProperties.getEndpoint();
        this.authenticationManager = this.authenticationManagerBuilder.build(new NoOpAuthenticationProvider());
    }

    @RequestMapping(value = "auth/test", method = { RequestMethod.POST, RequestMethod.GET })
    public AbstractAuthenticationToken debug(@RequestParam Map<String, String> parameters, Authentication auth) {
        if (auth instanceof AbstractAuthenticationToken) {
            return (AbstractAuthenticationToken) auth;
        }

        return null;
    }

    @RequestMapping(value = AUTHORIZE_URL, method = { RequestMethod.POST, RequestMethod.GET })
    public void authorize(
        @RequestParam Map<String, String> parameters,
        @CurrentSecurityContext SecurityContext securityContext,
        HttpServletRequest httpRequest,
        HttpServletResponse httpResponse
    ) throws IOException {
        if (!securityProperties.isOidcAuthEnabled()) {
            throw new UnsupportedOperationException();
        }

        Authentication authentication = securityContext.getAuthentication();

        //resolve user authentication
        if (
            authentication == null ||
            !(authentication.isAuthenticated()) ||
            !(authentication instanceof AbstractAuthenticationToken)
        ) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        UserAuthentication<?> user = authenticationManager.authenticate(authentication);

        //sanity check
        String responseType = parameters.get(OAuth2ParameterNames.RESPONSE_TYPE);
        if (!"code".equals(responseType)) {
            throw new IllegalArgumentException("invalid response type");
        }

        log.debug("authorize request for {}", authentication.getName());

        //read parameters
        String clientId = parameters.get(OAuth2ParameterNames.CLIENT_ID);
        if (!jwtClientId.equals(clientId)) {
            throw new IllegalArgumentException("invalid client");
        }

        String state = parameters.get(OAuth2ParameterNames.STATE);
        if (!StringUtils.hasText(state) || state.length() < MIN_STATE_LENGTH) {
            throw new IllegalArgumentException("invalid state");
        }

        String scope = parameters.get(OAuth2ParameterNames.SCOPE);

        String redirectUri = parameters.get(OAuth2ParameterNames.REDIRECT_URI);
        if (!StringUtils.hasText(redirectUri)) {
            throw new IllegalArgumentException("missing redirect_uri");
        }

        //redirect must match allowed
        boolean matches = matches(redirectUri);
        if (!matches) {
            throw new IllegalArgumentException("invalid redirect_uri");
        }

        //pkce
        String codeChallenge = parameters.get(PkceParameterNames.CODE_CHALLENGE);
        String codeChallengeMethod = parameters.get(PkceParameterNames.CODE_CHALLENGE_METHOD);

        if (codeChallengeMethod != null && !"S256".equals(codeChallengeMethod)) {
            throw new IllegalArgumentException("invalid code challenge method");
        }

        //generate code and store request
        String code = keyGenerator.generateKey();
        Instant now = Instant.now();

        AuthorizationRequest request = AuthorizationRequest
            .builder()
            .clientId(clientId)
            .redirectUri(redirectUri)
            .code(code)
            .scope(scope)
            .state(state)
            .codeChallenge(codeChallenge)
            .codeChallengeMethod(codeChallengeMethod)
            .username(user.getUsername())
            .issuedTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(AUTH_REQUEST_DURATION)))
            .build();

        if (log.isTraceEnabled()) {
            log.trace("request: {}", request);
        }

        String key = requestStore.store(request, user);

        log.debug("stored auth request for {} with key {}", authentication.getName(), key);

        //build response
        AuthorizationResponse response = AuthorizationResponse
            .builder()
            .code(code)
            .state(state)
            .issuer(issuer)
            .redirectUrl(redirectUri)
            .build();
        if (log.isTraceEnabled()) {
            log.trace("response: {}", response);
        }

        //redirect
        redirectStrategy.sendRedirect(httpRequest, httpResponse, response.buildRedirectUri());
    }

    private boolean matches(String redirectUri) {
        //simple matcher to authorize requests
        if (jwtRedirectUris == null || jwtRedirectUris.isEmpty() || redirectUri == null) {
            return false;
        }

        //valid uri
        try {
            URI uri = new URI(redirectUri);

            //exact match
            Optional<String> exact = jwtRedirectUris
                .stream()
                .filter(u -> redirectUri.toLowerCase().equals(u.toLowerCase()))
                .findFirst();
            if (exact.isPresent()) {
                return true;
            }

            //localhost relaxed match: any port/path is valid
            String localhost = "http://localhost:*";
            Optional<String> localhostMatch = jwtRedirectUris
                .stream()
                .filter(u -> u.toLowerCase().equals(localhost))
                .findFirst();

            if (localhostMatch.isPresent() && uri.getHost().equals("localhost")) {
                return true;
            }

            return false;
        } catch (URISyntaxException e) {
            //invalid uri
            return false;
        }
    }
}
