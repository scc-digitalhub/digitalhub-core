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

import it.smartcommunitylabdhub.authorization.model.AuthorizationRequest;
import it.smartcommunitylabdhub.authorization.model.TokenResponse;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.repositories.AuthorizationRequestStore;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.authorization.services.TokenService;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.endpoint.PkceParameterNames;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class AuthorizationCodeGranter implements TokenGranter {

    private String clientId;
    private String clientSecret;

    private AuthorizationRequestStore requestStore;

    private final TokenService tokenService;

    public AuthorizationCodeGranter(TokenService tokenService, JwtTokenService jwtTokenService) {
        Assert.notNull(tokenService, "token service is required");
        Assert.notNull(jwtTokenService, "token service is required");
        this.tokenService = tokenService;
    }

    @Autowired
    public void setClientId(@Value("${jwt.client-id}") String clientId) {
        this.clientId = clientId;
    }

    @Autowired
    public void setClientSecret(@Value("${jwt.client-secret}") String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @Autowired
    public void setRequestStore(AuthorizationRequestStore requestStore) {
        this.requestStore = requestStore;
    }

    @Override
    public TokenResponse grant(@NotNull Map<String, String> parameters, Authentication authentication) {
        //auth token requires client authentication either basic or pkce
        //we should already have a user auth here
        //TODO support client basic auth + form auth

        if (authentication == null || !(authentication instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid user authentication");
        }

        UserAuthentication<?> user = (UserAuthentication<?>) authentication;

        //require PKCE or client secret, we validate later
        if (
            !StringUtils.hasText(parameters.get(PkceParameterNames.CODE_VERIFIER)) &&
            !StringUtils.hasText(parameters.get(OAuth2ParameterNames.CLIENT_SECRET))
        ) {
            throw new InsufficientAuthenticationException("Invalid client authentication");
        }

        //sanity check
        String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        if (!type().getValue().equals(grantType)) {
            throw new IllegalArgumentException("invalid grant type");
        }

        String state = parameters.get(OAuth2ParameterNames.STATE);
        if (state == null) {
            throw new IllegalArgumentException("invalid or missing state");
        }

        //secret auth
        String cid = parameters.get(OAuth2ParameterNames.CLIENT_ID);
        if (cid == null || !clientId.equals(cid)) {
            throw new IllegalArgumentException("invalid or missing client_id");
        }

        String cSecret = parameters.get(OAuth2ParameterNames.CLIENT_SECRET);
        if (cSecret != null && !clientSecret.equals(cSecret)) {
            throw new InsufficientAuthenticationException("Invalid client authentication");
        }

        String redirectUrl = parameters.get(OAuth2ParameterNames.REDIRECT_URI);
        if (redirectUrl == null) {
            throw new IllegalArgumentException("invalid or missing redirect_uri");
        }

        //recover request from key
        AuthorizationRequest request = requestStore.find(state);
        if (request == null) {
            throw new IllegalArgumentException("invalid request");
        }

        if (!request.getClientId().equals(cid) || !request.getRedirectUrl().equals(redirectUrl)) {
            //mismatch
            throw new IllegalArgumentException("invalid request");
        }

        //check PKCE
        String codeVerifier = parameters.get(PkceParameterNames.CODE_VERIFIER);
        String codeChallenge = request.getCodeChallenge();
        String codeChallengeMethod = request.getCodeChallengeMethod();

        if (codeChallengeMethod != null && !"S256".equals(codeChallengeMethod)) {
            throw new IllegalArgumentException("invalid code challenge method");
        }

        if (StringUtils.hasText(codeVerifier) && !StringUtils.hasText(codeChallenge)) {
            throw new IllegalArgumentException("invalid code challenge");
        }

        if (
            StringUtils.hasText(codeVerifier) &&
            StringUtils.hasText(codeChallenge) &&
            !createS256Hash(codeChallenge).equals(codeVerifier)
        ) {
            //invalid code
            throw new IllegalArgumentException("invalid code verifier");
        }

        //valid request, consume
        request = requestStore.consume(state);
        if (request == null) {
            //concurrently consumed?
            throw new IllegalArgumentException("invalid request");
        }

        String code = request.getCode();

        log.debug("auth code token request for {}", cid);
        if (log.isTraceEnabled()) {
            log.trace("code {}", code);
        }

        try {
            if (!user.isAuthenticated()) {
                throw new IllegalArgumentException("invalid auth");
            }

            //generate full credentials + new refresh
            return tokenService.generateToken(user, true);
        } catch (AuthenticationException ae) {
            throw new IllegalArgumentException("invalid request");
        }
    }

    @Override
    public AuthorizationGrantType type() {
        return AuthorizationGrantType.AUTHORIZATION_CODE;
    }

    private static String createS256Hash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            return "______ERROR";
        }
    }
}
