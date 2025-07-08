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

package it.smartcommunitylabdhub.commons.config;

import it.smartcommunitylabdhub.commons.Keys;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

// @Configuration
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = true)
@Getter
public class ApplicationProperties {

    @NotBlank
    private String endpoint;

    @NotBlank
    @Pattern(regexp = Keys.SLUG_PATTERN)
    private String name;

    private String contactsEmail;
    private String contactsName;
    private String contactsLink;

    private String description;
    private String version;
    private String level;
    private String api;
    private List<String> profiles;

    // @ConstructorBinding
    public ApplicationProperties(
        String endpoint,
        String name,
        String contactsEmail,
        String contactsName,
        String contactsLink,
        String description,
        String version,
        String level,
        String api,
        List<String> profiles
    ) {
        this.endpoint = endpoint;
        this.name = name;
        this.contactsEmail = contactsEmail;
        this.contactsName = contactsName;
        this.contactsLink = contactsLink;
        this.description = description;
        this.version = version;
        this.profiles = profiles;

        //level can be automagically extracted from version
        this.level = StringUtils.hasText(level) ? level : inferApiLevel(version);

        this.api = api;
    }

    public static String inferApiLevel(String version) {
        if (version == null) {
            return null;
        }

        String[] values = version.replaceAll("[^\\d.]", "").split("\\.");
        if (values.length < 2) {
            return null;
        }

        try {
            int major = Integer.parseInt(values[0]);
            int minor = Integer.parseInt(values[1]);

            int level = (major * 13) + minor;

            return Integer.toString(level);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
