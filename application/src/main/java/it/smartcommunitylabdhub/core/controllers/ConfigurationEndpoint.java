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

package it.smartcommunitylabdhub.core.controllers;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConfigurationEndpoint {

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ApplicationProperties applicationProperties;

    @Value("${jwt.cache-control}")
    private String cacheControl;

    //cache, we don't expect config to be mutable!
    private Map<String, Serializable> config = null;

    @GetMapping(value = { "/.well-known/configuration" })
    public ResponseEntity<Map<String, Serializable>> getConfiguration() {
        if (config == null) {
            config = generate();
        }

        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, cacheControl).body(config);
    }

    private Map<String, Serializable> generate() {
        Map<String, Serializable> map = new TreeMap<>();
        if (configurationService != null) {
            configurationService.getConfigurations().forEach(c -> map.putAll(c.toMap()));
        }

        //always override core props
        map.put("dhcore_endpoint", applicationProperties.getEndpoint());

        return Collections.unmodifiableMap(map);
    }
}
