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

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.StringUtils;

// @Configuration
@ConfigurationProperties(prefix = "security", ignoreUnknownFields = true)
@Getter
@Setter
public class SecurityProperties {

    @NestedConfigurationProperty
    private BasicAuthenticationProperties basic;

    @NestedConfigurationProperty
    private JwtAuthenticationProperties jwt;

    @NestedConfigurationProperty
    private OidcAuthenticationProperties oidc;

    public boolean isBasicAuthEnabled() {
        return basic != null && basic.isEnabled();
    }

    public boolean isJwtAuthEnabled() {
        return jwt != null && jwt.isEnabled();
    }

    public boolean isOidcAuthEnabled() {
        return oidc != null && oidc.isEnabled();
    }

    public boolean isRequired() {
        return isBasicAuthEnabled() || isJwtAuthEnabled();
    }

    @Getter
    @Setter
    public static class BasicAuthenticationProperties {

        private String username;
        private String password;

        public boolean isEnabled() {
            return StringUtils.hasText(username) && StringUtils.hasText(password);
        }
    }

    @Getter
    @Setter
    public static class JwtAuthenticationProperties {

        private String issuerUri;
        private String audience;
        private String claim;
        private String username;

        public boolean isEnabled() {
            return StringUtils.hasText(issuerUri) && StringUtils.hasText(audience);
        }
    }

    @Getter
    @Setter
    public static class OidcAuthenticationProperties {

        private String issuerUri;
        private String clientName;
        private String clientId;
        private String clientSecret;
        private List<String> scope;
        private String claim;
        private String usernameAttributeName;

        public boolean isEnabled() {
            return StringUtils.hasText(issuerUri) && StringUtils.hasText(clientId);
        }
    }
}
