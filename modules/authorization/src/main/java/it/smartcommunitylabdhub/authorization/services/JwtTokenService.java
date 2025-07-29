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

package it.smartcommunitylabdhub.authorization.services;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.ECDSASigner;
import com.nimbusds.jose.crypto.ECDSAVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.smartcommunitylabdhub.authorization.components.JWKSetKeyStore;
import it.smartcommunitylabdhub.authorization.exceptions.JwtTokenServiceException;
import it.smartcommunitylabdhub.authorization.model.PersonalAccessToken;
import it.smartcommunitylabdhub.authorization.model.RefreshToken;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.repositories.PersonalAccessTokenRepository;
import it.smartcommunitylabdhub.authorization.repositories.RefreshTokenRepository;
import it.smartcommunitylabdhub.authorization.utils.SecureKeyGenerator;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.sql.SQLTimeoutException;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.serializer.support.SerializationDelegate;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2TokenIntrospectionClaimNames;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.oidc.StandardClaimNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.authentication.WebAuthenticationDetails;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Service
@Slf4j
//TODO extract an interface
public class JwtTokenService implements InitializingBean {

    public static final int DEFAULT_ACCESS_TOKEN_DURATION = 3600 * 8; //8 hours
    public static final int DEFAULT_REFRESH_TOKEN_DURATION = 3600 * 24 * 30; //30 days
    public static final int DEFAULT_PERSONAL_TOKEN_DURATION = 3600 * 24 * 365; //1 year

    public static final int DEFAULT_KEY_LENGTH = 54;

    public static final String CLAIM_AUTHORITIES = "authorities";

    @Autowired
    //TODO move to JDBC!
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PersonalAccessTokenRepository personalAccessTokenRepository;

    @Autowired
    private JWKSetKeyStore keyStore;

    @Value("${jwt.client-id}")
    private String clientId;

    private String audience;
    private String issuer;

    private int accessTokenDuration = DEFAULT_ACCESS_TOKEN_DURATION;
    private int refreshTokenDuration = DEFAULT_REFRESH_TOKEN_DURATION;
    private int personalTokenDuration = DEFAULT_PERSONAL_TOKEN_DURATION;

    //we need to keep the key along with singer/verifier
    private JWK jwk;
    private JWSSigner signer;
    private JWSVerifier verifier;

    private JwtDecoder decoder;

    private JwtAuthenticationConverter authenticationConverter;

    //keygen
    private StringKeyGenerator keyGenerator;

    //custom serialization
    SerializationDelegate serializer = new SerializationDelegate(this.getClass().getClassLoader());

    public JwtTokenService() {
        log.debug("create jwks service");
        this.keyGenerator = new SecureKeyGenerator(DEFAULT_KEY_LENGTH);
    }

    @Autowired
    public void setAccessTokenDuration(@Value("${jwt.access-token.duration}") Integer accessTokenDuration) {
        if (accessTokenDuration != null) {
            this.accessTokenDuration = accessTokenDuration.intValue();
        }
    }

    @Autowired
    public void setRefreshTokenDuration(@Value("${jwt.refresh-token.duration}") Integer refreshTokenDuration) {
        if (refreshTokenDuration != null) {
            this.refreshTokenDuration = refreshTokenDuration.intValue();
        }
    }

    public void setPersonalTokenDuration(@Value("${jwt.personal-token.duration}") Integer personalTokenDuration) {
        if (personalTokenDuration != null) {
            this.personalTokenDuration = personalTokenDuration;
        }
    }

    @Autowired
    public void setApplicationProperties(ApplicationProperties applicationProperties) {
        Assert.notNull(applicationProperties, "app properties are required");
        this.issuer = applicationProperties.getEndpoint();
        this.audience = applicationProperties.getName();
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.hasText(audience, "audience can not be null");
        Assert.hasText(issuer, "issuer can not be null");

        if (keyStore != null) {
            //build signer for the given keys
            this.jwk = keyStore.getJwk();

            if (jwk != null) {
                try {
                    this.verifier = buildVerifier(jwk);
                    this.signer = buildSigner(jwk);
                    this.decoder = buildJwtDecoder(jwk);
                    this.authenticationConverter = buildAuthoritiesConverter();
                } catch (JOSEException e) {
                    log.warn("Exception loading signer/verifier", e);
                }
            }
        }
    }

    /*
     * Export config
     */

    public int getAccessTokenDuration() {
        return accessTokenDuration;
    }

    public int getRefreshTokenDuration() {
        return refreshTokenDuration;
    }

