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

import it.smartcommunitylabdhub.authorization.model.AuthorizationRequest;
import it.smartcommunitylabdhub.authorization.model.AuthorizationResponse;
import it.smartcommunitylabdhub.authorization.repositories.AuthorizationRequestStore;
import it.smartcommunitylabdhub.authorization.utils.SecureKeyGenerator;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
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

    public static final String TOKEN_URL = "/auth/authorize";
    private static final int CODE_LENGTH = 12;
    private static final int MIN_STATE_LENGTH = 5;

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

    //keygen
    private StringKeyGenerator keyGenerator = new SecureKeyGenerator(CODE_LENGTH);
    private String issuer;

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(keyGenerator, "code generator can not be null");

        if (securityProperties.isOidcAuthEnabled()) {
            Assert.notNull(requestStore, "request store can not be null");
        }

        this.issuer = applicationProperties.getEndpoint();
    }

    @RequestMapping(value = TOKEN_URL, method = { RequestMethod.POST, RequestMethod.GET })
    public AuthorizationResponse authorize(
        @RequestParam Map<String, String> parameters,
        @CurrentSecurityContext SecurityContext securityContext
    ) {
        if (!securityProperties.isOidcAuthEnabled()) {
            throw new UnsupportedOperationException();
        }

        Authentication authentication = securityContext.getAuthentication();

        //resolve user authentication
        if (authentication == null || !(authentication.isAuthenticated())) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        //sanity check
        String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.AUTHORIZATION_CODE.getValue().equals(grantType)) {
            throw new IllegalArgumentException("invalid grant type");
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

        String redirectUrl = parameters.get(OAuth2ParameterNames.REDIRECT_URI);
        if (!StringUtils.hasText(redirectUrl)) {
            throw new IllegalArgumentException("missing redirect_uri");
        }

        //redirect must match allowed
        boolean matches = matches(redirectUrl);
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

        AuthorizationRequest request = AuthorizationRequest
            .builder()
            .clientId(clientId)
            .redirectUrl(redirectUrl)
            .code(code)
            .state(state)
            .codeChallenge(codeChallenge)
            .codeChallengeMethod(codeChallengeMethod)
            .redirectUrl(code)
            .build();

        if (log.isTraceEnabled()) {
            log.trace("request: {}", request);
        }

        String key = requestStore.store(request);

        log.debug("stored auth request for {} with key {}", authentication.getName(), key);

        //build response
        AuthorizationResponse response = AuthorizationResponse.builder().code(code).state(state).issuer(issuer).build();
        if (log.isTraceEnabled()) {
            log.trace("response: {}", response);
        }

        return response;
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
