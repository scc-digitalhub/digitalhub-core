/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

class BaseOwnerContextManagerTest {

    private final List<String> authenticated = new ArrayList<>();

    private final BaseOwnerContextManager manager = new BaseOwnerContextManager() {
        @Override
        protected Authentication authenticate(String owner) {
            authenticated.add(owner);
            return new TestingAuthenticationToken(owner, null, "ROLE_USER");
        }
    };

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        authenticated.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static void loginAs(String user) {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(user, null));
    }

    private static String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : null;
    }

    @Test
    void switchesToOwnerKeepingCallerAsActor() {
        loginAs("bob");
        SecurityContext before = SecurityContextHolder.getContext();

        try (OwnerContextManager.Scope scope = manager.asOwner("alice")) {
            assertThat(currentUser()).isEqualTo("alice");
            assertThat(SecurityContextHolder.getContext()).isInstanceOf(DelegatedSecurityContext.class);
            assertThat(OwnerContextManager.currentActor()).isEqualTo("bob");
        }

        //previous context restored
        assertThat(SecurityContextHolder.getContext()).isSameAs(before);
        assertThat(currentUser()).isEqualTo("bob");
        assertThat(OwnerContextManager.currentActor()).isEqualTo("bob");
    }

    @Test
    void ownerCallingKeepsContextUntouched() {
        loginAs("alice");
        SecurityContext before = SecurityContextHolder.getContext();

        try (OwnerContextManager.Scope scope = manager.asOwner("alice")) {
            assertThat(SecurityContextHolder.getContext()).isSameAs(before);
            assertThat(OwnerContextManager.currentActor()).isEqualTo("alice");
        }

        assertThat(authenticated).isEmpty();
    }

    @Test
    void missingOwnerKeepsContextUntouched() {
        loginAs("bob");
        SecurityContext before = SecurityContextHolder.getContext();

        try (OwnerContextManager.Scope scope = manager.asOwner(null)) {
            assertThat(SecurityContextHolder.getContext()).isSameAs(before);
        }
        assertThat(authenticated).isEmpty();
    }

    @Test
    void nestedScopesKeepOriginalActorAndRestoreInOrder() {
        loginAs("admin");

        try (OwnerContextManager.Scope parent = manager.asOwner("alice")) {
            assertThat(currentUser()).isEqualTo("alice");

            //cascade on a child owned by another user
            try (OwnerContextManager.Scope child = manager.asOwner("carol")) {
                assertThat(currentUser()).isEqualTo("carol");
                //the real requester is still the original caller
                assertThat(OwnerContextManager.currentActor()).isEqualTo("admin");
            }

            assertThat(currentUser()).isEqualTo("alice");
            assertThat(OwnerContextManager.currentActor()).isEqualTo("admin");
        }

        assertThat(currentUser()).isEqualTo("admin");
    }

    @Test
    void asyncContextWithoutActorUsesOwnerAsActor() {
        //async paths restore the owner directly, with no requester
        loginAs("alice");
        assertThat(OwnerContextManager.currentActor()).isEqualTo("alice");

        SecurityContextHolder.clearContext();
        assertThat(OwnerContextManager.currentActor()).isNull();
    }
}
