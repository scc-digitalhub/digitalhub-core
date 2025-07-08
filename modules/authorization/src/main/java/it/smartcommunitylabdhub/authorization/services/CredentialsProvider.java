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

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public interface CredentialsProvider {
    /*
     * Process the given authentication token to extract credentials
     * that will be added to UserAuth by manager
     */

    @Nullable
    <T extends AbstractAuthenticationToken> Credentials process(@NotNull T token);

    /*
     * Generate or get a set of credentials for the given authenticated user
     */
    @Nullable
    Credentials get(@NotNull UserAuthentication<?> auth);
}
