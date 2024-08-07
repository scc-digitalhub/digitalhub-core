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
import it.smartcommunitylabdhub.authorization.model.TokenResponse;
import it.smartcommunitylabdhub.authorization.utils.JWKUtils;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class JwtTokenService implements InitializingBean {

    private static final int DEFAULT_ACCESS_TOKEN_DURATION = 3600 * 8; //8 hours
    private static final int DEFAULT_REFRESH_TOKEN_DURATION = 3600 * 24 * 30; //30 days

    @Autowired
    private JWKSetKeyStore keyStore;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Autowired
    private SecurityProperties securityProperties;

    @Value("${jwt.client-id}")
    private String clientId;

    private int accessTokenDuration = DEFAULT_ACCESS_TOKEN_DURATION;
    private int refreshTokenDuration = DEFAULT_REFRESH_TOKEN_DURATION;

    //we need to keep the key along with singer/verifier
    private JWK jwk;
    private JWSSigner signer;
    private JWSVerifier verifier;

    @Autowired
    public void setAccessTokenDuration(@Value("${jwt.access-token.duration}") Integer accessTokenDuration) {
        if (accessTokenDuration != null) {
            this.accessTokenDuration = accessTokenDuration.intValue();
        }
    }

    @Autowired
    public void setRefreshTokenDuration(@Value("${jwt.access-token.duration}") Integer refreshTokenDuration) {
        if (refreshTokenDuration != null) {
            this.refreshTokenDuration = refreshTokenDuration.intValue();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (securityProperties.isRequired()) {
            //build signer for the given keys
            this.jwk = keyStore.getJwk();

            if (jwk != null) {
                try {
                    if (jwk.getAlgorithm() == null) {
                        throw new JOSEException("key algorithm invalid");
                    }
                    if (jwk instanceof RSAKey) {
                        // build RSA signers & verifiers
                        if (jwk.isPrivate()) { // only add the signer if there's a private key
                            signer = new RSASSASigner((RSAKey) jwk);
                        }
                        verifier = new RSASSAVerifier((RSAKey) jwk);
                    } else if (jwk instanceof ECKey) {
                        // build EC signers & verifiers
                        if (jwk.isPrivate()) {
                            signer = new ECDSASigner((ECKey) jwk);
                        }

                        verifier = new ECDSAVerifier((ECKey) jwk);
                    } else if (jwk instanceof OctetSequenceKey) {
                        // build HMAC signers & verifiers

                        if (jwk.isPrivate()) { // technically redundant check because all HMAC keys are private
                            signer = new MACSigner((OctetSequenceKey) jwk);
                        }

                        verifier = new MACVerifier((OctetSequenceKey) jwk);
                    } else {
                        log.warn("Unknown key type: " + jwk);
                    }
                } catch (JOSEException e) {
                    log.warn("Exception loading signer/verifier", e);
                }
            }
        }
    }

    public TokenResponse generateCredentials(Authentication authentication) {
        // Serialize to compact form
        SignedJWT accessToken = generateAccessToken(authentication);
        SignedJWT refreshToken = generateRefreshToken(authentication, accessToken);

        return TokenResponse
            .builder()
            .accessToken(accessToken)
            .refreshToken(refreshToken)
            .expiration(accessTokenDuration)
            .clientId(clientId)
            .issuer(applicationProperties.getEndpoint())
            .build();
    }

    public String generateAccessTokenAsString(Authentication authentication) throws JwtTokenServiceException {
        // Serialize to compact form
        SignedJWT jwt = generateAccessToken(authentication);
        String jwtToken = jwt.serialize();

        if (log.isTraceEnabled()) {
            log.trace("Generated JWT token: {}", jwtToken);
        }

        return jwtToken;
    }

    public SignedJWT generateAccessToken(Authentication authentication) throws JwtTokenServiceException {
        if (signer == null) {
            throw new UnsupportedOperationException("signer not available");
        }

        try {
            JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jwk.getAlgorithm().getName());

            Instant now = Instant.now();

            // build access token claims
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(authentication.getName())
                .issuer(applicationProperties.getEndpoint())
                .issueTime(Date.from(now))
                .audience(applicationProperties.getName())
                .jwtID(UUID.randomUUID().toString())
                .expirationTime(Date.from(now.plusSeconds(accessTokenDuration)));

            //define authorities as claims
            List<String> authorities = authentication
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

            claims.claim("authorities", authorities);

            //add client if set
            if (StringUtils.hasText(clientId)) {
                claims.claim("client_id", clientId);
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

    public SignedJWT generateRefreshToken(Authentication authentication, SignedJWT accessToken)
        throws JwtTokenServiceException {
        if (signer == null) {
            throw new UnsupportedOperationException("signer not available");
        }

        try {
            JWSAlgorithm jwsAlgorithm = JWSAlgorithm.parse(jwk.getAlgorithm().getName());

            Instant now = Instant.now();

            // build refresh token claims
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                .subject(authentication.getName())
                .issuer(applicationProperties.getEndpoint())
                .issueTime(Date.from(now))
                .audience(applicationProperties.getName())
                .jwtID(UUID.randomUUID().toString())
                .expirationTime(Date.from(now.plusSeconds(refreshTokenDuration)));

            //associate access token via hash binding
            String hash = JWKUtils.getAccessTokenHash(jwsAlgorithm, accessToken);
            claims.claim(IdTokenClaimNames.AT_HASH, hash);

            //define authorities as claims
            List<String> authorities = authentication
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

            claims.claim("authorities", authorities);

            //add client if set
            if (StringUtils.hasText(clientId)) {
                claims.claim("client_id", clientId);
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
}
