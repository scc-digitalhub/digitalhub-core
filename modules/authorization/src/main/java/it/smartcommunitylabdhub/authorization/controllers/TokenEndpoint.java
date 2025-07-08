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

import it.smartcommunitylabdhub.authorization.exceptions.JwtTokenServiceException;
import it.smartcommunitylabdhub.authorization.grants.TokenGranter;
import it.smartcommunitylabdhub.authorization.model.TokenResponse;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Slf4j
public class TokenEndpoint implements InitializingBean {

    public static final String TOKEN_URL = "/auth/token";
    public static final String TOKEN_EXCHANGE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:token-exchange";
    public static final String ACCESS_TOKEN_TYPE = "urn:ietf:params:oauth:token-type:access_token";

    @Value("${jwt.client-id}")
    private String clientId;

    @Value("${jwt.client-secret}")
    private String clientSecret;

    // @Autowired
    // private JwtTokenService jwtTokenService;

    // @Autowired
    // private JWKSetKeyStore jwkSetKeyStore;

    // @Autowired
    // private ApplicationProperties applicationProperties;

    @Autowired
    private SecurityProperties securityProperties;

    // @Autowired
    // AuthorizableAwareEntityService<Project> projectAuthHelper;

    //TODO move to dedicated filter initalized via securityConfig!
    // private JwtAuthenticationProvider accessTokenAuthProvider;
    // private JwtAuthenticationProvider refreshTokenAuthProvider;
    // private JwtAuthenticationProvider externalTokenAuthProvider;

    @Autowired
    private List<TokenGranter> granters;

    @Override
    public void afterPropertiesSet() throws Exception {
        // if (securityProperties.isRequired()) {
        // Assert.notNull(jwkSetKeyStore, "jwks store is required");
        // Assert.notNull(jwkSetKeyStore.getJwk(), "jwk is required");
        // //build auth provider to validate tokens
        // JwtAuthenticationConverter jwtConverter = coreJwtAuthenticationConverter("authorities", projectAuthHelper);
        // accessTokenAuthProvider = new JwtAuthenticationProvider(coreJwtDecoder(jwkSetKeyStore.getJwk(), false));
        // accessTokenAuthProvider.setJwtAuthenticationConverter(jwtConverter);
        // // refreshTokenAuthProvider = new JwtAuthenticationProvider(coreJwtDecoder(jwkSetKeyStore.getJwk(), true));
        // // refreshTokenAuthProvider.setJwtAuthenticationConverter(jwtConverter);

        // if (securityProperties.isJwtAuthEnabled()) {
        //     JwtAuthenticationProperties jwtProps = securityProperties.getJwt();
        //     externalTokenAuthProvider =
        //         new JwtAuthenticationProvider(externalJwtDecoder(jwtProps.getIssuerUri(), jwtProps.getAudience()));

        //     externalTokenAuthProvider.setJwtAuthenticationConverter(
        //         externalJwtAuthenticationConverter(jwtProps.getUsername(), jwtProps.getClaim(), projectAuthHelper)
        //     );
        // }
        // }
    }

    //TODO add errorHandling
    @RequestMapping(value = TOKEN_URL, method = { RequestMethod.POST, RequestMethod.GET })
    public TokenResponse token(
        @RequestParam Map<String, String> parameters,
        @CurrentSecurityContext SecurityContext securityContext
    ) {
        if (!securityProperties.isRequired()) {
            throw new UnsupportedOperationException();
        }

        Authentication authentication = securityContext.getAuthentication();

        //resolve client authentication
        if (authentication == null || !(authentication.isAuthenticated())) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        //select flow
        String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        log.debug("token request for {}", grantType);
        if (log.isTraceEnabled()) {
            log.trace("authentication name {}", authentication.getName());
        }

        //pick first matching granter
        TokenGranter granter = granters
            .stream()
            .filter(g -> g.type().getValue().equals(grantType))
            .findFirst()
            .orElse(null);

        if (granter != null) {
            return granter.grant(parameters, authentication);
        }

        // if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(grantType)) {
        //     return clientCredentials(parameters, authentication);
        // } else if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
        //     return refreshToken(parameters, authentication);
        // } else if (TOKEN_EXCHANGE_GRANT_TYPE.equals(grantType)) {
        //     return tokenExchange(parameters, authentication);
        // }

        throw new IllegalArgumentException("invalid or unsupported grant type");
    }

    @ExceptionHandler(JwtTokenServiceException.class)
    public ResponseEntity<String> handleServiceException(JwtTokenServiceException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }
    // private TokenResponse refreshToken(Map<String, String> parameters, Authentication authentication) {
    //     if (refreshTokenAuthProvider == null) {
    //         throw new UnsupportedOperationException();
    //     }

    //     //refresh token is usable without credentials
    //     String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
    //     if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
    //         throw new IllegalArgumentException("invalid grant type");
    //     }

    //     String token = parameters.get("refresh_token");
    //     if (token == null) {
    //         throw new IllegalArgumentException("invalid or missing refresh_token");
    //     }

