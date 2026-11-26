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
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * Security context for operations performed ON BEHALF of the owner of an object.
 *
 * The authentication is the OWNER's one: every execution (credentials, runtimes, dispatched work)
 * uses it. The actor is the REAL user who requested the operation, kept only for auditing.
 */
public class DelegatedSecurityContext extends SecurityContextImpl {

    private final String actor;

    public DelegatedSecurityContext(Authentication owner, @Nullable String actor) {
        super(owner);
        this.actor = actor;
    }

    /**
     * The real user who requested the operation, if known
     */
    public @Nullable String getActor() {
        return actor;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof DelegatedSecurityContext other)) {
            return false;
        }
        return super.equals(obj) && java.util.Objects.equals(actor, other.actor);
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + (actor != null ? actor.hashCode() : 0);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " [Authentication=" + getAuthentication() + ", Actor=" + actor + "]";
    }
}
