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

package it.smartcommunitylabdhub.authorization.config;

import com.nimbusds.jose.JOSEException;
import it.smartcommunitylabdhub.authorization.components.JWKSetKeyStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

@Configuration
public class KeyStoreConfig {

    @Value("${jwt.keystore.path}")
    private String path;

    @Value("${jwt.keystore.kid}")
    private String kid;

    @Autowired
    private ResourceLoader resourceLoader;

    private JWKSetKeyStore keyStore;

    @Bean
    public JWKSetKeyStore getJWKSetKeyStore() throws JOSEException {
        if (keyStore != null) {
            //re-use because we load from config *before* services are built
            return keyStore;
        }

        if (StringUtils.hasText(path) && !path.contains(":")) {
            //no protocol specified, try as file by default
            this.path = "file:" + path;
        }

        Resource location = resourceLoader.getResource(path);
        if (location != null && location.exists() && location.isReadable()) {
            // Load from resource
            keyStore = new JWKSetKeyStore(location, kid);
        } else {
            // Generate new in-memory keystore
            keyStore = new JWKSetKeyStore();
        }

        return keyStore;
    }
}
