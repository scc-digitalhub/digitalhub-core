/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.core.repositories.BaseEntityRepositoryImpl;
import it.smartcommunitylabdhub.core.test.TestDTO;
import it.smartcommunitylabdhub.core.test.TestEntity;
import it.smartcommunitylabdhub.core.test.TestFsm;
import it.smartcommunitylabdhub.core.test.TestJpaRepository;
import it.smartcommunitylabdhub.core.test.TestPersistenceConfig;
import it.smartcommunitylabdhub.lifecycle.detached.DetachedEntry;
import it.smartcommunitylabdhub.lifecycle.detached.DetachedOperations;
import it.smartcommunitylabdhub.lifecycle.detached.InMemoryDetachedRegistry;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import java.io.Serializable;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Detached transitions: the step is written, then effects and the caller effect run detached
 */
@SpringBootTest(
    classes = TestPersistenceConfig.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:entitybase-detached;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    }
)
class BaseLifecycleManagerDetachedTest {

    private static final String TYPE = TestDTO.class.getName();
    private static final String DELETING = "DELETING";

    @Autowired
    private BaseEntityRepositoryImpl<TestEntity, TestDTO> repository;

    @Autowired
    private TestJpaRepository jpaRepository;

    @Autowired
    private LocalEntityLockManager lockManager;

    @Autowired
    private CacheManager cacheManager;

    private InMemoryDetachedRegistry registry;
    private DetachedOperations operations;
    private TestFsm fsm;
    private BaseLifecycleManager<TestDTO> manager;

    //effects by stage
    private final Map<String, List<Effect<TestDTO>>> effects = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        jpaRepository.deleteAllInBatch();
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
        effects.clear();

        registry = new InMemoryDetachedRegistry();
        operations = new DetachedOperations(registry);
        operations.setBaseDelay(Duration.ofMillis(20));
        operations.setMaxDelay(Duration.ofMillis(100));