    public int getPersonalTokenDuration() {
        return personalTokenDuration;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getAudience() {
        return audience;
    }

    public String getExchangeAudience() {
        return audience + "/exchange";
    }

    public String getClientId() {
        return clientId;
    }

    public JWSVerifier getVerifier() {
        return verifier;
    }

    public JwtDecoder getDecoder() {
        return decoder;
    }

    public JwtAuthenticationConverter getAuthenticationConverter() {
        return authenticationConverter;
    }

    public OpaqueTokenIntrospector getPersonalAccessTokenIntrospector() {
        return new OpaqueTokenIntrospector() {
            @Override
            public OAuth2AuthenticatedPrincipal introspect(String token) {
                try {
                    // Find the token
                    PersonalAccessToken pat = personalAccessTokenRepository.consume(token);
                    if (pat == null) {
                        return null;
                    }

                    if (log.isTraceEnabled()) {
                        log.trace("token: {}", pat);
                    }

                    // Check expiration
                    if (pat.getExpiresAt().before(Date.from(Instant.now()))) {
                        throw new JwtTokenServiceException("Personal access token is expired");
                    }

                    //deserialize authentication
                    byte[] bytes = pat.getAuth();
                    if (bytes == null || bytes.length == 0) {
                        throw new JwtTokenServiceException("Missing authentication for token");
                    }

                    UserAuthentication<?> user = (UserAuthentication<?>) serializer.deserializeFromByteArray(bytes);

                    log.debug("Personal access token successfully restored");

                    //map attributes
                    Instant now = Instant.now();

                    Map<String, Object> attributes = new HashMap<>();
                    attributes.put("sub", user.getName());
                    attributes.put("preferred_username", user.getUsername());
                    attributes.put("client_id", clientId);
                    attributes.put("aud", audience);
                    attributes.put("iss", issuer);
                    attributes.put("iat", now);
                    attributes.put("exp", now.plusSeconds(accessTokenDuration));
                    attributes.put("scope", StringUtils.collectionToCommaDelimitedString(pat.getScopes()));

                    //inject extended attributes
                    attributes.put("credentials", user.getCredentials());
                    attributes.put("subject_token", user.getToken());

                    //TODO evaluate restoring original token
                    DefaultOAuth2AuthenticatedPrincipal principal = new DefaultOAuth2AuthenticatedPrincipal(
                        user.getName(),
                        attributes,
                        user.getAuthorities()
                    );

                    return principal;
                } catch (IOException | StoreException | JwtTokenServiceException e) {
                    log.debug("error introspecting personal access token", e);
                    throw new JwtTokenServiceException(e.getMessage());
                }
            }
        };
    }

    public OpaqueTokenAuthenticationConverter getPersonalAccessTokenConverter() {
        return new OpaqueTokenAuthenticationConverter() {
            @Override
            public BearerTokenAuthentication convert(
                String introspectedToken,
                OAuth2AuthenticatedPrincipal authenticatedPrincipal
            ) {
                if (authenticatedPrincipal == null) {
                    return null;
                }

                Instant iat = authenticatedPrincipal.getAttribute(OAuth2TokenIntrospectionClaimNames.IAT);
                Instant exp = authenticatedPrincipal.getAttribute(OAuth2TokenIntrospectionClaimNames.EXP);
                OAuth2AccessToken accessToken = new OAuth2AccessToken(
                    OAuth2AccessToken.TokenType.BEARER,
                    introspectedToken,
                    iat,
                    exp
                );
                return new BearerTokenAuthentication(
                    authenticatedPrincipal,
                    accessToken,
                    authenticatedPrincipal.getAuthorities()
                );
            }
        };
    }

    /*
     * Access tokens
     */
    public String generateAccessTokenAsString(@NotNull UserAuthentication<?> authentication)
        throws JwtTokenServiceException {
        // Serialize to compact form
        SignedJWT jwt = generateAccessToken(authentication);
        String jwtToken = jwt.serialize();

        if (log.isTraceEnabled()) {
            log.trace("Generated JWT token: {}", jwtToken);
        }

        return jwtToken;
    }

    public SignedJWT generateAccessToken(@NotNull UserAuthentication<?> authentication)
        throws JwtTokenServiceException {
        return generateAccessToken(authentication, List.of(audience));
    }

    public SignedJWT generateAccessToken(@NotNull UserAuthentication<?> authentication, List<String> audiences)
        throws JwtTokenServiceException {
        return generateAccessToken(authentication, List.of(audience), accessTokenDuration);
    }

    public SignedJWT generateAccessToken(
        @NotNull UserAuthentication<?> authentication,
        List<String> audiences,
        int duration
    ) throws JwtTokenServiceException {
        if (signer == null) {
            throw new UnsupportedOperationException("signer not available");
        }

        try {
            JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jwk.getAlgorithm().getName());

            Instant now = Instant.now();

            // build access token claims
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(authentication.getName())
                .issuer(issuer)
                .issueTime(Date.from(now))
                .audience(audiences)
                .jwtID(keyGenerator.generateKey())
                .expirationTime(Date.from(now.plusSeconds(duration)));
            claims.claim(StandardClaimNames.PREFERRED_USERNAME, authentication.getUsername());

            //define authorities as claims
            List<String> authorities = authentication
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

            claims.claim(CLAIM_AUTHORITIES, authorities);

            //add client if set
            if (StringUtils.hasText(clientId)) {
                claims.claim("client_id", clientId);
            }

            //include any credential available
            //NOTE: we expect claims to NOT clash
            if (authentication.getCredentials() != null) {
                authentication
                    .getCredentials()
                    .stream()
                    .filter(c -> c != null)
                    .map(c -> {
                        if (c instanceof CredentialsContainer) {
                            ((CredentialsContainer) c).eraseCredentials();
                        }
                        return c;
                    })
                    .flatMap(c -> c.toMap().entrySet().stream())
                    .forEach(c -> claims.claim(c.getKey(), c.getValue()));
            }

            // build and sign
            JWTClaimsSet claimsSet = claims.build();
            JWSHeader header = new JWSHeader.Builder(jwsAlgorithm).keyID(jwk.getKeyID()).build();
            SignedJWT jwt = new SignedJWT(header, claimsSet);
            jwt.sign(signer);

            return jwt;
        } catch (JOSEException e) {
            log.error("Error generating JWT token", e);
            return null;
        }
    }

