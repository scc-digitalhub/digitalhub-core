package it.smartcommunitylabdhub.authorization.services;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import it.smartcommunitylabdhub.authorization.components.JWKSetKeyStore;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;

import java.io.Serializable;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class JwtTokenService {

    @Autowired
    private JWKSetKeyStore keyStoreUtil;

    @Autowired
    ApplicationProperties applicationProperties;

    @Autowired
    SecurityProperties securityProperties;

    // Default to 30 days in milliseconds (30 * 24 * 60 * 60 * 1000)
    @Value("${jwt.expiration:2592000000}")
    private long jwtExpiration;

    @Value("${jwt.keyId:kid}")
    private String keyId;

    public String generateToken(Authentication authentication)
            throws JwtTokenServiceException {
        try {

            // Extract claims from authentication if it's a JwtAuthenticationToken


            JWK jwk = keyStoreUtil.getJwk();
            RSAPrivateKey privateKey = jwk.toRSAKey().toRSAPrivateKey();
            RSASSASigner signer = new RSASSASigner(privateKey);

            // Prepare JWT claims
            JWTClaimsSet.Builder claimsSetBuilder = new JWTClaimsSet.Builder()
                    .subject(authentication.getName())
                    .issuer(applicationProperties.getEndpoint())
                    .issueTime(new Date())
                    .audience(applicationProperties.getName())
                    .jwtID(UUID.randomUUID().toString())
                    .expirationTime(new Date(System.currentTimeMillis() + jwtExpiration));

            List<String> additionalClaims = extractClaims(authentication);
            if(additionalClaims != null){
                claimsSetBuilder.claim(securityProperties.getJwt().getClaim(), additionalClaims);
            }

            // Build claims set
            JWTClaimsSet claimsSet = claimsSetBuilder.build();

            // Create signed JWT
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256)
                            .keyID(jwk.getKeyID()).build(),
                    claimsSet);

            // Compute the RSA signature
            signedJWT.sign(signer);

            // Serialize to compact form
            String jwtToken = signedJWT.serialize();

            log.info("Generated JWT token: {}", jwtToken);

            return jwtToken;
        } catch (JOSEException e) {
            log.error("Error generating JWT token", e);
            return null;
        }
    }


    private List<String> extractClaims(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthToken) {
            Jwt jwt = jwtAuthToken.getToken();
            return jwt.getClaimAsStringList(securityProperties.getJwt().getClaim());
        } else {
            log.warn("Authentication is not of type JwtAuthenticationToken");
            return null;
        }
    }

    public static class JwtTokenServiceException extends RuntimeException {
        public JwtTokenServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
