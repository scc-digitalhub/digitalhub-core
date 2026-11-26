/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.security.BaseOwnerContextManager;
import it.smartcommunitylabdhub.core.security.OwnerContextManager;
import it.smartcommunitylabdhub.core.test.TestDTO;
import it.smartcommunitylabdhub.core.test.TestEntity;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Operations on existing objects are performed on behalf of their owner,
 * while the requester is kept as actor
 */
class BaseEntityServiceOwnerTest {

    private SearchableEntityRepository<TestEntity, TestDTO> repository;
    private BaseEntityServiceImpl<TestDTO, TestEntity> service;

    //identities observed while the store is invoked: [user, actor]
    private final List<String[]> observed = new ArrayList<>();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() throws Exception {
        repository = mock(SearchableEntityRepository.class);
        service = new BaseEntityServiceImpl<TestDTO, TestEntity>(repository) {};
        service.setOwnerContextManager(
            new BaseOwnerContextManager() {
                @Override
                protected Authentication authenticate(String owner) {
                    return new TestingAuthenticationToken(owner, null, "ROLE_USER");
                }
            }
        );
        service.afterPropertiesSet();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", null));
        observed.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static TestDTO owned(String id, String owner) {
        Map<String, Serializable> status = new HashMap<>(Map.of("state", "CREATED"));
        return TestDTO.builder()
            .id(id)
            .name("n")
            .kind("test")
            .project("prj")
            .user(owner)
            .spec(new HashMap<>(Map.of("a", 1)))
            .status(status)
            .build();
    }

    private void record() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        observed.add(new String[] { auth != null ? auth.getName() : null, OwnerContextManager.currentActor() });
    }

    @Test
    void updateRunsAsOwnerWithRequesterAsActor() throws Exception {
        when(repository.get("e1")).thenReturn(owned("e1", "alice"));
        when(repository.update(eq("e1"), any())).thenAnswer(inv -> {
            record();
            return inv.getArgument(1);
        });

        service.update("e1", owned("e1", "alice"));

        assertThat(observed).hasSize(1);
        assertThat(observed.get(0)).containsExactly("alice", "bob");
        //caller context restored
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("bob");
    }

    @Test
    void deleteRunsAsOwnerWithRequesterAsActor() throws Exception {
        when(repository.find("e1")).thenReturn(owned("e1", "alice"));
        org.mockito.Mockito.doAnswer(inv -> {
            record();
            return null;
        })
            .when(repository)
            .delete("e1");

        service.delete("e1", false);

        assertThat(observed).hasSize(1);
        assertThat(observed.get(0)).containsExactly("alice", "bob");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("bob");
    }

    @Test
    void updateRejectsDtoTargetingAnotherEntity() throws Exception {
        when(repository.get("e1")).thenReturn(owned("e1", "alice"));

        assertThatThrownBy(() -> service.update("e1", owned("e2", "alice"))).isInstanceOf(
            IllegalArgumentException.class
        );
        verify(repository, never()).update(any(), any());
    }

    @Test
    void updateFillsMissingDtoId() throws Exception {
        when(repository.get("e1")).thenReturn(owned("e1", "alice"));
        when(repository.update(eq("e1"), any())).thenAnswer(inv -> inv.getArgument(1));

        TestDTO dto = owned(null, "alice");
        TestDTO res = service.update("e1", dto);

        assertThat(res.getId()).isEqualTo("e1");
    }
}
