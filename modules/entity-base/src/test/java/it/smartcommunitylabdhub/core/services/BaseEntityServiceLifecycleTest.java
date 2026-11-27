/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.core.lifecycle.BaseLifecycleManager;
import it.smartcommunitylabdhub.core.repositories.BaseEntityRepositoryImpl;
import it.smartcommunitylabdhub.core.test.TestDTO;
import it.smartcommunitylabdhub.core.test.TestEntity;
import it.smartcommunitylabdhub.core.test.TestFsm;
import it.smartcommunitylabdhub.core.test.TestJpaRepository;
import it.smartcommunitylabdhub.core.test.TestPersistenceConfig;
import it.smartcommunitylabdhub.core.test.TestPersistenceConfig.EventCollector;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Service + lifecycle + repository working together
 */
@SpringBootTest(
    classes = TestPersistenceConfig.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:entitybase-service;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    }
)
class BaseEntityServiceLifecycleTest {

    @Autowired
    private BaseEntityRepositoryImpl<TestEntity, TestDTO> repository;

    @Autowired
    private TestJpaRepository jpaRepository;

    @Autowired
    private LocalEntityLockManager lockManager;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EventCollector events;

    private TestFsm fsm;
    private BaseLifecycleManager<TestDTO> manager;
    private BaseEntityServiceImpl<TestDTO, TestEntity> service;

    //finalized ids, with the turn status observed during finalization
    private final List<String> finalized = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        jpaRepository.deleteAllInBatch();
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
        events.clear();
        finalized.clear();

        fsm = new TestFsm();
        manager = new BaseLifecycleManager<TestDTO>(TestDTO.class) {};
        manager.setEntityRepository(repository);
        manager.setEventPublisher(e -> {});
        manager.setLockManager(lockManager);
        manager.setFsmFactory(fsm.factory());
        manager.afterPropertiesSet();

        service = new BaseEntityServiceImpl<TestDTO, TestEntity>(repository) {};
        service.setLifecycleManager(manager);
        service.setLockManager(lockManager);
        service.setFinalizer(dto ->
            finalized.add(dto.getId() + ":held=" + lockManager.isHeldByCurrentThread(TestDTO.class, dto.getId()))
        );
        service.afterPropertiesSet();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private static TestDTO dto(String id, String state) {
        Map<String, Serializable> status = new HashMap<>();
        if (state != null) {
            status.put("state", state);
        }
        return TestDTO.builder()
            .id(id)
            .name("n-" + id)
            .kind("test")
            .project("prj")
            .spec(new HashMap<>(Map.of("a", 1)))
            .status(status)
            .build();
    }

    private String storedState(String id) throws Exception {
        return (String) repository.get(id).getStatus().get("state");
    }

    @Test
    void createWithStateWalksFromCreated() throws Exception {
        TestDTO res = service.create(dto("e1", "RUNNING"));

        assertThat(res.getStatus()).containsEntry("state", "RUNNING");
        assertThat(fsm.calls()).containsExactly("CREATED>BUILT", "BUILT>READY", "READY>RUNNING");
        assertThat(storedState("e1")).isEqualTo("RUNNING");
    }

    @Test
    void createWithoutPathFailsAfterPersistingCreated() throws Exception {
        //CREATED is persisted first by design, the walk is then planned and rejected
        assertThatThrownBy(() -> service.create(dto("e1", "UNKNOWN"))).isInstanceOf(InvalidTransitionException.class);
        assertThat(storedState("e1")).isEqualTo("CREATED");
    }

    @Test
    void updateWithoutStateKeepsStoredState() throws Exception {
        service.create(dto("e1", "BUILT"));

        TestDTO update = dto("e1", null);
        service.update("e1", update);

        assertThat(storedState("e1")).isEqualTo("BUILT");
    }

    @Test
    void updateWithStateChangeWalksTheFsm() throws Exception {
        service.create(dto("e1", null));

        service.update("e1", dto("e1", "READY"));

        assertThat(fsm.calls()).containsExactly("CREATED>BUILT", "BUILT>READY");
        assertThat(storedState("e1")).isEqualTo("READY");
    }

    @Test
    void staleStateCanNotBringObjectBack() throws Exception {
        service.create(dto("e1", null));
        TestDTO stale = service.get("e1");

        //the object moves forward meanwhile
        manager.perform(stale, "BUILD");

        //a stale update asking for CREATED is evaluated against the actual state
        assertThatThrownBy(() -> service.update("e1", stale)).isInstanceOf(InvalidTransitionException.class);
        assertThat(storedState("e1")).isEqualTo("BUILT");

        //a merge without state keeps the actual state
        TestDTO patch = dto("e1", null);
        patch.setStatus(new HashMap<>(Map.of("note", "x")));
        service.update("e1", patch, false, true);
        assertThat(repository.get("e1").getStatus()).containsEntry("state", "BUILT").containsEntry("note", "x");
    }

    @Test
    void deleteWithLifecycleLeavesFinalizationToTheLifecycle() throws Exception {
        service.create(dto("e1", null));

        service.delete("e1", true);

        //finalization runs as effects of the delete step, never via the service finalizer
        assertThat(repository.find("e1")).isNull();
        assertThat(finalized).isEmpty();
    }

    @Test
    void failedDeleteNeverFinalizes() throws Exception {
        service.create(dto("e1", null));
        fsm.on("DELETED", (cur, next, ev, ctx, in) -> {
            throw new CoreRuntimeException("delete failed");
        });

        service.delete("e1", true);

        assertThat(storedState("e1")).isEqualTo("ERROR");
        assertThat(finalized).isEmpty();
    }

    @Test
    void deleteWithoutLifecycleFinalizesAfterRemoval() throws Exception {
        service.setLifecycleManager(null);
        service.create(dto("e1", null));

        service.delete("e1", true);

        assertThat(repository.find("e1")).isNull();
        assertThat(finalized).containsExactly("e1:held=false");
    }

    @Test
    void bulkDeleteWithoutCascadeRemovesOneByOne() throws Exception {
        service.create(dto("e1", null));
        service.create(dto("e2", null));
        events.clear();

        service.deleteByProject("prj", false);

        assertThat(jpaRepository.count()).isZero();
        //no lifecycle, no finalizer: plain store deletes, one event each
        assertThat(fsm.calls()).isEmpty();
        assertThat(finalized).isEmpty();
        assertThat(events.getEvents())
            .extracting(EntityEvent::getAction)
            .containsExactly(EntityAction.DELETE, EntityAction.DELETE);
    }

    @Test
    void bulkDeleteWithCascadeRunsTheFullFlowSequentially() throws Exception {
        service.create(dto("e1", null));
        service.create(dto("e2", null));

        service.deleteByProject("prj", true);

        assertThat(jpaRepository.count()).isZero();
        assertThat(fsm.calls()).containsExactlyInAnyOrder("CREATED>DELETED", "CREATED>DELETED");
        //with a lifecycle finalization is part of the lifecycle, not of the service
        assertThat(finalized).isEmpty();
    }
}
