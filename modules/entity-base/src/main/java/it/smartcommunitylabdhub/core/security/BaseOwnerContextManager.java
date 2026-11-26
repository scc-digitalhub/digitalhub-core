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
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Base owner switch: builds the owner authentication via {@link #authenticate(String)}
 * and installs a {@link DelegatedSecurityContext} carrying the real user as actor.
 */
@Slf4j
public abstract class BaseOwnerContextManager implements OwnerContextManager {

    /**
     * Build the full authentication (with credentials) for the given owner
     */
    protected abstract Authentication authenticate(@NotNull String owner);

    @Override
    public Scope asOwner(@Nullable String owner) {
        if (owner == null) {
            //no owner known: keep the current context
            return Scope.noop();
        }

        SecurityContext previous = SecurityContextHolder.getContext();
        Authentication current = previous != null ? previous.getAuthentication() : null;
        if (current != null && owner.equals(current.getName())) {
            //already acting as the owner (keeps the actor, if any)
            return Scope.noop();
        }

        String actor = OwnerContextManager.actor(previous);
        Authentication auth = authenticate(owner);
        if (auth == null) {
            throw new IllegalStateException("unable to build authentication for owner " + owner);
        }

        log.debug("switch context to owner {} on behalf of {}", owner, actor);
        SecurityContextHolder.setContext(new DelegatedSecurityContext(auth, actor));

        return () -> SecurityContextHolder.setContext(previous);
    }
}
