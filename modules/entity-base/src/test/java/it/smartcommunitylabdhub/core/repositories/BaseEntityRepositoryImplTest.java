/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.repositories;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.core.test.TestDTO;
import it.smartcommunitylabdhub.core.test.TestEntity;
import it.smartcommunitylabdhub.core.test.TestJpaRepository;
import it.smartcommunitylabdhub.core.test.TestPersistenceConfig;
import it.smartcommunitylabdhub.core.test.TestPersistenceConfig.EventCollector;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager.EntityLock;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(
    classes = TestPersistenceConfig.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:entitybase-repo;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    }
)
class BaseEntityRepositoryImplTest {

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

    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAllInBatch();
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
        events.clear();
        executor = Executors.newFixedThreadPool(2);
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        SecurityContextHolder.clearContext();
    }

    private static TestDTO dto(String id, Map<String, Serializable> spec) {
        return TestDTO.builder()
            .id(id)
            .name("name-" + id)
            .kind("test")
            .project("prj")
            .spec(new HashMap<>(spec))
            .status(new HashMap<>(Map.of("state", "CREATED")))
            .build();
    }

    @Test
    void createAndGet() throws Exception {
        TestDTO created = repository.create(dto("e1", Map.of("a", 1)));
        assertThat(created.getId()).isEqualTo("e1");
        assertThat(created.getUser()).isEqualTo("alice");

        TestDTO read = repository.get("e1");
        assertThat(read.getName()).isEqualTo("name-e1");
        assertThat(read.getSpec()).containsEntry("a", 1);
        assertThat(read.getUser()).isEqualTo("alice");
    }

    @Test
    void createGeneratesMissingId() throws Exception {
        TestDTO created = repository.create(dto(null, Map.of()));
        assertThat(created.getId()).isNotBlank();
        assertThat(repository.find(created.getId())).isNotNull();
    }

    @Test
    void readsReturnIndependentInstances() throws Exception {
        repository.create(dto("e1", Map.of("a", 1)));

        TestDTO first = repository.get("e1");
        TestDTO second = repository.get("e1");
        assertThat(first).isNotSameAs(second);

        //mutating a returned dto must never leak into the cache
        first.getSpec().put("a", 99);
        first.setName("changed");

        TestDTO third = repository.get("e1");
        assertThat(third.getSpec()).containsEntry("a", 1);
        assertThat(third.getName()).isEqualTo("name-e1");
    }

    @Test
    void createWithExistingIdFailsAndDoesNotOverwrite() throws Exception {
        repository.create(dto("e1", Map.of("a", 1)));

        assertThatThrownBy(() -> repository.create(dto("e1", Map.of("a", 2)))).isInstanceOf(
            DuplicatedEntityException.class
        );

        assertThat(repository.get("e1").getSpec()).containsEntry("a", 1);
        assertThat(jpaRepository.count()).isEqualTo(1);
    }

    @Test
    void updateWritesThroughCacheAndStore() throws Exception {
        repository.create(dto("e1", Map.of("a", 1)));
        //warm the cache
        repository.get("e1");

        repository.update("e1", dto("e1", Map.of("a", 2)));

        assertThat(repository.get("e1").getSpec()).containsEntry("a", 2);
        //store matches
        assertThat(new TestConverters().dtoBuilder().convert(jpaRepository.findById("e1").orElseThrow()).getSpec())
            .containsEntry("a", 2);
    }

    @Test
    void updateKeepsFixedFields() throws Exception {
        repository.create(dto("e1", Map.of("a", 1)));

        TestDTO changed = dto("e1", Map.of("a", 2));
        changed.setName("other");
        changed.setKind("otherkind");
        changed.setProject("otherprj");

        //a different user performs the update
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", null));
        TestDTO updated = repository.update("e1", changed);

        assertThat(updated.getName()).isEqualTo("name-e1");
        assertThat(updated.getKind()).isEqualTo("test");
        assertThat(updated.getProject()).isEqualTo("prj");
        assertThat(updated.getUser()).isEqualTo("alice");
        assertThat(updated.getSpec()).containsEntry("a", 2);
        assertThat(jpaRepository.findById("e1").orElseThrow().getUpdatedBy()).isEqualTo("bob");
    }

    @Test
    void updateRejectsMismatchingIdAndMissingEntity() throws Exception {
        repository.create(dto("e1", Map.of()));

        assertThatThrownBy(() -> repository.update("e1", dto("e2", Map.of()))).isInstanceOf(
            IllegalArgumentException.class
        );
        assertThatThrownBy(() -> repository.update("missing", dto(null, Map.of()))).isInstanceOf(
            NoSuchEntityException.class
        );
    }

    @Test
    void deleteInvalidatesCache() throws Exception {
        repository.create(dto("e1", Map.of()));
        repository.get("e1");

        repository.delete("e1");

        assertThat(repository.find("e1")).isNull();
        assertThatThrownBy(() -> repository.get("e1")).isInstanceOf(NoSuchEntityException.class);
        //deleting a missing entity is a no-op
        repository.delete("e1");
    }

    @Test
    void cacheServesReadsUntilRepositoryWrites() throws Exception {
        repository.create(dto("e1", Map.of("a", 1)));
        repository.get("e1");

        //change the row bypassing the repository
        TestEntity e = jpaRepository.findById("e1").orElseThrow();
        e.setName("bypassed");
        jpaRepository.saveAndFlush(e);

        //cache hit: the store is not read again
        assertThat(repository.get("e1").getName()).isEqualTo("name-e1");

        //a repository write refreshes the cache
        repository.update("e1", dto("e1", Map.of("a", 3)));
        assertThat(repository.get("e1").getSpec()).containsEntry("a", 3);
    }

    @Test
    void eventsAreDeliveredAfterCommitWithSnapshots() throws Exception {
        repository.create(dto("e1", Map.of("a", 1)));
        repository.update("e1", dto("e1", Map.of("a", 2)));
        repository.delete("e1");

        List<EntityEvent<TestEntity>> received = events.getEvents();
        assertThat(received).extracting(EntityEvent::getAction).containsExactly(
            EntityAction.CREATE,
            EntityAction.UPDATE,
            EntityAction.DELETE
        );

        EntityEvent<TestEntity> update = received.get(1);
        assertThat(update.getPrev()).isNotNull();
        assertThat(update.getPrev().getCreatedBy()).isEqualTo("alice");
        assertThat(received.get(2).getEntity().getCreatedBy()).isEqualTo("alice");
    }

    @Test
    void bulkDeleteEventsKeepAuditFields() throws Exception {
        repository.create(dto("e1", Map.of()));
        repository.create(dto("e2", Map.of()));
        repository.get("e1");
        events.clear();

        long count = repository.deleteAll();
        assertThat(count).isEqualTo(2);

        assertThat(events.getEvents()).hasSize(2);
        assertThat(events.getEvents()).allSatisfy(ev -> {
            assertThat(ev.getAction()).isEqualTo(EntityAction.DELETE);
            assertThat(ev.getEntity().getCreatedBy()).isEqualTo("alice");
        });

        //cache invalidated
        assertThat(repository.find("e1")).isNull();
    }

    @Test
    void writesWaitForTheTurnWhileReadsDoNot() throws Exception {
        repository.create(dto("e1", Map.of("a", 1)));

        Future<TestDTO> update;
        try (EntityLock turn = lockManager.acquire(TestDTO.class, "e1")) {
            update = executor.submit(() -> repository.update("e1", dto("e1", Map.of("a", 2))));

            //write is waiting for the turn
            assertThatThrownBy(() -> update.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);

            //reads never take the turn
            Future<TestDTO> read = executor.submit(() -> repository.get("e1"));
            assertThat(read.get(300, TimeUnit.MILLISECONDS).getSpec()).containsEntry("a", 1);
        }

        //turn released: the write proceeds
        assertThat(update.get(2, TimeUnit.SECONDS).getSpec()).containsEntry("a", 2);
    }

    @Test
    void writeFailsWhenTurnIsNotAvailableInTime() throws Exception {
        repository.create(dto("e1", Map.of("a", 1)));

        try (EntityLock turn = lockManager.acquire(TestDTO.class, "e1")) {
            Future<TestDTO> update = executor.submit(() -> repository.update("e1", dto("e1", Map.of("a", 2))));

            assertThatThrownBy(() -> update.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(StoreException.class);
        }

        assertThat(repository.get("e1").getSpec()).containsEntry("a", 1);
    }

    @Test
    void writesAreReentrantForTheTurnHolder() throws Exception {
        repository.create(dto("e1", Map.of("a", 1)));

        //the holder of the turn (ex. service or lifecycle) can write without blocking itself
        try (EntityLock turn = lockManager.acquire(TestDTO.class, "e1")) {
            repository.update("e1", dto("e1", Map.of("a", 2)));
        }

        assertThat(repository.get("e1").getSpec()).containsEntry("a", 2);
        assertThat(lockManager.size()).isZero();
    }

    /*
     * Helper to access test converters from the test
     */
    private static class TestConverters {

        it.smartcommunitylabdhub.core.test.TestConverters.DtoBuilder dtoBuilder() {
            return new it.smartcommunitylabdhub.core.test.TestConverters.DtoBuilder();
        }
    }
}
