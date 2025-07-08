/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.authorization.model;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import jakarta.validation.constraints.NotNull;
import java.util.Collection;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.util.Assert;

public class UserAuthentication<T extends AbstractAuthenticationToken> extends AbstractAuthenticationToken {

    private static final long serialVersionUID = Keys.SERIAL_VERSION_UID;

    private final T token;
    private final String username;

    private List<Credentials> credentials;

    public UserAuthentication(@NotNull T token) {
        super(token.getAuthorities());
        Assert.notNull(token, "token cannot be null");
        this.token = token;
        this.username = token.getName();
        if (token.isAuthenticated()) {
            setAuthenticated(true);
        }
    }

    public UserAuthentication(T token, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        Assert.notNull(token, "token cannot be null");
        this.token = token;
        this.username = token.getName();

        setAuthenticated(true);
    }

    public UserAuthentication(T token, String username, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        Assert.notNull(token, "token cannot be null");
        this.token = token;
        this.username = username;

        setAuthenticated(true);
    }

    @Override
    public List<Credentials> getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return token;
    }

    public String getUsername() {
        return username;
    }

    public T getToken() {
        return token;
    }

    public void setCredentials(List<Credentials> credentials) {
        this.credentials = credentials;
    }
}
