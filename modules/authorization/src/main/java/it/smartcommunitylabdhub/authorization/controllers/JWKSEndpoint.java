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

import com.nimbusds.jose.jwk.JWKSet;
import it.smartcommunitylabdhub.authorization.components.JWKSetKeyStore;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JWKSEndpoint {

    public static final String JWKS_URL = "/auth/jwks";

    @Autowired
    private JWKSetKeyStore jwkSetKeyStore;

    @Autowired
    private SecurityProperties securityProperties;

    @Value("${jwt.cache-control}")
    private String cacheControl;

    @GetMapping(JWKS_URL)
    public ResponseEntity<Map<String, Object>> getJWKInfo() {
        if (!securityProperties.isRequired()) {
            throw new UnsupportedOperationException();
        }

        //expose the entire jwkSet as JSON
        JWKSet jwkSet = jwkSetKeyStore.getJwkSet();
        Map<String, Object> jwkSetMap = jwkSet.toJSONObject();

        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, cacheControl).body(jwkSetMap);
    }
}
