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

package it.smartcommunitylabdhub.authorization.services;

import it.smartcommunitylabdhub.authorization.model.PersonalAccessToken;
import it.smartcommunitylabdhub.authorization.model.RefreshToken;
import it.smartcommunitylabdhub.authorization.model.TokenResponse;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface TokenService {
    TokenResponse generatePersonalAccessToken(
        @NotNull UserAuthentication<?> authentication,
        @Nullable List<String> scopes
    );
    TokenResponse generatePersonalAccessToken(
        @NotNull UserAuthentication<?> authentication,
        String name,
        @Nullable List<String> scopes
    );

    TokenResponse generateAccessToken(
        @NotNull UserAuthentication<?> authentication,
        boolean withCredentials,
        boolean withRefresh
    );

    TokenResponse generateAccessToken(
        @NotNull UserAuthentication<?> authentication,
        boolean withCredentials,
        boolean withRefresh,
        boolean withExchange
    );

    //TODO evaluate common interface for all token types
    public List<PersonalAccessToken> getPersonalAccessTokens(@NotNull UserAuthentication<?> authentication);

    public @Nullable PersonalAccessToken getPersonalAccessToken(
        @NotNull UserAuthentication<?> authentication,
        @NotNull String tokenId
    );

    public void revokePersonalAccessToken(@NotNull UserAuthentication<?> authentication, @NotNull String tokenId);

    public List<RefreshToken> getRefreshTokens(@NotNull UserAuthentication<?> authentication);

    public @Nullable RefreshToken getRefreshToken(
        @NotNull UserAuthentication<?> authentication,
        @NotNull String tokenId
    );

    public void revokeRefreshToken(@NotNull UserAuthentication<?> authentication, @NotNull String tokenId);
}
