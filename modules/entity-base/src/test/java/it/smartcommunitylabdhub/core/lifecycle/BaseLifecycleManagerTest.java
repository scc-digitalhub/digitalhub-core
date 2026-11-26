/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.commons.infrastructure.PostProcessor;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvent;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.core.repositories.BaseEntityRepositoryImpl;
import it.smartcommunitylabdhub.core.security.BaseOwnerContextManager;
import it.smartcommunitylabdhub.core.security.OwnerContextManager;
import it.smartcommunitylabdhub.core.test.TestDTO;
import it.smartcommunitylabdhub.core.test.TestEntity;
import it.smartcommunitylabdhub.core.test.TestFsm;
import it.smartcommunitylabdhub.core.test.TestJpaRepository;
import it.smartcommunitylabdhub.core.test.TestPersistenceConfig;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager.EntityLock;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import java.io.Serializable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(
    classes = TestPersistenceConfig.class,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:entitybase-lifecycle;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    }
)
class BaseLifecycleManagerTest {

    @Autowired
    private BaseEntityRepositoryImpl<TestEntity, TestDTO> repository;

    @Autowired
    private TestJpaRepository jpaRepository;

    @Autowired
    private LocalEntityLockManager lockManager;

    @Autowired
    private CacheManager cacheManager;

    private TestFsm fsm;
    private BaseLifecycleManager<TestDTO> manager;
    private final List<Object> published = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        jpaRepository.deleteAllInBatch();
        cacheManager.getCacheNames().forEach(n -> cacheManager.getCache(n).clear());
        published.clear();

