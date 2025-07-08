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

package it.smartcommunitylabdhub.authorization.utils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.SignedJWT;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

public class JWKUtils {

    private static final Integer DEFAULT_RSA_KEY_LENGTH = 2048;

    public static JWK generateRsaJWK(@Nullable String id) throws IllegalArgumentException, JOSEException {
        return generateRsaJWK(id, KeyUse.SIGNATURE, JWSAlgorithm.RS256, DEFAULT_RSA_KEY_LENGTH);
    }

    public static JWK generateRsaJWK(
        @Nullable String id,
        @Nullable KeyUse usage,
        @Nullable JWSAlgorithm algorithm,
        @Nullable Integer length
    ) throws IllegalArgumentException, JOSEException {
        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
        }

        return new RSAKeyGenerator(length).keyUse(usage).keyID(id).algorithm(algorithm).generate();
    }

    public static JWKSet loadJwkSet(Resource location) throws IllegalArgumentException {
        Assert.notNull(location, "Key Set resource cannot be null");

        //read from file
        if (location.exists() && location.isReadable()) {
            try {
                return JWKSet.parse(location.getContentAsString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new IllegalArgumentException("Key Set resource could not be read: " + location);
            } catch (ParseException e) {
                throw new IllegalArgumentException("Key Set resource could not be parsed: " + location);
            }
        } else {
            throw new IllegalArgumentException("Key Set resource could not be read: " + location);
        }
    }

    public static JWKSet createJwkSet() throws JOSEException {
        //generate a set with a single RSA key for signing
        return new JWKSet(JWKUtils.generateRsaJWK(null));
    }

    public static String getAccessTokenHash(JWSAlgorithm signingAlg, SignedJWT token) {
        byte[] tokenBytes = token.serialize().getBytes();
        Base64URL base64 = getHash(signingAlg, tokenBytes);
        return base64.toString();
    }

    public static Base64URL getHash(JWSAlgorithm signingAlg, byte[] bytes) {
        //guess hash algorithm from signing algo
        String alg = null;
        if (
            signingAlg.equals(JWSAlgorithm.HS256) ||
            signingAlg.equals(JWSAlgorithm.ES256) ||
            signingAlg.equals(JWSAlgorithm.RS256) ||
            signingAlg.equals(JWSAlgorithm.PS256)
        ) {
            alg = "SHA-256";
        } else if (
            signingAlg.equals(JWSAlgorithm.ES384) ||
            signingAlg.equals(JWSAlgorithm.HS384) ||
            signingAlg.equals(JWSAlgorithm.RS384) ||
            signingAlg.equals(JWSAlgorithm.PS384)
        ) {
            alg = "SHA-384";
        } else if (
            signingAlg.equals(JWSAlgorithm.ES512) ||
            signingAlg.equals(JWSAlgorithm.HS512) ||
            signingAlg.equals(JWSAlgorithm.RS512) ||
            signingAlg.equals(JWSAlgorithm.PS512)
        ) {
            alg = "SHA-512";
        }
        if (alg == null) {
            return null;
        }

        try {
            MessageDigest hash = MessageDigest.getInstance(alg);
            hash.reset();
            hash.update(bytes);

            //keep left-most half as per spec
            byte[] hashBytes = hash.digest();
            byte[] hashBytesLeftHalf = Arrays.copyOf(hashBytes, hashBytes.length / 2);

            //encode as base64 url
            return Base64URL.encode(hashBytesLeftHalf);
        } catch (NoSuchAlgorithmException e) {
            //shouldn't happen
            return null;
        }
    }

    private JWKUtils() {}
}
