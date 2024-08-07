package it.smartcommunitylabdhub.authorization.controllers;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import it.smartcommunitylabdhub.authorization.components.JWKSetKeyStore;
import it.smartcommunitylabdhub.authorization.model.TokenResponse;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.CurrentSecurityContext;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
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

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JWKSetKeyStore jwkSetKeyStore;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private SecurityProperties securityProperties;

    //TODO move to dedicated filter initalized via securityConfig!
    private JwtAuthenticationProvider accessTokenAuthProvider;
    private JwtAuthenticationProvider refreshTokenAuthProvider;
    private JwtAuthenticationProvider externalTokenAuthProvider;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (securityProperties.isRequired()) {
            Assert.notNull(jwkSetKeyStore, "jwks store is required");
            Assert.notNull(jwkSetKeyStore.getJwk(), "jwk is required");

            JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
            JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
            authoritiesConverter.setAuthoritiesClaimName("authorities");
            authoritiesConverter.setAuthorityPrefix("");
            jwtConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

            //build auth provider to validate tokens
            accessTokenAuthProvider = new JwtAuthenticationProvider(coreJwtDecoder(jwkSetKeyStore.getJwk(), false));
            accessTokenAuthProvider.setJwtAuthenticationConverter(jwtConverter);
            refreshTokenAuthProvider = new JwtAuthenticationProvider(coreJwtDecoder(jwkSetKeyStore.getJwk(), true));
            refreshTokenAuthProvider.setJwtAuthenticationConverter(jwtConverter);

            if (securityProperties.isJwtAuthEnabled()) {
                externalTokenAuthProvider = new JwtAuthenticationProvider(externalJwtDecoder());
                externalTokenAuthProvider.setJwtAuthenticationConverter(externalJwtAuthenticationConverter());
            }
        }
    }

    @PostMapping(TOKEN_URL)
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

        if (AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(grantType)) {
            return clientCredentials(parameters, authentication);
        } else if (AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
            return refreshToken(parameters, authentication);
        } else if (TOKEN_EXCHANGE_GRANT_TYPE.equals(grantType)) {
            return tokenExchange(parameters, authentication);
        }

        throw new IllegalArgumentException("invalid or unsupported grant type");
    }

    private TokenResponse refreshToken(Map<String, String> parameters, Authentication authentication) {
        if (refreshTokenAuthProvider == null) {
            throw new UnsupportedOperationException();
        }

        //refresh token is usable without credentials
        //TODO add rotation by storing refresh tokens in db!

        String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
            throw new IllegalArgumentException("invalid grant type");
        }

        String token = parameters.get("refresh_token");
        if (token == null) {
            throw new IllegalArgumentException("invalid or missing refresh_token");
        }

        String cid = parameters.get("client_id");
        if (cid == null || !clientId.equals(cid)) {
            throw new IllegalArgumentException("invalid or missing client_id");
        }

        log.debug("refresh token request for {}", cid);
        if (log.isTraceEnabled()) {
            log.trace("refresh token {}", token);
        }

        //validate via provider
        try {
            BearerTokenAuthenticationToken request = new BearerTokenAuthenticationToken(token);
            Authentication auth = refreshTokenAuthProvider.authenticate(request);
            if (!auth.isAuthenticated()) {
                throw new IllegalArgumentException("invalid or missing refresh_token");
            }

            // Consume refresh token
            jwtTokenService.consume(authentication, token);

            //token is valid, use as context for generation
            return jwtTokenService.generateCredentials(auth);
        } catch (AuthenticationException ae) {
            throw new IllegalArgumentException("invalid or missing refresh_token");
        }
    }

    private TokenResponse clientCredentials(Map<String, String> parameters, Authentication authentication) {
        //client credentials *requires* basic auth
        if (authentication == null || !(authentication instanceof UsernamePasswordAuthenticationToken)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        //for client credentials to mimic admin user client *must* match authenticated user
        UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) authentication;
        if (clientId != null && !clientId.equals(auth.getName())) {
            throw new InsufficientAuthenticationException("Invalid client authentication");
        }

        String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.CLIENT_CREDENTIALS.getValue().equals(grantType)) {
            throw new IllegalArgumentException("invalid grant type");
        }

        log.debug("client token request for {}", auth.getName());

        //generate as per user
        return jwtTokenService.generateCredentials(authentication);
    }

    private TokenResponse tokenExchange(Map<String, String> parameters, Authentication authentication) {
        if (accessTokenAuthProvider == null) {
            throw new UnsupportedOperationException();
        }

        //token exchange *requires* basic auth
        if (authentication == null || !(authentication instanceof UsernamePasswordAuthenticationToken)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        //for client credentials to mimic admin user client *must* match authenticated user
        UsernamePasswordAuthenticationToken clientAuth = (UsernamePasswordAuthenticationToken) authentication;
        if (clientId != null && !clientId.equals(clientAuth.getName())) {
            throw new InsufficientAuthenticationException("Invalid client authentication");
        }

        String grantType = parameters.get(OAuth2ParameterNames.GRANT_TYPE);
        if (!TOKEN_EXCHANGE_GRANT_TYPE.equals(grantType)) {
            throw new IllegalArgumentException("invalid grant type");
        }

        //validate token as well
        String token = parameters.get("subject_token");
        if (token == null) {
            throw new IllegalArgumentException("invalid or missing subject_token");
        }

        String tokenType = parameters.get("subject_token_type");
        if (!ACCESS_TOKEN_TYPE.equals(tokenType)) {
            throw new IllegalArgumentException("invalid or missing subject_token_type");
        }

        log.debug("exchange token request from {}", clientAuth.getName());
        if (log.isTraceEnabled()) {
            log.trace("subject token {}", token);
        }

        //validate via provider
        try {
            BearerTokenAuthenticationToken request = new BearerTokenAuthenticationToken(token);
            Authentication userAuth = accessTokenAuthProvider.authenticate(request);
            if (!userAuth.isAuthenticated()) {
                throw new IllegalArgumentException("invalid or missing subject_token");
            }

            log.debug(
                    "exchange token request from {} resolved for {} via internal provider",
                    clientAuth.getName(),
                    userAuth.getName()
            );

            //token is valid, use as context for generation
            return jwtTokenService.generateCredentials(userAuth);
        } catch (AuthenticationException ae) {
            //fall back to external if available
            if (externalTokenAuthProvider != null) {
                try {
                    BearerTokenAuthenticationToken request = new BearerTokenAuthenticationToken(token);
                    Authentication userAuth = externalTokenAuthProvider.authenticate(request);
                    if (!userAuth.isAuthenticated()) {
                        throw new IllegalArgumentException("invalid or missing subject_token");
                    }

                    log.debug(
                            "exchange token request from {} resolved for {} via external provider",
                            clientAuth.getName(),
                            userAuth.getName()
                    );

                    //token is valid, use as context for generation
                    return jwtTokenService.generateCredentials(userAuth);
                } catch (AuthenticationException ae1) {
                    throw new IllegalArgumentException("invalid or missing subject_token");
                }
            }

            throw new IllegalArgumentException("invalid or missing subject_token");
        }
    }

    //TODO move to filter + config!
    private JwtDecoder coreJwtDecoder(JWK jwk, boolean asRefresh) throws JOSEException {
        //we support only RSA keys
        if (!(jwk instanceof RSAKey)) {
            throw new IllegalArgumentException("the provided key is not suitable for token authentication");
        }

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(jwk.toRSAKey().toRSAPublicKey()).build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(
                applicationProperties.getEndpoint()
        );
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                (aud -> aud != null && aud.contains(applicationProperties.getName()))
        );

        //refresh tokens *must contain* at_hash, access token *not*
        OAuth2TokenValidator<Jwt> tokenValidator = new JwtClaimValidator<String>(
                IdTokenClaimNames.AT_HASH,
                (hash -> (asRefresh ? hash != null : hash == null))
        );

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                withIssuer,
                audienceValidator,
                tokenValidator
        );
        jwtDecoder.setJwtValidator(validator);

        return jwtDecoder;
    }

    /**
     * External auth via JWT
     */
    private JwtDecoder externalJwtDecoder() {
        SecurityProperties.JwtAuthenticationProperties jwtProps = securityProperties.getJwt();
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withIssuerLocation(jwtProps.getIssuerUri()).build();

        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                (aud -> aud != null && aud.contains(jwtProps.getAudience()))
        );

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(jwtProps.getIssuerUri());
        OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
        jwtDecoder.setJwtValidator(withAudience);

        return jwtDecoder;
    }

    private JwtAuthenticationConverter externalJwtAuthenticationConverter() {
        SecurityProperties.JwtAuthenticationProperties jwtProps = securityProperties.getJwt();
        String claim = jwtProps.getClaim();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter((Jwt source) -> {
            if (source == null) return null;

            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

            if (StringUtils.hasText(claim) && source.hasClaim(claim)) {
                List<String> roles = source.getClaimAsStringList(claim);
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

            return authorities;
        });
        return converter;
    }
}