        fsm = new TestFsm();
        manager = new BaseLifecycleManager<TestDTO>(TestDTO.class) {};
        manager.setEntityRepository(repository);
        manager.setEventPublisher(published::add);
        manager.setLockManager(lockManager);
        manager.setFsmFactory(fsm.factory());
        manager.afterPropertiesSet();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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
        return (String) repository.get(id).getStatus().get("state");
    }

    private List<String> lifecycleStates() {
        return published
            .stream()
            .filter(LifecycleEvent.class::isInstance)
            .map(e -> ((LifecycleEvent<?>) e).getState())
            .toList();
    }

    /*
     * Perform / handle
     */

    @Test
    void handleWalksTheShortestPathOneStepAtATime() throws Exception {
        TestDTO dto = create("e1", "CREATED");
        List<String> effects = new ArrayList<>();

        TestDTO res = manager.handle(dto, "RUNNING", null, (d, r) ->
            effects.add((String) d.getStatus().get("state"))
        );

        assertThat(fsm.calls()).containsExactly("CREATED>BUILT", "BUILT>READY", "READY>RUNNING");
        assertThat(res.getStatus()).containsEntry("state", "RUNNING");
        assertThat(storedState("e1")).isEqualTo("RUNNING");
        //one caller effect per persisted step, in order, one event for the whole operation
        assertThat(effects).containsExactly("BUILT", "READY", "RUNNING");
        assertThat(lifecycleStates()).containsExactly("RUNNING");
    }

    @Test
    void transitionChangesOnTheContextArePersisted() throws Exception {
        TestDTO dto = create("e1", "CREATED");
        fsm.on("BUILT", (cur, next, ev, ctx, in) -> {
            //replace spec entirely, change status in place
            ctx.setSpec(new HashMap<>(Map.of("image", "built:1")));
            ctx.getStatus().put("build", "done");
            return Optional.empty();
        });
        fsm.on("READY", (cur, next, ev, ctx, in) -> {
            //next step sees the previous step changes and adds its own
            ctx.getSpec().put("ready", ctx.getSpec().get("image"));
            return Optional.empty();
        });

        manager.handle(dto, "READY");

        TestDTO stored = repository.get("e1");
        assertThat(stored.getSpec()).containsEntry("image", "built:1").containsEntry("ready", "built:1");
        assertThat(stored.getSpec()).doesNotContainKey("a");
        assertThat(stored.getStatus()).containsEntry("build", "done").containsEntry("state", "READY");
    }

    @Test
    void onlyMetadataSpecAndStatusAreTakenFromTheContext() throws Exception {
        TestDTO dto = create("e1", "CREATED");
        fsm.on("BUILT", (cur, next, ev, ctx, in) -> {
            //fields other than metadata/spec/status are effectively final
            ctx.setName("changed");
            ctx.setUser("mallory");
            ctx.setProject("other");
            ctx.getSpec().put("built", true);
            return Optional.empty();
        });

        //processors see the merged result of the transition
        List<String> seen = new CopyOnWriteArrayList<>();
        manager.setSpecProcessorRegistry(stage ->
            List.of(
                new Processor<TestDTO, Spec>() {
                    @Override
                    public <I extends Serializable> Spec process(String stage, TestDTO d, I input) {
                        seen.add(d.getName() + "/" + d.getUser() + "/" + d.getProject() + "/" + d.getSpec().get("built"));
                        return null;
                    }
                }
            )
        );

        manager.perform(dto, "BUILD");

        assertThat(seen).containsExactly("n-e1/alice/prj/true");
        assertThat(repository.get("e1").getSpec()).containsEntry("built", true);
    }

    @Test
    void failedTransitionKeepsOnlyPartialMetadataSpecAndStatus() throws Exception {
        TestDTO dto = create("e1", "CREATED");
        fsm.on("BUILT", (cur, next, ev, ctx, in) -> {
            ctx.setUser("mallory");
            ctx.getSpec().put("partial", true);
            throw new CoreRuntimeException("failed");
        });
        List<String> users = new CopyOnWriteArrayList<>();
        fsm.on("ERROR", (cur, next, ev, ctx, in) -> {
            users.add(ctx.getUser());
            return Optional.empty();
        });

        manager.perform(dto, "BUILD");

        assertThat(users).containsExactly("alice");
        assertThat(repository.get("e1").getSpec()).containsEntry("partial", true);
    }

    @Test
    void processorsAndErrorStepReceiveTheOperationInput() throws Exception {
        List<Object> inputs = new CopyOnWriteArrayList<>();
        //transition output differs from the input
        fsm.on("BUILT", (cur, next, ev, ctx, in) -> Optional.of("output"));
        manager.setStatusProcessorRegistry(stage ->
            List.of(
                new Processor<TestDTO, it.smartcommunitylabdhub.commons.models.status.Status>() {
                    @Override
                    public <I extends Serializable> it.smartcommunitylabdhub.commons.models.status.Status process(
                        String stage,
                        TestDTO d,
                        I input
                    ) {
                        inputs.add(stage + ":" + input);
                        return null;
                    }
                }
            )
        );

        manager.perform(create("e1", "CREATED"), "BUILD", "runnable-1");
        assertThat(inputs).containsExactly("onBuilt:runnable-1");

        //ERROR step gets the input too (ex. runtime cleanup of the runnable)
        fsm.on("READY", (cur, next, ev, ctx, in) -> {
            throw new CoreRuntimeException("fail");
        });
        fsm.on("ERROR", (cur, next, ev, ctx, in) -> {
            inputs.add("error-logic:" + in);
            return Optional.empty();
        });
        inputs.clear();
        manager.perform(repository.get("e1"), "RUN", "runnable-2");
        assertThat(inputs).containsExactly("error-logic:runnable-2", "onError:runnable-2");
    }

    @Test
    void eventIsPublishedOnceAfterCallerEffects() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        manager.setEventPublisher(e -> {
            if (e instanceof LifecycleEvent<?> le) {
                order.add("event:" + le.getState() + ":" + le.getEvent());
            }
        });

        manager.perform(create("e1", "CREATED"), "BUILD", null, (d, r) -> order.add("effect"));
        assertThat(order).containsExactly("effect", "event:BUILT:BUILD");

        order.clear();
        manager.handle(repository.get("e1"), "RUNNING", null, (d, r) -> order.add("effect"));
        assertThat(order).containsExactly("effect", "effect", "event:RUNNING:null");
    }

    @Test
    void processorBatchesSeePreviousBatchesResults() throws Exception {
        List<String> seen = new CopyOnWriteArrayList<>();
        manager.setMetadataProcessorRegistry(stage ->
            List.of(
                new Processor<TestDTO, it.smartcommunitylabdhub.commons.models.metadata.Metadata>() {
                    @Override
                    public <I extends Serializable> it.smartcommunitylabdhub.commons.models.metadata.Metadata process(
                        String stage,
                        TestDTO d,
                        I input
                    ) {
                        //write on the copy: never visible to others
                        d.getSpec().put("leak", true);
                        it.smartcommunitylabdhub.commons.models.metadata.Metadata m =
                            new it.smartcommunitylabdhub.commons.models.metadata.Metadata() {
                                @Override
                                public Map<String, Serializable> toMap() {
                                    return Map.of("meta", "m1");
                                }

                                @Override
                                public void configure(Map<String, Serializable> data) {}
                            };
                        return m;
                    }
                }
            )
        );
        manager.setSpecProcessorRegistry(stage ->
            List.of(
                new Processor<TestDTO, Spec>() {
                    @Override
                    public <I extends Serializable> Spec process(String stage, TestDTO d, I input) {
                        seen.add("spec sees meta=" + d.getMetadata().get("meta") + " leak=" + d.getSpec().get("leak"));
                        return spec(Map.of("specOut", "s1"));
                    }
                }
            )
        );
        manager.setStatusProcessorRegistry(stage ->
            List.of(
                new Processor<TestDTO, it.smartcommunitylabdhub.commons.models.status.Status>() {
                    @Override
                    public <I extends Serializable> it.smartcommunitylabdhub.commons.models.status.Status process(
                        String stage,
                        TestDTO d,
                        I input
                    ) {
                        seen.add("status sees meta=" + d.getMetadata().get("meta") + " spec=" + d.getSpec().get("specOut"));
                        return null;
                    }
                }
            )
        );

        manager.perform(create("e1", "CREATED"), "BUILD");

        assertThat(seen).containsExactly("spec sees meta=m1 leak=null", "status sees meta=m1 spec=s1");
    }

    @Test
    void failingEffectDoesNotStopTheOthers() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<String> ran = new CopyOnWriteArrayList<>();
        manager.setEffectRegistry(stage ->
            "onBuilt".equals(stage)
                ? List.<Effect<TestDTO>>of(
                    (st, dto, out) -> ran.add("first"),
                    (st, dto, out) -> {
                        throw new UnsupportedOperationException("unexpected");
                    },
                    (st, dto, out) -> {
                        ran.add("third");
                        done.countDown();
                    }
                )
                : List.of()
        );

        manager.perform(create("e1", "CREATED"), "BUILD");

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(ran).containsExactly("first", "third");
    }

    @Test
    void handleWithoutPathThrowsAndWritesNothing() throws Exception {
        TestDTO dto = create("e1", "COMPLETED");

        assertThatThrownBy(() -> manager.handle(dto, "RUNNING")).isInstanceOf(InvalidTransitionException.class);

        assertThat(fsm.calls()).isEmpty();
        assertThat(published).isEmpty();
        assertThat(storedState("e1")).isEqualTo("COMPLETED");
    }

    @Test
    void handleSameStateRunsExplicitSelfLoop() throws Exception {
        TestDTO dto = create("e1", "RUNNING");

        manager.handle(dto, "RUNNING");

        assertThat(fsm.calls()).containsExactly("RUNNING>RUNNING");
        assertThat(lifecycleStates()).containsExactly("RUNNING");

        //no explicit cycle: invalid
        TestDTO created = create("e2", "CREATED");
        assertThatThrownBy(() -> manager.handle(created, "CREATED")).isInstanceOf(InvalidTransitionException.class);
    }

    @Test
    void performExecutesSingleEventStep() throws Exception {
        TestDTO dto = create("e1", "CREATED");

        manager.perform(dto, "BUILD");
        assertThat(storedState("e1")).isEqualTo("BUILT");

        //event not available on the current state: nothing written
        assertThatThrownBy(() -> manager.perform(dto, "BUILD")).isInstanceOf(InvalidTransitionException.class);
        assertThat(storedState("e1")).isEqualTo("BUILT");
        assertThat(fsm.calls()).containsExactly("CREATED>BUILT");
    }

    @Test
    void operationStartsFromStoredObjectAndState() throws Exception {
        TestDTO stale = create("e1", "CREATED");
        //someone moved the object meanwhile
        manager.perform(stale, "BUILD");

        //a stale dto (still CREATED) is evaluated against the actual state
        assertThatThrownBy(() -> manager.perform(stale, "BUILD")).isInstanceOf(InvalidTransitionException.class);
        manager.perform(stale, "RUN");
        assertThat(storedState("e1")).isEqualTo("READY");
    }

    @Test
    void nestedCallerHoldingTheTurnIsTrusted() throws Exception {
        TestDTO dto = create("e1", "CREATED");
        dto.getSpec().put("a", 42);

        try (EntityLock turn = lockManager.acquire(TestDTO.class, "e1")) {
            manager.perform(dto, "BUILD");
        }

        assertThat(repository.get("e1").getSpec()).containsEntry("a", 42);

        //non nested: the stored object wins over the provided dto
        TestDTO other = repository.get("e1");
        other.getSpec().put("a", 7);
        manager.perform(other, "RUN");
        assertThat(repository.get("e1").getSpec()).containsEntry("a", 42);
    }

    @Test
    void deletedStateRemovesTheObject() throws Exception {
        TestDTO dto = create("e1", "CREATED");

        TestDTO res = manager.perform(dto, "DELETE");

        assertThat(res.getStatus()).containsEntry("state", "DELETED");
        assertThat(repository.find("e1")).isNull();
    }

    /*
     * Failures
     */

    @Test
    void logicFailureMovesToErrorKeepingPartialChanges() throws Exception {
        TestDTO dto = create("e1", "CREATED");
        fsm.on("BUILT", (cur, next, ev, ctx, in) -> {
            //external action done, then failure
            ctx.getSpec().put("built", true);
            throw new CoreRuntimeException("build failed");
        });
        List<String> effects = new ArrayList<>();

        TestDTO res = manager.perform(dto, "BUILD", null, (d, r) -> effects.add((String) d.getStatus().get("state")));

        assertThat(fsm.calls()).containsExactly("CREATED>BUILT", "CREATED>ERROR");
        assertThat(res.getStatus()).containsEntry("state", "ERROR").containsEntry("message", "build failed");
        TestDTO stored = repository.get("e1");
        assertThat(stored.getStatus()).containsEntry("state", "ERROR");
        //partial changes are kept: they describe what was done
        assertThat(stored.getSpec()).containsEntry("built", true);
        //caller effect still sees the persisted ERROR step
        assertThat(effects).containsExactly("ERROR");
    }

    @Test
    void walkStopsAtFailedStep() throws Exception {
        TestDTO dto = create("e1", "CREATED");
        fsm.on("READY", (cur, next, ev, ctx, in) -> {
            throw new IllegalStateException("not ready");
        });

        TestDTO res = manager.handle(dto, "RUNNING");

        assertThat(fsm.calls()).containsExactly("CREATED>BUILT", "BUILT>READY", "BUILT>ERROR");
        assertThat(res.getStatus()).containsEntry("state", "ERROR");
        assertThat(lifecycleStates()).containsExactly("ERROR");
    }

    @Test
    void logicTimeoutMovesToErrorFromPreStepObject() throws Exception {
        manager.setFsmTimeout(Duration.ofMillis(300));
        TestDTO dto = create("e1", "CREATED");
        fsm.on("BUILT", (cur, next, ev, ctx, in) -> {
            ctx.getSpec().put("late", true);
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return Optional.empty();
        });

        TestDTO res = manager.perform(dto, "BUILD");

        assertThat(res.getStatus()).containsEntry("state", "ERROR").containsEntry("message", "transition timeout");
        //changes of the timed out logic are discarded
        assertThat(repository.get("e1").getSpec()).doesNotContainKey("late");
    }

    @Test
    void rejectedLogicThrowsToCallerWithoutWriting() throws Exception {
        manager.setFsmExecutor(r -> {
            throw new RejectedExecutionException("full");
        });
        TestDTO dto = create("e1", "CREATED");

        assertThatThrownBy(() -> manager.perform(dto, "BUILD")).isInstanceOf(SystemException.class);

        assertThat(fsm.calls()).isEmpty();
        assertThat(published).isEmpty();
        assertThat(storedState("e1")).isEqualTo("CREATED");
    }

    /*
     * Processors and post processors
     */

    private static Spec spec(Map<String, Serializable> map) {
        return new Spec() {
            @Override
            public Map<String, Serializable> toMap() {
                return map;
            }

            @Override
            public void configure(Map<String, Serializable> data) {}
        };
    }

    @Test
    void processorsMergeResultsAndExplicitFailureFailsTheStep() throws Exception {
        List<String> mode = new CopyOnWriteArrayList<>(List.of("ok"));
        manager.setSpecProcessorRegistry(stage ->
            "onBuilt".equals(stage)
                ? List.of(
                    new Processor<TestDTO, Spec>() {
                        @Override
                        public <I extends Serializable> Spec process(String stage, TestDTO dto, I input) {
                            //processors work on copies
                            dto.getSpec().put("leak", true);
                            return spec(Map.of("processed", true));
                        }
                    },
                    new Processor<TestDTO, Spec>() {
                        @Override
                        public <I extends Serializable> Spec process(String stage, TestDTO dto, I input) {
                            if ("core".equals(mode.get(0))) {
                                throw new CoreRuntimeException("explicit");
                            }
                            //any other failure is ignored
                            throw new IllegalArgumentException("ignored");
                        }
                    }
                )
                : List.of()
        );

        TestDTO e1 = create("e1", "CREATED");
        manager.perform(e1, "BUILD");
        TestDTO stored = repository.get("e1");
        assertThat(stored.getStatus()).containsEntry("state", "BUILT");
        assertThat(stored.getSpec()).containsEntry("processed", true).doesNotContainKey("leak");

        mode.set(0, "core");
        TestDTO e2 = create("e2", "CREATED");
        TestDTO res = manager.perform(e2, "BUILD");
        assertThat(res.getStatus()).containsEntry("state", "ERROR").containsEntry("message", "explicit");
    }

    private static PostProcessor<TestDTO> append(String suffix) {
        return (stage, dto, output) -> output + suffix;
    }

    private static PostProcessor<TestDTO> failing(RuntimeException e) {
        return (stage, dto, output) -> {
            throw e;
        };
    }

    @Test
    void postProcessorsChainTheOutput() throws Exception {
        fsm.on("BUILT", (cur, next, ev, ctx, in) -> Optional.of("out"));
        manager.setPostProcessorRegistry(stage ->
            List.of(append("-a"), failing(new IllegalStateException("ignored")), append("-c"))
        );
        List<Serializable> outputs = new ArrayList<>();

        TestDTO dto = create("e1", "CREATED");
        manager.perform(dto, "BUILD", null, (d, r) -> outputs.add(r));

        assertThat(outputs).containsExactly("out-a-c");
        assertThat(storedState("e1")).isEqualTo("BUILT");
    }

    @Test
    void postProcessorExplicitFailureFailsTheStep() throws Exception {
        fsm.on("BUILT", (cur, next, ev, ctx, in) -> Optional.of("out"));
        manager.setPostProcessorRegistry(stage ->
            "onBuilt".equals(stage) ? List.of(failing(new CoreRuntimeException("no credentials"))) : List.of()
        );

        TestDTO res = manager.perform(create("e1", "CREATED"), "BUILD");

        assertThat(res.getStatus()).containsEntry("state", "ERROR").containsEntry("message", "no credentials");
    }

    /*
     * Effects and identity
     */

    @Test
    void effectsAreAsyncAndSeeThePersistedStep() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        List<String> seen = new CopyOnWriteArrayList<>();
        String testThread = Thread.currentThread().getName();

        manager.setEffectRegistry(stage ->
            "onBuilt".equals(stage)
                ? List.<Effect<TestDTO>>of((st, dto, out) -> {
                    try {
                        seen.add(Thread.currentThread().getName().equals(testThread) ? "same-thread" : "async");
                        seen.add(storedState(dto.getId()));
                    } catch (Exception e) {
                        seen.add("error");
                    } finally {
                        done.countDown();
                    }
                })
                : List.of()
        );

        manager.perform(create("e1", "CREATED"), "BUILD");

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(seen).containsExactly("async", "BUILT");
    }

    @Test
    void callerEffectRunsAfterTheTurnIsReleased() throws Exception {
        List<Boolean> held = new ArrayList<>();

        manager.handle(create("e1", "CREATED"), "READY", null, (d, r) ->
            held.add(lockManager.isHeldByCurrentThread(TestDTO.class, d.getId()))
        );

        assertThat(held).containsExactly(false, false);
    }

    @Test
    void operationsRunAsOwnerWithRequesterAsActor() throws Exception {
        TestDTO dto = create("e1", "CREATED");
        manager.setOwnerContextManager(
            new BaseOwnerContextManager() {
                @Override
                protected Authentication authenticate(String owner) {
                    return new TestingAuthenticationToken(owner, null, "ROLE_USER");
                }
            }
        );

        List<String> identities = new CopyOnWriteArrayList<>();
        fsm.on("BUILT", (cur, next, ev, ctx, in) -> {
            identities.add(SecurityContextHolder.getContext().getAuthentication().getName());
            identities.add(OwnerContextManager.currentActor());
            return Optional.empty();
        });

        //bob acts on alice's object
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", null));
        manager.perform(dto, "BUILD", null, (d, r) ->
            identities.add("effect:" + SecurityContextHolder.getContext().getAuthentication().getName())
        );

        assertThat(identities).containsExactly("alice", "bob", "effect:alice");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("bob");
        //audit records the real user
        assertThat(jpaRepository.findById("e1").orElseThrow().getUpdatedBy()).isEqualTo("bob");
    }
}
