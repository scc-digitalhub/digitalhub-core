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

import it.smartcommunitylabdhub.authorization.UserAuthenticationManager;
import it.smartcommunitylabdhub.authorization.UserAuthenticationManagerBuilder;
import it.smartcommunitylabdhub.authorization.model.TokenResponse;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.authorization.services.TokenService;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties.JwtAuthenticationProperties;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.OpaqueTokenAuthenticationProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class TokenExchangeGranter implements TokenGranter, InitializingBean {

    public static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";
    public static final String PAT_TYPE = "urn:ietf:params:oauth:token-type:pat";

    private String clientId;
    private SecurityProperties securityProperties;
    private UserAuthenticationManager authenticationManager;
    private UserAuthenticationManagerBuilder authenticationManagerBuilder;

    private JwtTokenService jwtTokenService;

    private final TokenService tokenService;

    public TokenExchangeGranter(TokenService jwtTokenService) {
        Assert.notNull(jwtTokenService, "token service is required");
        this.tokenService = jwtTokenService;
    }

    @Autowired
    public void setSecurityProperties(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties;
    }

    @Autowired
    public void setClientId(@Value("${jwt.client-id}") String clientId) {
        this.clientId = clientId;
    }

    @Autowired
    public void setAuthenticationManagerBuilder(UserAuthenticationManagerBuilder authenticationManagerBuilder) {
        this.authenticationManagerBuilder = authenticationManagerBuilder;
    }

    @Autowired
    public void setJwtTokenService(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(securityProperties, "security properties are required");
        Assert.notNull(authenticationManagerBuilder, "auth manager builder is required");

        List<AuthenticationProvider> providers = new ArrayList<>();

        //internal provider
        if (jwtTokenService != null) {
            JwtAuthenticationProvider coreJwtAuthProvider = new JwtAuthenticationProvider(jwtTokenService.getDecoder());
            coreJwtAuthProvider.setJwtAuthenticationConverter(jwtTokenService.getAuthenticationConverter());
            providers.add(coreJwtAuthProvider);

            // enable PAT auth provider
            OpaqueTokenAuthenticationProvider patAuthProvider = new OpaqueTokenAuthenticationProvider(
                jwtTokenService.getPersonalAccessTokenIntrospector()
            );
            patAuthProvider.setAuthenticationConverter(jwtTokenService.getPersonalAccessTokenConverter());
            providers.add(patAuthProvider);
        }

        //build ext provider when supported
        if (securityProperties.isJwtAuthEnabled()) {
            JwtAuthenticationProperties props = securityProperties.getJwt();

            JwtDecoder decoder = jwtDecoder(props.getIssuerUri(), props.getAudience());
            JwtAuthenticationProvider provider = new JwtAuthenticationProvider(decoder);
            provider.setJwtAuthenticationConverter((Jwt jwt) -> {
                Set<GrantedAuthority> authorities = new HashSet<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

                //read roles from token
                if (StringUtils.hasText(props.getClaim()) && jwt.hasClaim(props.getClaim())) {
                    List<String> roles = jwt.getClaimAsStringList(props.getClaim());
                    if (roles != null) {
                        roles.forEach(r -> {
                            if ("ROLE_ADMIN".equals(r) || r.contains(":")) {
                                //use as is
                                authorities.add(new SimpleGrantedAuthority(r));
                            } else {
                                //derive a scoped USER role
                                authorities.add(new SimpleGrantedAuthority(r + ":ROLE_USER"));
                            }
                        });
                    }
                }

                //principalName
                String username = jwt.getClaimAsString(props.getUsername());

                //fallback to SUB if missing
                if (!StringUtils.hasText(username)) {
                    username = jwt.getSubject();
                }

                return new JwtAuthenticationToken(jwt, authorities, username);
            });

            providers.add(provider);
        }

        //build manager
        this.authenticationManager =
            providers != null && !providers.isEmpty() ? authenticationManagerBuilder.build(providers) : null;
    }

    private static JwtDecoder jwtDecoder(String issuer, String audience) {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD,
            (aud -> aud != null && aud.contains(audience))
        );

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }

    @Override
    public TokenResponse grant(@NotNull Map<String, String> parameters, Authentication authentication) {
        if (authenticationManager == null) {
            throw new UnsupportedOperationException();
        }

        //token exchange *requires* basic auth OR anonymous
        if (
            authentication == null ||
            (!(authentication instanceof UsernamePasswordAuthenticationToken) &&
                !(authentication instanceof AnonymousAuthenticationToken))
        ) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        if (authentication instanceof UsernamePasswordAuthenticationToken) {
            //client *must* match authenticated user
            UsernamePasswordAuthenticationToken clientAuth = (UsernamePasswordAuthenticationToken) authentication;
            if (clientId != null && !clientId.equals(clientAuth.getName())) {
                throw new InsufficientAuthenticationException("Invalid client authentication");
            }
        }

        //sanity check
        String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        if (!type().getValue().equals(grantType)) {
            throw new IllegalArgumentException("invalid grant type");
        }

        String cid = parameters.get("client_id");
        if (cid != null && !clientId.equals(cid)) {
            throw new IllegalArgumentException("invalid or missing client_id");
        }

        Set<String> scopes = Set.of(
            StringUtils.delimitedListToStringArray(
                parameters.getOrDefault(OAuth2ParameterNames.SCOPE, "openid profile"),
                " "
            )
        );

        boolean withCredentials = scopes != null && scopes.contains("credentials");
        boolean withRefresh = scopes != null && scopes.contains("offline_access");

        //validate token
        String token = parameters.get("subject_token");
        if (token == null) {
            throw new IllegalArgumentException("invalid or missing subject_token");
        }
        String subjectTokenType = parameters.getOrDefault("subject_token_type", ACCESS_TOKEN_TYPE);
        if (!ACCESS_TOKEN_TYPE.equals(subjectTokenType) && !PAT_TYPE.equals(subjectTokenType)) {
            throw new IllegalArgumentException("invalid or missing subject_token_type");
        }

        String tokenType = parameters.getOrDefault("requested_token_type", ACCESS_TOKEN_TYPE);
        if (!ACCESS_TOKEN_TYPE.equals(tokenType) && !PAT_TYPE.equals(tokenType)) {
            throw new IllegalArgumentException("invalid or missing requested_token_type");
        }

        //sanity check: disable refresh token for PAT
        if (PAT_TYPE.equals(tokenType) && withRefresh) {
            throw new IllegalArgumentException("offline_access scope not allowed for PAT");
        }

        log.debug("exchange token request for {}", tokenType);
        if (log.isTraceEnabled()) {
            log.trace("subject token {}", token);
        }

        try {
            BearerTokenAuthenticationToken request = new BearerTokenAuthenticationToken(token);

            Authentication auth = authenticationManager.authenticate(request);
            if (!auth.isAuthenticated()) {
                throw new IllegalArgumentException("invalid or missing subject_token");
            }

            log.debug("exchange token request resolved for {} via provider", auth.getName());

            //token is valid, use as context for generation
            //we expect a UserAuth
            UserAuthentication<?> user = (UserAuthentication<?>) auth;

            if (ACCESS_TOKEN_TYPE.equals(tokenType)) {
                //check subject token in detail
                if (PAT_TYPE.equals(subjectTokenType)) {
                    //no refresh token for PAT
                    withRefresh = false;
                }

                //check token submitted
                if (
                    user.getToken() instanceof BearerTokenAuthenticationToken &&
                    ((BearerTokenAuthenticationToken) user.getToken()) instanceof OAuth2AuthenticatedPrincipal
                ) {
                    if (!PAT_TYPE.equals(subjectTokenType)) {
                        //authenticated with PAT as access token, invalid
                        throw new IllegalArgumentException("invalid subject token");
                    }

                    //no refresh token for PAT
                    withRefresh = false;

                    OAuth2AuthenticatedPrincipal principal =
                        ((OAuth2AuthenticatedPrincipal) (BearerTokenAuthenticationToken) user.getToken());

                    //grant credentials only if originally authorized
                    withCredentials =
                        withCredentials &&
                        principal.getAttribute(OAuth2ParameterNames.SCOPE) != null &&
                        StringUtils
                            .commaDelimitedListToSet(principal.getAttribute(OAuth2ParameterNames.SCOPE))
                            .contains("credentials");
                }

                if (
                    user.getToken() instanceof JwtAuthenticationToken &&
                    jwtTokenService
                        .getIssuer()
                        .equals(((JwtAuthenticationToken) user.getToken()).getToken().getClaim(JwtClaimNames.ISS))
                ) {
                    //no exchange for internal token
                    throw new IllegalArgumentException("invalid subject token");
                }

                //full credentials + refresh
                TokenResponse response = tokenService.generateAccessToken(user, withCredentials, withRefresh, false);
                response.setIssuedTokenType(ACCESS_TOKEN_TYPE);

                return response;
            }
            if (PAT_TYPE.equals(tokenType)) {
                //authorize only internal token with explicit audience
                if (
                    user.getToken() instanceof JwtAuthenticationToken &&
                    jwtTokenService
                        .getIssuer()
                        .equals(((JwtAuthenticationToken) user.getToken()).getToken().getClaim(JwtClaimNames.ISS))
                ) {
                    Jwt jwt = ((JwtAuthenticationToken) user.getToken()).getToken();

                    if (!jwt.getAudience().contains(jwtTokenService.getExchangeAudience())) {
                        throw new IllegalArgumentException("invalid subject token");
                    }

                    //PAT only
                    TokenResponse response = tokenService.generatePersonalAccessToken(user, List.copyOf(scopes));
                    response.setIssuedTokenType(PAT_TYPE);

                    return response;
                }
            }
            throw new IllegalArgumentException("invalid or missing subject_token_type");
        } catch (AuthenticationException ae1) {
            throw new IllegalArgumentException("invalid or missing subject_token");
        }
    }

    @Override
    public AuthorizationGrantType type() {
        return AuthorizationGrantType.TOKEN_EXCHANGE;
    }
}
