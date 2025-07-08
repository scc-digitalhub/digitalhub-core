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

package it.smartcommunitylabdhub.core.components.security;

import java.io.Serializable;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.Assert;

public class SecureCredentialsHelper {

    public static Serializable extractCredentials(Authentication auth) {
        if (auth == null) {
            return null;
        }

        if (auth instanceof JwtAuthenticationToken) {
            return convertCredentials((JwtAuthenticationToken) auth);
        }

        return null;
    }

    public static Serializable convertCredentials(JwtAuthenticationToken auth) {
        Assert.notNull(auth, "auth token can not be null");
        Assert.notNull(auth.getToken(), "jwt token can not be null");

        //token value is the credential
        return auth.getToken();
    }

    private SecureCredentialsHelper() {}
}
