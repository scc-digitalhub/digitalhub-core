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

package it.smartcommunitylabdhub.trinodb.config;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@NoArgsConstructor
@ToString
@ConfigurationProperties(prefix = "providers.trinodb", ignoreUnknownFields = true)
public class TrinoDbProperties {

    private Boolean enable;

    private String scheme;
    private String host;
    private Integer port;
    private String catalog;
    private String url;

    private String user;
    private String password;

    private Boolean useJwtToken;
    private Boolean useCoreCredentials;

    public boolean isEnabled() {
        return ((enable != null && enable.booleanValue()) && (host != null && !host.isBlank()));
    }
}