    //     String cid = parameters.get("client_id");
    //     if (cid == null || !clientId.equals(cid)) {
    //         throw new IllegalArgumentException("invalid or missing client_id");
    //     }

    //     log.debug("refresh token request for {}", cid);
    //     if (log.isTraceEnabled()) {
    //         log.trace("refresh token {}", token);
    //     }

    //     //validate via provider
    //     try {
    //         BearerTokenAuthenticationToken request = new BearerTokenAuthenticationToken(token);
    //         Authentication auth = refreshTokenAuthProvider.authenticate(request);
    //         if (!auth.isAuthenticated()) {
    //             throw new IllegalArgumentException("invalid or missing refresh_token");
    //         }

    //         // Consume refresh token
    //         jwtTokenService.consume(auth, token);

    //         //token is valid, use as context for generation
    //         return jwtTokenService.generateCredentials(auth);
    //     } catch (AuthenticationException ae) {
    //         throw new IllegalArgumentException("invalid or missing refresh_token");
    //     }
    // }

    // private TokenResponse clientCredentials(Map<String, String> parameters, Authentication authentication) {
    //     //client credentials *requires* basic auth
    //     if (authentication == null || !(authentication instanceof UsernamePasswordAuthenticationToken)) {
    //         throw new InsufficientAuthenticationException("Invalid or missing authentication");
    //     }

    //     //for client credentials to mimic admin user client *must* match authenticated user
    //     UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) authentication;
    //     if (clientId != null && !clientId.equals(auth.getName())) {
    //         throw new InsufficientAuthenticationException("Invalid client authentication");
    //     }

    //     String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
    //     if (!AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(grantType)) {
    //         throw new IllegalArgumentException("invalid grant type");
    //     }

    //     log.debug("client token request for {}", auth.getName());

    //     //generate as per user
    //     return jwtTokenService.generateCredentials(authentication);
    // }

    // private TokenResponse tokenExchange(Map<String, String> parameters, Authentication authentication) {
    //     if (accessTokenAuthProvider == null) {
    //         throw new UnsupportedOperationException();
    //     }

    //     //token exchange *requires* basic auth
    //     if (authentication == null || !(authentication instanceof UsernamePasswordAuthenticationToken)) {
    //         throw new InsufficientAuthenticationException("Invalid or missing authentication");
    //     }

    //     //for client credentials to mimic admin user client *must* match authenticated user
    //     UsernamePasswordAuthenticationToken clientAuth = (UsernamePasswordAuthenticationToken) authentication;
    //     if (clientId != null && !clientId.equals(clientAuth.getName())) {
    //         throw new InsufficientAuthenticationException("Invalid client authentication");
    //     }

    //     String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
    //     if (!TOKEN_EXCHANGE_GRANT_TYPE.equals(grantType)) {
    //         throw new IllegalArgumentException("invalid grant type");
    //     }

    //     String cid = parameters.get("client_id");
    //     if (cid != null && !clientId.equals(cid)) {
    //         throw new IllegalArgumentException("invalid or missing client_id");
    //     }

    //     //validate token as well
    //     String token = parameters.get("subject_token");
    //     if (token == null) {
    //         throw new IllegalArgumentException("invalid or missing subject_token");
    //     }

    //     String tokenType = parameters.get("subject_token_type");
    //     if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
    //         throw new IllegalArgumentException("invalid or missing subject_token_type");
    //     }

    //     log.debug("exchange token request from {}", clientAuth.getName());
    //     if (log.isTraceEnabled()) {
    //         log.trace("subject token {}", token);
    //     }

    //     //validate external provider
    //     if (externalTokenAuthProvider != null) {
    //         try {
    //             BearerTokenAuthenticationToken request = new BearerTokenAuthenticationToken(token);
    //             Authentication userAuth = externalTokenAuthProvider.authenticate(request);
    //             if (!userAuth.isAuthenticated()) {
    //                 throw new IllegalArgumentException("invalid or missing subject_token");
    //             }

    //             log.debug(
    //                 "exchange token request from {} resolved for {} via external provider",
    //                 clientAuth.getName(),
    //                 userAuth.getName()
    //             );

    //             //token is valid, use as context for generation
    //             return jwtTokenService.generateCredentials(userAuth);
    //         } catch (AuthenticationException ae1) {
    //             throw new IllegalArgumentException("invalid or missing subject_token");
    //         }
    //     }

    //     throw new IllegalArgumentException("invalid or missing subject_token");
    // }

    // //TODO move to filter + config!
    // private JwtDecoder coreJwtDecoder(JWK jwk, boolean asRefresh) throws JOSEException {
    //     //we support only RSA keys
    //     if (!(jwk instanceof RSAKey)) {
    //         throw new IllegalArgumentException("the provided key is not suitable for token authentication");
    //     }

    //     NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(jwk.toRSAKey().toRSAPublicKey()).build();