    /*
     * Refresh tokens
     */
    public String generateRefreshToken(@NotNull UserAuthentication<?> authentication, @NotNull SignedJWT accessToken)
        throws JwtTokenServiceException {
        log.debug("generate refresh token for {}", authentication.getName());
        if (log.isTraceEnabled()) {
            log.trace("access token: {}", accessToken.serialize());
        }

        String id = UUID.randomUUID().toString();

        //refresh tokens are opaque
        String jti = keyGenerator.generateKey();
        Instant now = Instant.now();

        //fetch ip address if available
        String ipAddress = null;
        Object details = authentication.getToken().getDetails();
        if (details instanceof WebAuthenticationDetails) {
            ipAddress = ((WebAuthenticationDetails) details).getRemoteAddress();
        }

        //store auth object serialized
        // byte[] auth = SerializationUtils.serialize(authentication);
        try {
            byte[] auth = serializer.serializeToByteArray(authentication);

            log.debug("store refresh token for {} with id {}", authentication.getName(), jti);

            // store Refresh Token into db
            RefreshToken refreshToken = RefreshToken
                .builder()
                .id(id)
                .token(jti)
                .user(authentication.getName())
                .auth(auth)
                .issuedAt(Date.from(now))
                .expiresAt(Date.from(now.plusSeconds(refreshTokenDuration)))
                .ipAddress(ipAddress)
                .build();

            //save
            refreshTokenRepository.store(id, refreshToken);

            return refreshToken.getToken();
        } catch (IOException | StoreException e) {
            throw new JwtTokenServiceException(e.getMessage());
        }
    }

    @Transactional(dontRollbackOn = { PessimisticLockingFailureException.class, SQLTimeoutException.class })
    public @NotNull UserAuthentication<?> consume(String refreshToken) {
        // try {
        if (verifier == null) {
            throw new UnsupportedOperationException("verifier not available");
        }

        log.debug("consume refresh token: {}", refreshToken);

        try {
            // consume the token
            RefreshToken token = refreshTokenRepository.consume(refreshToken);
            if (token == null) {
                log.debug("refresh token does not exists: {} ", refreshToken);
                throw new JwtTokenServiceException("Refresh token does not exist");
            }

            if (log.isTraceEnabled()) {
                log.trace("token: {}", token);
            }

            // Check expiration
            if (token.getExpiresAt().before(Date.from(Instant.now()))) {
                throw new JwtTokenServiceException("Refresh token has expired");
            }

            //deserialize authentication
            byte[] bytes = token.getAuth();
            if (bytes == null || bytes.length == 0) {
                throw new JwtTokenServiceException("Missing authentication for token");
            }

            // @SuppressWarnings("deprecation")
            // UserAuthentication<?> user = (UserAuthentication<?>) SerializationUtils.deserialize(bytes);

            UserAuthentication<?> user = (UserAuthentication<?>) serializer.deserializeFromByteArray(bytes);

            log.debug("Refresh token successfully consumed and removed from repository");
            return user;
            // } catch (ParseException e) {
            //     throw new JwtTokenServiceException("error parsing token", e);
            // } catch (JOSEException e) {
            //     throw new JwtTokenServiceException("Error verifying JWT token", e);
            // }

        } catch (IOException | StoreException e) {
            throw new JwtTokenServiceException(e.getMessage());
        }
    }

