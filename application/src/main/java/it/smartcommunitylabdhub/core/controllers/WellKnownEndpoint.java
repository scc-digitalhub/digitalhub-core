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
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WellKnownEndpoint {

    @Autowired
    private ApplicationProperties applicationProperties;

    @Value("${jwt.cache-control}")
    private String cacheControl;

    private Instant expires = Instant.now().plus(365, ChronoUnit.DAYS);

    @GetMapping(value = { "/.well-known/security.txt" })
    public ResponseEntity<String> securityTxt() {
        Map<String, String> config = new HashMap<>();
        if (StringUtils.hasText(applicationProperties.getContactsEmail())) {
            config.put("Contact", "mailto:" + applicationProperties.getContactsEmail());
        }
        if (StringUtils.hasText(applicationProperties.getContactsLink())) {
            config.put("Policy", applicationProperties.getContactsLink());
        }

        config.put("Preferred-Languages", "en, it");
        config.put("Expires", expires.toString());

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : config.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        return ResponseEntity
            .ok()
            .header(HttpHeaders.CACHE_CONTROL, cacheControl)
            .contentType(MediaType.TEXT_PLAIN)
            .body(sb.toString());
    }
}
