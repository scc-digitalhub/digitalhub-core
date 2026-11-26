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
package it.smartcommunitylabdhub.core.authorization;

import it.smartcommunitylabdhub.authorization.UserAuthenticationManager;
import it.smartcommunitylabdhub.authorization.UserAuthenticationManagerBuilder;
import it.smartcommunitylabdhub.authorization.providers.NoOpAuthenticationProvider;
import it.smartcommunitylabdhub.core.security.BaseOwnerContextManager;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/**
 * Owner switch backed by the user authentication manager: the owner authentication is
 * processed to get full credentials, the same way async dispatchers restore the owner.
 */
@Component
public class OwnerContextManagerImpl extends BaseOwnerContextManager {

    private UserAuthenticationManager authenticationManager;

    @Autowired
    public void setAuthenticationManagerBuilder(UserAuthenticationManagerBuilder authenticationManagerBuilder) {
        this.authenticationManager = authenticationManagerBuilder.build(new NoOpAuthenticationProvider());
    }

    @Override
    protected Authentication authenticate(@NotNull String owner) {
        UsernamePasswordAuthenticationToken userAuth = new UsernamePasswordAuthenticationToken(
            owner,
            null,
            Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
        );

        //process to get full credentials
        return authenticationManager != null ? authenticationManager.process(userAuth) : userAuth;
    }
}