    public List<RefreshToken> findRefreshTokens(@NotNull String user) {
        log.debug("find all refresh tokens for {}", user);
        try {
            return refreshTokenRepository
                .findByUser(user)
                .stream()
                .map(t -> {
                    //erase token
                    t.setToken(null);

                    //erase auth to avoid leaking authentication data
                    t.setAuth(null);
                    return t;
                })
                .toList();
        } catch (StoreException e) {
            throw new JwtTokenServiceException(e.getMessage());
        }
    }

    public void deleteRefreshToken(@NotNull String user, @NotNull String id) {
        log.debug("delete refresh token {} for {}", id, user);
        try {
            RefreshToken token = refreshTokenRepository.find(id);
            if (token == null) {
                //nothing to do
                return;
            }

            //check user matches
            if (!user.equals(token.getUser())) {
                throw new JwtTokenServiceException("Invalid user for refresh token");
            }

            //remove
            refreshTokenRepository.remove(id);
        } catch (StoreException e) {
            throw new JwtTokenServiceException(e.getMessage());
        }
    }

    public RefreshToken findRefreshToken(@NotNull String user, @NotNull String id) {
        log.debug("find refresh token {} for {}", id, user);
        try {
            RefreshToken token = refreshTokenRepository.find(id);
            if (token == null) {
                //nothing to do
                return null;
            }

            //check user matches
            if (!user.equals(token.getUser())) {
                throw new JwtTokenServiceException("Invalid user for refresh token");
            }

            //erase token
            token.setToken(null);

            //erase auth to avoid leaking authentication data
            token.setAuth(null);

            return token;
        } catch (StoreException e) {
            throw new JwtTokenServiceException(e.getMessage());
        }
    }

    // public List<RefreshTokenEntity> findRefreshTokens(@NotNull String subject) {
    //     log.debug("find refresh tokens for {}", subject);
    //     return refreshTokenRepository.findBy(null, null)
    // }

    /*
     * Personal access tokens
     */
    public String generatePersonalAccessToken(
        @NotNull UserAuthentication<?> authentication,
        @Nullable String name,
        @Nullable Set<String> scopes
    ) throws JwtTokenServiceException {
        log.debug("generate personal access token for {}", authentication.getName());

        String id = UUID.randomUUID().toString();
        if (!StringUtils.hasText(name)) {
            name = keyGenerator.generateKey();
        }

        //PAT tokens are opaque
        String jti = keyGenerator.generateKey();
        Instant now = Instant.now();
        Date expires = Date.from(now.plusSeconds(personalTokenDuration));

        //fetch ip address if available
        String ipAddress = null;
        Object details = authentication.getToken().getDetails();
        if (details instanceof WebAuthenticationDetails) {
            ipAddress = ((WebAuthenticationDetails) details).getRemoteAddress();
        }

        try {
            //serialize auth to keep authentication context
            byte[] auth = serializer.serializeToByteArray(authentication);

            log.debug("store personal access token for {} with id {}", authentication.getName(), jti);
            PersonalAccessToken personalAccessToken = PersonalAccessToken
                .builder()
                .id(id)
                .name(name)
                .token(jti)
                .user(authentication.getName())
                .issuedAt(Date.from(now))
                .expiresAt(expires)
                .scopes(scopes)
                .ipAddress(ipAddress)
                .auth(auth)
                .build();

            //save
            personalAccessTokenRepository.store(id, personalAccessToken);

            return personalAccessToken.getToken();
        } catch (IOException | StoreException e) {
            throw new JwtTokenServiceException(e.getMessage());
        }
    }

