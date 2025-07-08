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

package it.smartcommunitylabdhub.authorization.repositories;

import it.smartcommunitylabdhub.authorization.model.AuthorizationRequest;
import it.smartcommunitylabdhub.authorization.model.TokenRequest;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.util.Pair;
import org.springframework.util.Assert;

public class AuthorizationRequestStore implements Serializable {

    private final Map<String, Pair<AuthorizationRequest, UserAuthentication<?>>> requests;

    public AuthorizationRequestStore() {
        this.requests = new ConcurrentHashMap<>();
    }

    public AuthorizationRequest find(TokenRequest tokenRequest) {
        Assert.notNull(tokenRequest, "tokenRequest can not be null or empty");
        return find(extractKey(tokenRequest));
    }

    public AuthorizationRequest find(String key) {
        Assert.hasText(key, "key can not be null or empty");
        Pair<AuthorizationRequest, UserAuthentication<?>> entry = requests.get(key);

        if (entry == null) {
            return null;
        }

        return entry.getFirst();
    }

    public Collection<AuthorizationRequest> findAll() {
        return Collections.unmodifiableCollection(requests.values().stream().map(p -> p.getFirst()).toList());
    }

    public String store(AuthorizationRequest request, UserAuthentication<?> auth) {
        String key = extractKey(request);
        requests.put(key, Pair.of(request, auth));

        return key;
    }

    public UserAuthentication<?> consume(TokenRequest tokenRequest) {
        Assert.notNull(tokenRequest, "tokenRequest can not be null or empty");
        return consume(extractKey(tokenRequest));
    }

    public UserAuthentication<?> consume(String key) {
        Assert.hasText(key, "key can not be null or empty");
        Pair<AuthorizationRequest, UserAuthentication<?>> entry = requests.remove(key);

        if (entry == null) {
            throw new IllegalArgumentException();
        }

        return entry.getSecond();
    }

    public void remove(String key) {
        requests.remove(key);
    }

    private String extractKey(AuthorizationRequest request) {
        //we use clientId+redirect as key because we receive those from token request as well
        return request.getClientId() + "|" + request.getRedirectUri();
    }

    private String extractKey(TokenRequest request) {
        //we use clientId+redirect as key because we receive those from token request as well
        return request.getClientId() + "|" + request.getRedirectUri();
    }
}
