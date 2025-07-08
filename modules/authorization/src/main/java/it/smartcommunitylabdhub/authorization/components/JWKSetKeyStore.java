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

package it.smartcommunitylabdhub.authorization.components;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.KeyUse;
import it.smartcommunitylabdhub.authorization.utils.JWKUtils;
import jakarta.annotation.Nullable;
import java.util.List;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public class JWKSetKeyStore {

    @Getter
    private final JWKSet jwkSet;

    @Getter
    private final String kid;

    public JWKSetKeyStore(Resource location, @Nullable String kid) throws IllegalArgumentException {
        Assert.notNull(location, "location can not be null");

        //load
        log.debug("load keyStore from {}", location);
        this.jwkSet = JWKUtils.loadJwkSet(location);
        JWK jwk = load(jwkSet, kid);
        this.kid = jwk.getKeyID();

        log.debug("use key {} for signing", kid);
    }

    public JWKSetKeyStore() throws JOSEException {
        //create
        log.debug("create temporary keyStore");
        this.jwkSet = JWKUtils.createJwkSet();
        JWK jwk = load(jwkSet, null);
        this.kid = jwk.getKeyID();

        log.debug("use key {} for signing", kid);
    }

    public JWK getJwk() {
        return jwkSet.getKeyByKeyId(kid);
    }

    private JWK load(JWKSet jwkSet, String kid) {
        //if specified, kid must be in set
        if (StringUtils.hasText(kid)) {
            JWK key = jwkSet.getKeyByKeyId(kid);
            Assert.notNull(key, "Provided key_id is not in the set");

            //validate key usage
            Assert.isTrue(
                (key.getKeyUse() == KeyUse.SIGNATURE || key.getKeyUse() == null),
                "key should be usable for signing"
            );

            //use
            return key;
        } else {
            List<JWK> keys = jwkSet.getKeys();
            Assert.notNull(keys, "keystore must contain at least one valid key");
            Assert.isTrue(!keys.isEmpty(), "keystore must contain at least one valid key");

            //fetch the first signing key
            JWK key = keys
                .stream()
                .filter(k -> (k.getKeyUse() == KeyUse.SIGNATURE || k.getKeyUse() == null))
                .findFirst()
                .orElse(null);

            Assert.notNull(key, "No suitable key found in store");

            //use
            return key;
        }
    }
}