        fsm = new TestFsm();
        manager = new BaseLifecycleManager<TestDTO>(TestDTO.class) {};
        manager.setEntityRepository(repository);
        manager.setEventPublisher(e -> {});
        manager.setLockManager(lockManager);
        manager.setFsmFactory(fsm.factory());
        manager.setDetachedOperations(operations);
        manager.setEffectRegistry(stage -> effects.getOrDefault(stage, List.of()));

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
    }

    @AfterEach
    void tearDown() {
        operations.close();
        SecurityContextHolder.clearContext();
    }

    private void onDeleting(Effect<TestDTO> effect) {
        effects.computeIfAbsent("onDeleting", k -> new CopyOnWriteArrayList<>()).add(effect);
    }

    private TestDTO create(String id, String state) throws Exception {
        TestDTO dto = TestDTO.builder()
            .id(id)
            .name("n-" + id)
            .kind("test")
            .project("prj")
            .spec(new HashMap<>(Map.of("a", 1)))
            .status(new HashMap<>(Map.of("state", state)))
            .build();
        return repository.create(dto);
    }

    private String storedState(String id) throws Exception {
        TestDTO dto = repository.find(id);
        return dto != null ? (String) dto.getStatus().get("state") : null;
    }

    private void awaitGone(String id) {
        await().atMost(5, TimeUnit.SECONDS).until(() -> repository.find(id) == null);
    }

    private void awaitState(String id, String state) {
        await().atMost(5, TimeUnit.SECONDS).until(() -> state.equals(storedState(id)));
    }

    private DetachedEntry entry(String id) {
        return registry.list().stream().filter(e -> id.equals(e.id())).findFirst().orElse(null);
    }

    private void awaitNoEntries() {
        await().atMost(5, TimeUnit.SECONDS).until(() -> registry.list().isEmpty());
    }

    private BiConsumer<TestDTO, Serializable> toDeleted() {
        return (d, out) -> manager.handle(d, "DELETED");
    }

    @Test
    void stepIsWrittenThenEffectsAndCallerEffectRunDetached() throws Exception {
        operations.setBaseDelay(Duration.ofMillis(300));
        List<String> order = new CopyOnWriteArrayList<>();
        onDeleting((stage, dto, out) -> order.add("effect:" + Thread.currentThread().getName()));

        BiConsumer<TestDTO, Serializable> caller = (d, out) -> {
            order.add("caller:" + Thread.currentThread().getName());
            manager.handle(d, "DELETED");
        };
        TestDTO res = manager.perform(create("e1", "CREATED"), "REMOVE", null, caller);

        //the step is written synchronously, the rest is detached and delayed
        assertThat(res.getStatus()).containsEntry("state", DELETING);
        assertThat(storedState("e1")).isEqualTo(DELETING);
        assertThat(order).isEmpty();
        assertThat(entry("e1")).isNotNull();

        //effects first, then the caller effect, on the detached execution
        awaitGone("e1");
        assertThat(order).hasSize(2);
        assertThat(order.get(0)).startsWith("effect:detached-");
        assertThat(order.get(1)).startsWith("caller:detached-");
        assertThat(fsm.calls()).containsExactly("CREATED>DELETING", "DELETING>DELETED");
        awaitNoEntries();
    }

    @Test
    void callerEffectRunsWithinTheTurn() throws Exception {
        AtomicBoolean held = new AtomicBoolean(false);
        BiConsumer<TestDTO, Serializable> caller = (d, out) -> {
            held.set(lockManager.isHeldByCurrentThread(TestDTO.class, d.getId()));
            manager.handle(d, "DELETED");
        };

        manager.perform(create("e1", "CREATED"), "REMOVE", null, caller);

        awaitGone("e1");
        assertThat(held.get()).isTrue();
    }

    @Test
    void performFromTheDetachedExecutionIsScheduledAgain() throws Exception {
        AtomicInteger passes = new AtomicInteger();
        List<DetachedEntry> entries = new CopyOnWriteArrayList<>();
        onDeleting((stage, dto, out) -> {
            passes.incrementAndGet();
            entries.add(entry(dto.getId()));
        });

        AtomicReference<BiConsumer<TestDTO, Serializable>> caller = new AtomicReference<>();
        caller.set((d, out) -> {
            if (passes.get() < 3) {
                //written now, its detached part scheduled again (delayed)
                manager.perform(d, "REMOVE", null, caller.get());
            } else {
                manager.handle(d, "DELETED");
            }
        });

        manager.perform(create("e1", "CREATED"), "REMOVE", null, caller.get());

        awaitGone("e1");
        assertThat(passes.get()).isEqualTo(3);
        assertThat(fsm.calls()).containsExactly(
            "CREATED>DELETING",
            "DELETING>DELETING",
            "DELETING>DELETING",
            "DELETING>DELETED"
        );

        //one entry for the whole operation: start time kept, attempts counted
        assertThat(entries).extracting(DetachedEntry::attempts).containsExactly(1, 2, 3);
        assertThat(entries).extracting(DetachedEntry::startedAt).containsOnly(entries.get(0).startedAt());
        awaitNoEntries();
    }

    @Test
    void coreRuntimeExceptionInEffectsMovesToErrorWithoutCallerEffect() throws Exception {
        AtomicInteger callers = new AtomicInteger();
        AtomicBoolean secondRan = new AtomicBoolean(false);
        onDeleting((stage, dto, out) -> {
            throw new CoreRuntimeException("cleanup failed");
        });
        onDeleting((stage, dto, out) -> secondRan.set(true));

        BiConsumer<TestDTO, Serializable> caller = (d, out) -> {
            callers.incrementAndGet();
            manager.handle(d, "DELETED");
        };
        manager.perform(create("e1", "CREATED"), "REMOVE", null, caller);

        awaitState("e1", "ERROR");
        assertThat(repository.get("e1").getStatus()).containsEntry("message", "cleanup failed");
        //the remaining effects of the pass are skipped, the caller effect never runs
        assertThat(secondRan.get()).isFalse();
        assertThat(callers.get()).isZero();
        awaitNoEntries();
    }

    @Test
    void otherEffectFailuresAreSkipped() throws Exception {
        AtomicBoolean secondRan = new AtomicBoolean(false);
        onDeleting((stage, dto, out) -> {
            throw new IllegalStateException("boom");
        });
        onDeleting((stage, dto, out) -> secondRan.set(true));

        manager.perform(create("e1", "CREATED"), "REMOVE", null, toDeleted());

        awaitGone("e1");
        assertThat(secondRan.get()).isTrue();
    }

    @Test
    void coreRuntimeExceptionInCallerEffectMovesToError() throws Exception {
        BiConsumer<TestDTO, Serializable> caller = (d, out) -> {
            throw new CoreRuntimeException("children left");
        };

        manager.perform(create("e1", "CREATED"), "REMOVE", null, caller);

        awaitState("e1", "ERROR");
        assertThat(repository.get("e1").getStatus()).containsEntry("message", "children left");
        awaitNoEntries();
    }

    @Test
    void callerEffectIsSkippedWhenTheObjectMovedMeanwhile() throws Exception {
        AtomicInteger callers = new AtomicInteger();
        onDeleting((stage, dto, out) ->
            //meanwhile someone else fails the object (ex. the cleaner)
            CompletableFuture.runAsync(() -> {
                try {
                    manager.handle(repository.get("e1"), "ERROR");
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).join()
        );

        BiConsumer<TestDTO, Serializable> caller = (d, out) -> {
            callers.incrementAndGet();
            //would restart the delete from ERROR: must never run
            manager.perform(d, "REMOVE");
        };
        manager.perform(create("e1", "CREATED"), "REMOVE", null, caller);

        awaitNoEntries();
        assertThat(callers.get()).isZero();
        assertThat(storedState("e1")).isEqualTo("ERROR");
        assertThat(fsm.calls()).containsExactly("CREATED>DELETING", "DELETING>ERROR");
    }

    @Test
    void expiredOperationIsInterruptedAndMovedToError() throws Exception {
        operations.setTimeout(Duration.ofMillis(300));
        operations.setCheckInterval(Duration.ofMillis(100));

        AtomicBoolean interrupted = new AtomicBoolean(false);
        onDeleting((stage, dto, out) -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
        });

        manager.perform(create("e1", "CREATED"), "REMOVE", null, toDeleted());

        awaitState("e1", "ERROR");
        assertThat((String) repository.get("e1").getStatus().get("message")).contains("timed out");
        await().atMost(5, TimeUnit.SECONDS).untilTrue(interrupted);
        awaitNoEntries();
        //the interrupted execution never applies its caller effect
        assertThat(storedState("e1")).isEqualTo("ERROR");
    }

    @Test
    void nestedDetachedRunsInlineAndIsNeverRegistered() throws Exception {
        create("parent", "CREATED");
        create("child", "CREATED");

        AtomicReference<String> parentThread = new AtomicReference<>();
        AtomicReference<String> childThread = new AtomicReference<>();
        AtomicBoolean childRegistered = new AtomicBoolean(false);
        AtomicBoolean childGoneOnReturn = new AtomicBoolean(false);

        onDeleting((stage, dto, out) -> {
            if ("parent".equals(dto.getId())) {
                parentThread.set(Thread.currentThread().getName());
                try {
                    manager.perform(repository.get("child"), "REMOVE", null, toDeleted());
                    //the child detached part ran inline: done when perform returns
                    childGoneOnReturn.set(repository.find("child") == null);
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            } else {
                childThread.set(Thread.currentThread().getName());
                childRegistered.set(
                    operations.isLive(TYPE, "child", DELETING) || entry("child") != null
                );
            }
        });

        manager.perform(repository.get("parent"), "REMOVE", null, toDeleted());

        awaitGone("parent");
        assertThat(childThread.get()).isEqualTo(parentThread.get());
        assertThat(childRegistered.get()).isFalse();
        assertThat(childGoneOnReturn.get()).isTrue();
        awaitNoEntries();
    }

    @Test
    void nestedPerformFromItsOwnCallerEffectIsScheduledNotRecursive() throws Exception {
        create("parent", "CREATED");
        create("child", "CREATED");

        AtomicInteger childPasses = new AtomicInteger();
        List<String> childThreads = new CopyOnWriteArrayList<>();
        AtomicReference<BiConsumer<TestDTO, Serializable>> again = new AtomicReference<>();
        again.set((d, o) -> {
            if (childPasses.get() < 2) {
                manager.perform(d, "REMOVE", null, again.get());
            } else {
                manager.handle(d, "DELETED");
            }
        });

        onDeleting((stage, dto, out) -> {
            if ("parent".equals(dto.getId())) {
                try {
                    manager.perform(repository.get("child"), "REMOVE", null, again.get());
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            } else {
                childPasses.incrementAndGet();
                childThreads.add(Thread.currentThread().getName());
            }
        });

        manager.perform(repository.get("parent"), "REMOVE", null, toDeleted());

        awaitGone("parent");
        awaitGone("child");
        assertThat(childPasses.get()).isEqualTo(2);
        //first pass inline in the parent, the second one scheduled on its own execution
        assertThat(childThreads.get(0)).isNotEqualTo(childThreads.get(1));
        awaitNoEntries();
    }
}
