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
package it.smartcommunitylabdhub.core.security;

import jakarta.annotation.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Switches the security context to the OWNER of an object.
 *
 * Every operation on an existing object is performed on behalf of its owner: the caller identity
 * is used only for authorization (before the operation) and auditing (as actor).
 * Scopes nest: closing a scope restores the previous context.
 */
public interface OwnerContextManager {
    /**
     * Run as the given owner until the returned scope is closed.
     * When the current authentication already is the owner, the context is left untouched.
     */
    Scope asOwner(@Nullable String owner);

    /**
     * Resolve the real user (actor) from a context: the delegating actor when present,
     * the authenticated user otherwise
     */
    static @Nullable String actor(@Nullable SecurityContext context) {
        if (context == null) {
            return null;
        }

        if (context instanceof DelegatedSecurityContext delegated && delegated.getActor() != null) {
            return delegated.getActor();
        }

        Authentication auth = context.getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    static @Nullable String currentActor() {
        return actor(SecurityContextHolder.getContext());
    }

    @FunctionalInterface
    interface Scope extends AutoCloseable {
        @Override
        void close();

        static Scope noop() {
            return () -> {};
        }
    }
}