    public List<PersonalAccessToken> findPersonalAccessTokens(@NotNull String user) {
        log.debug("find all personal access tokens for {}", user);
        try {
            return personalAccessTokenRepository
                .findByUser(user)
                .stream()
                .map(t -> {
                    //erase token
                    t.setToken(null);

                    //erase auth to avoid leaking authentication data
                    t.setAuth(null);
                    return t;
                })
                .toList();
        } catch (StoreException e) {
            throw new JwtTokenServiceException(e.getMessage());
        }
    }

    public PersonalAccessToken findPersonalAccessToken(@NotNull String user, @NotNull String personalAccessToken) {
        log.debug("find personal access token {} for {}", personalAccessToken, user);
        try {
            PersonalAccessToken token = personalAccessTokenRepository.find(personalAccessToken);
            if (token == null) {
                //nothing to do
                return null;
            }

            //check user matches
            if (!user.equals(token.getUser())) {
                throw new JwtTokenServiceException("Invalid user for personal access token");
            }

            //erase token
            token.setToken(null);

            //erase auth to avoid leaking authentication data
            token.setAuth(null);

            return token;
        } catch (StoreException e) {
            throw new JwtTokenServiceException(e.getMessage());
        }
    }

    public void deletePersonalAccessToken(@NotNull String user, @NotNull String personalAccessToken) {
        log.debug("delete personal access token {} for {}", personalAccessToken, user);
        try {
            PersonalAccessToken token = personalAccessTokenRepository.find(personalAccessToken);
            if (token == null) {
                //nothing to do
                return;
            }

            //check user matches
            if (!user.equals(token.getUser())) {
                throw new JwtTokenServiceException("Invalid user for personal access token");
            }

            //remove
            personalAccessTokenRepository.remove(personalAccessToken);
        } catch (StoreException e) {
            throw new JwtTokenServiceException(e.getMessage());
        }
    }

    private JWSSigner buildSigner(@NotNull JWK jwk) throws JOSEException {
        if (jwk.getAlgorithm() == null) {
            log.warn("Unsupported key: " + jwk);
            throw new JOSEException("key algorithm invalid");
        }

        if (jwk instanceof RSAKey && jwk.isPrivate()) {
            // only add the signer if there's a private key
            return new RSASSASigner((RSAKey) jwk);
        } else if (jwk instanceof ECKey && jwk.isPrivate()) {
            // build EC signers & verifiers
            return new ECDSASigner((ECKey) jwk);
        } else if (jwk instanceof OctetSequenceKey) {
            // build HMAC signers & verifiers
            if (jwk.isPrivate()) { // technically redundant check because all HMAC keys are private
                return new MACSigner((OctetSequenceKey) jwk);
            }
        }

        log.warn("Unknown key type: " + jwk);
        return null;
    }

    private JWSVerifier buildVerifier(@NotNull JWK jwk) throws JOSEException {
        if (jwk.getAlgorithm() == null) {
            log.warn("Unsupported key: " + jwk);
            throw new JOSEException("key algorithm invalid");
        }

        if (jwk instanceof RSAKey) {
            return new RSASSAVerifier((RSAKey) jwk);
        } else if (jwk instanceof ECKey) {
            return new ECDSAVerifier((ECKey) jwk);
        } else if (jwk instanceof OctetSequenceKey) {
            return new MACVerifier((OctetSequenceKey) jwk);
        }

        log.warn("Unknown key type: " + jwk);
        return null;
    }

    private JwtDecoder buildJwtDecoder(@NotNull JWK jwk) throws JOSEException {
        //we support only RSA keys
        if (!(jwk instanceof RSAKey)) {
            log.warn("Unsupported key type: " + jwk);
            throw new IllegalArgumentException("the provided key is not suitable for token authentication");
        }

        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withPublicKey(jwk.toRSAKey().toRSAPublicKey()).build();
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD,
            (aud -> aud != null && aud.contains(audience))
        );

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator);
        jwtDecoder.setJwtValidator(validator);

        return jwtDecoder;
    }

    private JwtAuthenticationConverter buildAuthoritiesConverter() {
        JwtAuthenticationConverter authConverter = new JwtAuthenticationConverter();
        authConverter.setPrincipalClaimName(JwtClaimNames.SUB);
        authConverter.setJwtGrantedAuthoritiesConverter((Jwt source) -> {
            if (source == null) return null;

            Set<GrantedAuthority> authorities = new HashSet<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));

            List<String> roles = source.getClaimAsStringList(CLAIM_AUTHORITIES);
            if (roles != null) {
                roles.forEach(r -> {
                    //use as is
                    authorities.add(new SimpleGrantedAuthority(r));
                });
            }

            return authorities;
        });

        return authConverter;
    }
}