    //     OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(
    //         applicationProperties.getEndpoint()
    //     );
    //     OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
    //         JwtClaimNames.AUD,
    //         (aud -> aud != null && aud.contains(applicationProperties.getName()))
    //     );

    //     //refresh tokens *must contain* at_hash, access token *not*
    //     OAuth2TokenValidator<Jwt> tokenValidator = new JwtClaimValidator<String>(
    //         IdTokenClaimNames.AT_HASH,
    //         (hash -> (asRefresh ? hash != null : hash == null))
    //     );

    //     OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
    //         withIssuer,
    //         audienceValidator,
    //         tokenValidator
    //     );
    //     jwtDecoder.setJwtValidator(validator);

    //     return jwtDecoder;
    // }

    // /**
    //  * External auth via JWT
    //  * TODO! use SecurityConfig instead (move tokenEndpoint to app!)
    //  * copied from SecurityConfig
    //  */
    // private static JwtDecoder externalJwtDecoder(String issuer, String audience) {
    //     NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuer).build();

    //     OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
    //         JwtClaimNames.AUD,
    //         (aud -> aud != null && aud.contains(audience))
    //     );

    //     OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
    //     OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
    //     jwtDecoder.setJwtValidator(withAudience);

    //     return jwtDecoder;
    // }

    // private static Converter<Jwt, AbstractAuthenticationToken> externalJwtAuthenticationConverter(
    //     String usernameClaimName,
    //     String rolesClaimName,
    //     AuthorizableAwareEntityService<Project> projectAuthHelper
    // ) {
    //     return (Jwt jwt) -> {
    //         Set<GrantedAuthority> authorities = new HashSet<>();
    //         authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

    //         //read roles from token
    //         if (StringUtils.hasText(rolesClaimName) && jwt.hasClaim(rolesClaimName)) {
    //             List<String> roles = jwt.getClaimAsStringList(rolesClaimName);
    //             if (roles != null) {
    //                 roles.forEach(r -> {
    //                     if ("ROLE_ADMIN".equals(r) || r.contains(":")) {
    //                         //use as is
    //                         authorities.add(new SimpleGrantedAuthority(r));
    //                     } else {
    //                         //derive a scoped USER role
    //                         authorities.add(new SimpleGrantedAuthority(r + ":ROLE_USER"));
    //                     }
    //                 });
    //             }
    //         }

    //         //principalName
    //         String username = jwt.getClaimAsString(usernameClaimName);

    //         //fallback to SUB if missing
    //         if (!StringUtils.hasText(username)) {
    //             username = jwt.getSubject();
    //         }

    //         if (projectAuthHelper != null) {
    //             //inject roles from ownership of projects
    //             //derive a scoped ADMIN role
    //             projectAuthHelper
    //                 .findIdsByCreatedBy(username)
    //                 .forEach(p -> authorities.add(new SimpleGrantedAuthority(p + ":ROLE_ADMIN")));

    //             //inject roles from sharing of projects
    //             //derive a scoped USER role
    //             //TODO make configurable?
    //             projectAuthHelper
    //                 .findIdsBySharedTo(username)
    //                 .forEach(p -> authorities.add(new SimpleGrantedAuthority(p + ":ROLE_USER")));
    //         }

    //         return new JwtAuthenticationToken(jwt, authorities, username);
    //     };
    // }

    // private static JwtAuthenticationConverter coreJwtAuthenticationConverter(
    //     String claim,
    //     AuthorizableAwareEntityService<Project> projectAuthHelper
    // ) {
    //     JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    //     converter.setJwtGrantedAuthoritiesConverter((Jwt source) -> {
    //         if (source == null) return null;

    //         Set<GrantedAuthority> authorities = new HashSet<>();
    //         authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

    //         if (StringUtils.hasText(claim) && source.hasClaim(claim)) {
    //             List<String> roles = source.getClaimAsStringList(claim);
    //             if (roles != null) {
    //                 roles.forEach(r -> {
    //                     //use as is
    //                     authorities.add(new SimpleGrantedAuthority(r));
    //                 });
    //             }
    //         }

    //         //refresh project authorities via helper
    //         if (projectAuthHelper != null && StringUtils.hasText(source.getSubject())) {
    //             String username = source.getSubject();

    //             //inject roles from ownership of projects
    //             projectAuthHelper
    //                 .findIdsByCreatedBy(username)
    //                 .forEach(p -> {
    //                     //derive a scoped ADMIN role
    //                     authorities.add(new SimpleGrantedAuthority(p + ":ROLE_ADMIN"));
    //                 });

    //             //inject roles from sharing of projects
    //             projectAuthHelper
    //                 .findIdsBySharedTo(username)
    //                 .forEach(p -> {
    //                     //derive a scoped USER role
    //                     //TODO make configurable?
    //                     authorities.add(new SimpleGrantedAuthority(p + ":ROLE_USER"));
    //                 });
    //         }

    //         return authorities;
    //     });
    //     return converter;
    // }
}
