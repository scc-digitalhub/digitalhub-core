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

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public class UserAuthenticationHelper {

    public static UserAuthentication<?> getUserAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null) {
            return null;
        }

        if (auth instanceof UserAuthentication) {
            return (UserAuthentication<?>) auth;
        }

        //workaround: inflate basic auth tokens
        //TODO use authManager to produce proper authentication
        //NOTE: this flow does not propagate ROLE_ADMIN authority
        if (auth instanceof UsernamePasswordAuthenticationToken) {
            UserAuthentication<UsernamePasswordAuthenticationToken> user = new UserAuthentication<>(
                (UsernamePasswordAuthenticationToken) auth,
                auth.getName(),
                auth.getAuthorities()
            );

            //update context
            SecurityContextHolder.getContext().setAuthentication(user);

            return user;
        }

        return null;
    }

    private UserAuthenticationHelper() {}
}
