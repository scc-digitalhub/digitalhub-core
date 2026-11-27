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

package it.smartcommunitylabdhub.core.lifecycle;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.commons.infrastructure.EffectRegistry;
import it.smartcommunitylabdhub.commons.infrastructure.PostProcessor;
import it.smartcommunitylabdhub.commons.infrastructure.PostProcessorRegistry;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.infrastructure.ProcessorRegistry;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.lifecycle.LifecycleEvent;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.Metadata;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.events.EntityOperationsPublisher;
import it.smartcommunitylabdhub.core.security.OwnerContextManager;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.events.EntityOperation;
import it.smartcommunitylabdhub.fsm.Fsm;
import it.smartcommunitylabdhub.fsm.Transition;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import it.smartcommunitylabdhub.lifecycle.AbstractLifecycleManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.lifecycle.detached.DetachedOperations;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager.EntityLock;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.Assert;
import org.springframework.util.SerializationUtils;
import org.springframework.util.StringUtils;

/**
 * Lifecycle manager: drives objects through their FSM.
 *
 * - {@code perform(event)}: execute the transition matching the event on the current state (1 step)
 * - {@code handle(state)}: reconcile to an observed state, following the shortest path from the
 *   current state (N steps). The path is planned BEFORE starting: no path means nothing is done.
 *
 * Every operation runs on behalf of the object OWNER, inside the per-object turn, starting from the
 * STORED object (or from the dto provided by a caller already holding the turn).
 *
 * Each step:
 * 1. FSM transition logic, on its executor, on a copy of the dto, time bound
 * 2. processors (parallel, time bound each), results merged into metadata/spec/status
 * 3. post processors (chained on the transition output, time bound each and as a chain)
 * 4. write (the step is persisted, DELETED removes the object)
 * 5. effects dispatched asynchronously (fire and forget)
 *
 * DETACHED transitions (declared by the FSM) replace step 5: after the write and the release of the
 * turn, the effects run in order on a detached execution (registered, delayed, without time bounds),
 * then the caller effect runs within the turn, only if the object is still in the detached state:
 * its calls on the manager re-enter the turn. A detached step started by the detached execution of
 * the same object is scheduled again (delayed, with backoff); one reached while running the detached
 * execution of another object runs inline. Timeout moves the object to ERROR (restart is handled by
 * the application recovery, never re-run).
 *
 * After the turn is fully released, the caller effect runs once per written (attached) step, in
 * order, then a single LifecycleEvent is published for the whole operation (events publish
 * outcomes, they are never used to perform work).
 *
 * Processors run in batches (metadata, spec, status): every processor gets its own copy of the dto,
 * and each batch sees the results merged by the previous ones.
 *
 * Failures:
 * - invalid transitions (or no path) throw back to the caller: nothing is written
 * - rejected transition logic (executor full) throws back to the caller: nothing was executed
 * - failures during a step move the object to ERROR, keeping the partially changed dto
 *   (or the pre-step dto on timeout), since external actions can not be rolled back
 */
@Slf4j
public abstract class BaseLifecycleManager<D extends BaseDTO & SpecDTO & StatusDTO & MetadataDTO>
    extends AbstractLifecycleManager<D>
    implements LifecycleManager<D>
{

    public static final String STATE_ERROR = "ERROR";
    public static final String STATE_DELETED = "DELETED";

    public static final long FSM_TIMEOUT = 10L; // seconds
    public static final long PROCESSOR_TIMEOUT = 10L; // seconds
    public static final long POST_PROCESSOR_TIMEOUT = 10L; // seconds
    public static final long POST_PROCESSOR_CHAIN_TIMEOUT = 30L; // seconds

    //shared fallback executor for unwired managers: daemon threads, never one pool per manager
    private static final ExecutorService DEFAULT_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r);
        t.setName("lifecycle-default-" + t.getId());
        t.setDaemon(true);
        return t;
    });

    protected final Class<D> typeClass;

    protected ProcessorRegistry<D, Metadata> metadataProcessorRegistry;
    protected ProcessorRegistry<D, Spec> specProcessorRegistry;
    protected ProcessorRegistry<D, Status> statusProcessorRegistry;

    protected PostProcessorRegistry<D> postProcessorRegistry;
    protected EffectRegistry<D> effectRegistry;

    protected Fsm.Factory<String, String, D> fsmFactory;
    protected EntityOperationsPublisher operationsPublisher;

    private long fsmTimeoutMillis = TimeUnit.SECONDS.toMillis(FSM_TIMEOUT);

    private Executor fsmExecutor = DEFAULT_EXECUTOR;
    private Executor processorExecutor = DEFAULT_EXECUTOR;
    private Executor effectExecutor = DEFAULT_EXECUTOR;

    protected OwnerContextManager ownerContextManager;

    //fallback resolved lazily: wired managers never create the shared instance
    private DetachedOperations detachedOperations;

    @SuppressWarnings("unchecked")
    protected BaseLifecycleManager() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.typeClass = (Class<D>) t;
    }

    protected BaseLifecycleManager(Class<D> typeClass) {
        this.typeClass = typeClass;
    }

    @Autowired(required = false)
    public void setFsmFactory(Fsm.Factory<String, String, D> fsmFactory) {
        this.fsmFactory = fsmFactory;
    }

    @Autowired(required = false)
    public void setOperationsPublisher(EntityOperationsPublisher operationsPublisher) {
        this.operationsPublisher = operationsPublisher;
    }

    @Autowired(required = false)
    public void setMetadataProcessorRegistry(ProcessorRegistry<D, Metadata> metadataProcessorRegistry) {
        this.metadataProcessorRegistry = metadataProcessorRegistry;
    }

    @Autowired(required = false)
    public void setSpecProcessorRegistry(ProcessorRegistry<D, Spec> specProcessorRegistry) {
        this.specProcessorRegistry = specProcessorRegistry;
    }

    @Autowired(required = false)
    public void setStatusProcessorRegistry(ProcessorRegistry<D, Status> statusProcessorRegistry) {
        this.statusProcessorRegistry = statusProcessorRegistry;
    }

    @Autowired(required = false)
    public void setPostProcessorRegistry(PostProcessorRegistry<D> postProcessorRegistry) {
        this.postProcessorRegistry = postProcessorRegistry;
    }

    @Autowired(required = false)
    public void setEffectRegistry(EffectRegistry<D> effectRegistry) {
        this.effectRegistry = effectRegistry;
    }

    /**
     * Time bound for the FSM transition logic of a single step
     */
    public void setFsmTimeout(@NotNull Duration timeout) {
        Assert.notNull(timeout, "timeout can not be null");
        Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "timeout must be positive");
        this.fsmTimeoutMillis = timeout.toMillis();
    }

    @Autowired(required = false)
    public void setFsmExecutor(@Qualifier("fsmExecutor") Executor fsmExecutor) {
        if (fsmExecutor != null) {
            this.fsmExecutor = fsmExecutor;
        }
    }

    @Autowired(required = false)
    public void setProcessorExecutor(@Qualifier("processorExecutor") Executor processorExecutor) {
        if (processorExecutor != null) {
            this.processorExecutor = processorExecutor;
        }
    }

    @Autowired(required = false)
    public void setEffectExecutor(@Qualifier("effectExecutor") Executor effectExecutor) {
        if (effectExecutor != null) {
            this.effectExecutor = effectExecutor;
        }
    }

    @Autowired(required = false)
    public void setOwnerContextManager(OwnerContextManager ownerContextManager) {
        this.ownerContextManager = ownerContextManager;
    }

    @Autowired(required = false)
    public void setDetachedOperations(DetachedOperations detachedOperations) {
        if (detachedOperations != null) {
            this.detachedOperations = detachedOperations;
        }
    }

    /**
     * Operations on existing objects are performed on behalf of their owner
     */
    protected OwnerContextManager.Scope asOwner(D dto) {
        return ownerContextManager != null && dto != null
            ? ownerContextManager.asOwner(dto.getUser())
            : OwnerContextManager.Scope.noop();
    }

    @Override
    protected Class<?> getLockType(D dto) {
        //use the declared type, matching services and repositories
        return typeClass;
    }

    protected Fsm<String, String, D> fsm(D dto) {
        if (fsmFactory == null) {
            throw new IllegalStateException("FSM factory not set: provide or override");
        }

        //initial state is current state
        String state = state(dto);
        if (state == null) {
            throw new IllegalStateException("State not set");
        }

        // create state machine via factory, context is the dto itself
        return fsmFactory.create(state, dto);
    }

    /*
     * Perform: execute the transition matching the event (single step)
     */
    public D perform(@NotNull D dto, @NotNull String event) {
        return perform(dto, event, null, null);
    }

    public <I extends Serializable> D perform(@NotNull D dto, @NotNull String event, @Nullable I input) {
        return perform(dto, event, input, null);
    }

    public <I extends Serializable, R extends Serializable> D perform(
        @NotNull D dto,
        @NotNull String event,
        @Nullable I input,
        @Nullable BiConsumer<D, R> effect
    ) {
        Assert.notNull(dto, "dto can not be null");
        Assert.hasText(event, "event can not be null or empty");
        log.debug("perform {} for {} with id {}", event, typeClass.getSimpleName().toLowerCase(), dto.getId());

        return operate(dto, effect, current ->
            List.of(new Step<Fsm<String, String, D>, R>(event, input, fsm -> fsm.<I, R>perform(event, input)))
        );
    }

    /*
     * Handle: reconcile to the given state, following the shortest path (N steps)
     */
    public D handle(@NotNull D dto, String nextState) {
        return handle(dto, nextState, null, null);
    }

    public <I extends Serializable> D handle(@NotNull D dto, String nextState, @Nullable I input) {
        return handle(dto, nextState, input, null);
    }

    public <I extends Serializable, R extends Serializable> D handle(
        @NotNull D dto,
        String nextState,
        @Nullable I input,
        @Nullable BiConsumer<D, R> effect
    ) {
        Assert.notNull(dto, "dto can not be null");
        Assert.hasText(nextState, "state can not be null or empty");
        log.debug("handle {} for {} with id {}", nextState, typeClass.getSimpleName().toLowerCase(), dto.getId());

        return operate(dto, effect, current -> {
            //plan BEFORE starting: no path means nothing is done
            String from = state(current);
            List<String> path = fsm(current)
                .pathTo(nextState)
                .orElseThrow(() -> new InvalidTransitionException(from, nextState));

            log.debug("planned path {} -> {} for {}: {}", from, nextState, current.getId(), path);
            return path
                .stream()
                .map(state ->
                    new Step<Fsm<String, String, D>, R>(null, input, fsm -> fsm.<I, R>goToState(state, input))
                )
                .toList();
        });
    }

    /*
     * Operation: owner scope + turn + steps
     */
    private <R extends Serializable> D operate(
        @NotNull D dto,
        @Nullable BiConsumer<D, R> effect,
        @NotNull Function<D, List<Step<Fsm<String, String, D>, R>>> planner
    ) {
        String id = dto.getId();
        Assert.hasText(id, "id can not be null or empty");

        //a caller already holding the turn has read the object within it: trust the dto
        boolean nested = lockManager.isHeldByCurrentThread(typeClass, id);

        //owner from the STORED object (immutable), never from the caller dto
        D owned = nested ? dto : read(id);

        //act on behalf of the owner, for the whole operation including the caller effect
        try (OwnerContextManager.Scope scope = asOwner(owned)) {
            try (EntityLock turn = lockManager.acquire(typeClass, id)) {
                //read the current version within the turn
                D current = nested ? dto : read(id);

                List<Step<Fsm<String, String, D>, R>> steps = planner.apply(current);
                D res = current;
                for (Step<Fsm<String, String, D>, R> step : steps) {
                    StepResult<D, R> result = execute(current, step.event(), step.input(), step.logic());
                    res = result.dto();

                    Transition<String, String, D> transition = result.transition();
                    boolean detached = transition != null && transition.isDetached();

                    if (detached) {
                        //declared detached by the fsm: effects, then the caller effect, run detached
                        D written = copy(result.dto());
                        R output = result.output();
                        String event = step.event();
                        lockManager.runAfterRelease(typeClass, id, () ->
                            detach(written, state(written), output, event, effect)
                        );
                    } else if (effect != null) {
                        //caller effect runs after the turn is fully released, once per written step
                        D written = copy(result.dto());
                        R output = result.output();
                        lockManager.runAfterRelease(typeClass, id, () -> effect.accept(written, output));
                    }

                    String state = state(res);
                    if (result.failed() || STATE_ERROR.equals(state) || STATE_DELETED.equals(state)) {
                        //terminal for this operation
                        break;
                    }

                    current = res;
                }

                if (!steps.isEmpty()) {
                    //one event per operation, published AFTER the caller effects:
                    //events are for publishing the outcome, intermediate steps are not published
                    D published = copy(res);
                    String event = steps.get(0).event();
                    lockManager.runAfterRelease(typeClass, id, () -> publish(published, event, state(published)));
                }

                return res;
            }
        }
    }

    private <R extends Serializable> StepResult<D, R> execute(
        @NotNull D current,
        @Nullable String event,
        @Nullable Serializable input,
        @NotNull Function<Fsm<String, String, D>, Optional<R>> logic
    ) {
        try {
            return step(current, event, input, logic);
        } catch (StepFailure f) {
            @SuppressWarnings("unchecked")
            D failed = (D) f.dto;
            log.debug("step on {} failed, move to ERROR: {}", current.getId(), f.getMessage());
            return error(failed, event, input, f.getMessage());
        }
    }

    private <R extends Serializable> StepResult<D, R> step(
        @NotNull D current,
        @Nullable String event,
        @Nullable Serializable input,
        @NotNull Function<Fsm<String, String, D>, Optional<R>> logic
    ) {
        //transition output escapes the update logic via holder
        AtomicReference<R> holder = new AtomicReference<>();
        AtomicReference<Transition<String, String, D>> executed = new AtomicReference<>();

        //write back via exec: logic is applied within the (re-entrant) turn, then persisted
        D saved = exec(new EntityOperation<>(current, EntityAction.UPDATE), dto -> {
            //1. fsm logic on a copy, time bound
            FsmOutcome<D, R> outcome = runFsm(dto, logic);
            String state = outcome.state();
            R output = outcome.output();
            executed.set(outcome.transition());

            //merge back ONLY metadata, spec and status from the fsm context:
            //every other field is effectively final
            D d = merge(dto, outcome.context());

            //enforce state from the fsm
            Map<String, Serializable> baseStatus = Map.of("state", state);
            d.setStatus(MapUtils.mergeMultipleMaps(d.getStatus(), baseStatus));

            //2. processors
            if (metadataProcessorRegistry != null) {
                Map<String, Serializable> psm = postProcess(d, state, input, metadataProcessorRegistry);
                d.setMetadata(MapUtils.mergeMultipleMaps(d.getMetadata(), psm));
            }
            if (specProcessorRegistry != null) {
                Map<String, Serializable> psm = postProcess(d, state, input, specProcessorRegistry);
                d.setSpec(MapUtils.mergeMultipleMaps(d.getSpec(), psm));
            }
            if (statusProcessorRegistry != null) {
                Map<String, Serializable> psm = postProcess(d, state, input, statusProcessorRegistry);
                d.setStatus(MapUtils.mergeMultipleMaps(d.getStatus(), psm, baseStatus));
            }

            //3. post processors on the output
            holder.set(postProcessOutput(d, state, output));

            //4. persisted by exec
            return d;
        });

        String state = state(saved);
        R output = holder.get();

        if (STATE_DELETED.equals(state)) {
            //cleanup already performed by the transition: remove the object via exec
            log.debug("entity {} is in DELETED state, removing from repository", saved.getId());
            exec(new EntityOperation<>(saved, EntityAction.DELETE), d -> d);
        }

        Transition<String, String, D> transition = executed.get();
        if (transition == null || !transition.isDetached()) {
            //5. effects, async (detached transitions run them after the turn is released)
            dispatchEffects(saved, state, output);
        }

        return new StepResult<>(saved, output, false, transition);
    }

    /**
     * Move to ERROR from the given dto (partial or pre-step), with the operation input
     */
    private <R extends Serializable> StepResult<D, R> error(
        D failed,
        @Nullable String event,
        @Nullable Serializable input,
        String message
    ) {
        if (message != null) {
            failed.setStatus(MapUtils.mergeMultipleMaps(failed.getStatus(), Map.of("message", message)));
        }

        try {
            StepResult<D, R> res = step(failed, event, input, fsm ->
                fsm.<Serializable, R>goToState(STATE_ERROR, input)
            );
            return new StepResult<>(res.dto(), res.output(), true, res.transition());
        } catch (StepFailure e) {
            //ERROR transition itself failed: propagate, nothing more is written
            log.error("unable to move {} to ERROR: {}", failed.getId(), e.getMessage());
            throw new SystemException("unable to move " + failed.getId() + " to ERROR: " + e.getMessage(), e);
        }
    }

    private <R extends Serializable> FsmOutcome<D, R> runFsm(
        @NotNull D current,
        @NotNull Function<Fsm<String, String, D>, Optional<R>> logic
    ) {
        //work on a copy: the fsm and its logic never touch the caller object
        D work = copy(current);
        Fsm<String, String, D> fsm = fsm(work);

        FutureTask<FsmOutcome<D, R>> task = new FutureTask<>(
            withContext(() -> {
                Optional<R> out = logic.apply(fsm);
                return new FsmOutcome<>(
                    fsm.getCurrentState(),
                    fsm.getContext(),
                    out.orElse(null),
                    fsm.getLastTransition()
                );
            })
        );

        try {
            fsmExecutor.execute(task);
        } catch (RejectedExecutionException e) {
            //nothing was executed: back to the caller, no ERROR
            log.warn("transition rejected for {}: executor is full", current.getId());
            throw new SystemException("unable to schedule transition for " + current.getId() + ", retry later", e);
        }

        try {
            return task.get(fsmTimeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            log.warn("transition timed out for {}", current.getId());
            //the logic may still be running: use the pre-step dto
            throw new StepFailure(copy(current), "transition timeout", e);
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new SystemException("interrupted during transition for " + current.getId(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof InvalidTransitionException ite) {
                //invalid: back to the caller, nothing written
                throw ite;
            }

            //logic completed with an error: the partial changes on the context are complete,
            //merge back only metadata, spec and status
            String message = cause != null && cause.getMessage() != null ? cause.getMessage() : "transition error";
            log.debug("transition logic failed for {}: {}", current.getId(), message);
            throw new StepFailure(merge(current, work), message, cause);
        }
    }

    private void publish(D dto, @Nullable String event, String state) {
        LifecycleEvent<D> e = LifecycleEvent.<D>builder()
            .id(dto.getId())
            .kind(dto.getKind())
            .user(dto.getUser())
            .project(dto.getProject())
            .event(event)
            .state(state)
            .dto(copy(dto))
            .build();

        log.debug("publish event {} on state {} for {}", event, state, dto.getId());
        if (log.isTraceEnabled()) {
            log.trace("event: {}", e);
        }

        this.eventPublisher.publishEvent(e);
    }

    /*
     * Processors: parallel, each time bound, results merged in registration order
     */
    protected <I extends Serializable> Map<String, Serializable> postProcess(
        @NotNull D dto,
        @NotNull String state,
        @Nullable I input,
        @NotNull ProcessorRegistry<D, ? extends Spec> processorRegistry
    ) {
        String stage = stage(state);
        List<? extends Processor<D, ? extends Spec>> processors = processorRegistry.getProcessors(stage);
        if (processors == null || processors.isEmpty()) {
            return new HashMap<>();
        }

        // scatter: submit all tasks upfront, each with its own time budget
        List<FutureTask<Map<String, Serializable>>> tasks = new ArrayList<>();
        for (Processor<D, ? extends Spec> p : processors) {
            FutureTask<Map<String, Serializable>> task = new FutureTask<>(
                withContext(() -> {
                    //safeguard dto from side effects by deep cloning it for each processor
                    Spec s = p.process(stage, copy(dto), input);
                    return s != null ? s.toMap() : null;
                })
            );
            try {
                processorExecutor.execute(task);
                tasks.add(task);
            } catch (RejectedExecutionException e) {
                log.error("Processor rejected for stage {} on {}, skipping", stage, dto.getId());
            }
        }

        // gather in order
        Map<String, Serializable> res = new HashMap<>();
        for (FutureTask<Map<String, Serializable>> task : tasks) {
            Map<String, Serializable> map = await(task, TimeUnit.SECONDS.toMillis(PROCESSOR_TIMEOUT), stage, dto);
            if (map != null) {
                res = MapUtils.mergeMultipleMaps(res, map);
            }
        }

        return res;
    }

    /*
     * Post processors: chained on the output, each time bound, the whole chain time bound
     */
    @SuppressWarnings("unchecked")
    protected <R extends Serializable> R postProcessOutput(@NotNull D dto, @NotNull String state, @Nullable R output) {
        if (postProcessorRegistry == null || output == null) {
            return output;
        }

        String stage = stage(state);
        List<PostProcessor<D>> postProcessors = postProcessorRegistry.getPostProcessors(stage);
        if (postProcessors == null || postProcessors.isEmpty()) {
            return output;
        }

        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(POST_PROCESSOR_CHAIN_TIMEOUT);

        if (output instanceof Collection<?> collection) {
            //multiple outputs: each element goes through the chain
            ArrayList<Serializable> list = new ArrayList<>();
            for (Object o : collection) {
                list.add(o instanceof Serializable so ? chain(dto, stage, so, postProcessors, deadline) : null);
            }
            return (R) list;
        }

        return (R) chain(dto, stage, output, postProcessors, deadline);
    }

    private Serializable chain(
        D dto,
        String stage,
        Serializable output,
        List<PostProcessor<D>> postProcessors,
        long deadline
    ) {
        Serializable current = output;
        for (PostProcessor<D> pp : postProcessors) {
            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                log.warn("Post processors chain timed out for stage {} on {}, skipping remaining", stage, dto.getId());
                break;
            }

            //each post processor works on its own copy of the output
            final Serializable in = SerializationUtils.clone(current);
            FutureTask<Serializable> task = new FutureTask<>(withContext(() -> pp.process(stage, copy(dto), in)));
            try {
                processorExecutor.execute(task);
            } catch (RejectedExecutionException e) {
                log.error("Post processor rejected for stage {} on {}, skipping", stage, dto.getId());
                continue;
            }

            Serializable o = await(
                task,
                Math.min(TimeUnit.SECONDS.toMillis(POST_PROCESSOR_TIMEOUT), remaining),
                stage,
                dto
            );
            if (o != null) {
                current = o;
            }
        }

        return current;
    }

    /**
     * Wait for a processing task: CoreRuntimeException fails the step, other failures are skipped
     */
    private <T> T await(FutureTask<T> task, long timeoutMs, String stage, D dto) {
        try {
            return task.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            log.warn("Processing timed out for stage {} on {}, skipping", stage, dto.getId());
            return null;
        } catch (InterruptedException e) {
            task.cancel(true);
            Thread.currentThread().interrupt();
            throw new SystemException("interrupted during processing for " + dto.getId(), e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CoreRuntimeException cre) {
                //explicit failure signaled by the processor: fail the step
                throw new StepFailure(dto, cre.getMessage(), cre);
            }

            log.error(
                "Processing failed for stage {} on {}: {}",
                stage,
                dto.getId(),
                cause != null ? cause.getMessage() : e.getMessage()
            );
            return null;
        }
    }

    /*
     * Effects: async, fire and forget, after the write
     */
    private void dispatchEffects(D saved, String state, @Nullable Serializable output) {
        if (effectRegistry == null) {
            return;
        }

        String stage = stage(state);
        List<Effect<D>> effects = effectRegistry.getEffects(stage);
        if (effects == null || effects.isEmpty()) {
            return;
        }

        D dto = copy(saved);
        Serializable out = output != null ? SerializationUtils.clone(output) : null;

        try {
            Callable<Void> task = withContext(() -> {
                //ordered, each effect isolated from the others: failures are captured per effect
                for (Effect<D> effect : effects) {
                    FutureTask<Void> single = new FutureTask<>(() -> {
                        effect.process(stage, copy(dto), out);
                        return null;
                    });
                    single.run();

                    try {
                        single.get();
                    } catch (ExecutionException e) {
                        Throwable cause = e.getCause() != null ? e.getCause() : e;
                        log.error("Effect failed for stage {} on {}: {}", stage, dto.getId(), cause.getMessage());
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
                return null;
            });
            effectExecutor.execute(new FutureTask<>(task));
        } catch (RejectedExecutionException e) {
            log.error("Effects rejected for stage {} on {}, skipping", stage, saved.getId());
        }
    }

    /*
     * Detached transitions
     */

    protected DetachedOperations detachedOperations() {
        if (detachedOperations == null) {
            detachedOperations = DetachedOperations.shared();
        }
        return detachedOperations;
    }

    private String detachedType() {
        return typeClass.getName();
    }

    /**
     * Start the detached part of a written step:
     * - started by the detached execution of the same object: scheduled again, delayed
     * - nested in the detached execution of another object: inline, never registered
     * - otherwise: registered and delayed on its own thread
     */
    private <R extends Serializable> void detach(
        D saved,
        String state,
        @Nullable R output,
        @Nullable String event,
        @Nullable BiConsumer<D, R> effect
    ) {
        String type = detachedType();
        String id = saved.getId();

        if (!detachedOperations().isRunningObject(type, id) && detachedOperations().isInside()) {
            log.debug("run detached {} for {} inline", state, id);
            //marked as running: a step started by its own caller effect is scheduled, never recursive
            detachedOperations().runAs(type, id, state, () -> runDetached(saved, state, output, effect, false));
            return;
        }

        log.debug("schedule detached {} for {}", state, id);
        detachedOperations().schedule(
            type,
            id,
            state,
            event,
            withContext(() -> runDetached(saved, state, output, effect, true)),
            withContext(() -> fail(id, state, "detached " + state + " timed out"))
        );
    }

    /**
     * Detached execution: effects in order, then the caller effect within the turn
     */
    private <R extends Serializable> void runDetached(
        D saved,
        String state,
        @Nullable R output,
        @Nullable BiConsumer<D, R> effect,
        boolean owned
    ) {
        //owned: running as the registered execution of this operation (not nested inline)
        String type = detachedType();
        String id = saved.getId();

        try {
            runEffectsInline(saved, state, output);
        } catch (CoreRuntimeException e) {
            log.debug("detached {} effects failed for {}: {}", state, id, e.getMessage());
            finish(owned, id, state);
            fail(id, state, e.getMessage());
            return;
        }

        if (Thread.currentThread().isInterrupted()) {
            //expired meanwhile: the expiry has already handled the object
            log.debug("detached {} for {} interrupted", state, id);
            return;
        }

        if (effect != null) {
            //within the turn: calls on the manager re-enter it, nobody else can step in
            try (EntityLock turn = lockManager.acquire(typeClass, id)) {
                D current = find(id);
                if (current == null || !state.equals(state(current))) {
                    log.debug("detached {} for {}: object changed meanwhile, caller effect skipped", state, id);
                } else {
                    effect.accept(copy(current), output);
                }
            } catch (CoreRuntimeException e) {
                log.debug("detached {} caller effect failed for {}: {}", state, id, e.getMessage());
                finish(owned, id, state);
                fail(id, state, e.getMessage());
                return;
            } catch (RuntimeException e) {
                log.error("detached {} caller effect failed for {}: {}", state, id, e.getMessage());
            }
        }

        //a re-run scheduled meanwhile keeps the entry (and its start time)
        if (!detachedOperations().isPending(type, id, state)) {
            finish(owned, id, state);
        }
    }

    private void finish(boolean owned, String id, String state) {
        if (owned) {
            detachedOperations().complete(detachedType(), id, state);
        }
    }

    /**
     * Effects of a detached step: in order, CoreRuntimeException fails, anything else is skipped
     */
    private void runEffectsInline(D saved, String state, @Nullable Serializable output) {
        if (effectRegistry == null) {
            return;
        }

        String stage = stage(state);
        List<Effect<D>> effects = effectRegistry.getEffects(stage);
        if (effects == null || effects.isEmpty()) {
            return;
        }

        for (Effect<D> effect : effects) {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            try {
                //each effect works on its own copies
                effect.process(stage, copy(saved), output != null ? SerializationUtils.clone(output) : null);
            } catch (CoreRuntimeException e) {
                throw e;
            } catch (RuntimeException e) {
                log.error("Effect failed for stage {} on {}: {}", stage, saved.getId(), e.getMessage());
            }
        }
    }

    /**
     * Move to ERROR, within the turn, only if the object is still in the given state
     */
    private void fail(String id, String state, @Nullable String message) {
        try (EntityLock turn = lockManager.acquire(typeClass, id)) {
            D current = find(id);
            if (current == null || !state.equals(state(current))) {
                log.debug("detached {} for {}: object changed meanwhile, not moved to ERROR", state, id);
                return;
            }

            if (message != null) {
                current.setStatus(MapUtils.mergeMultipleMaps(current.getStatus(), Map.of("message", message)));
            }
            handle(current, STATE_ERROR, message);
        } catch (RuntimeException e) {
            log.error("unable to move {} to ERROR from detached {}: {}", id, state, e.getMessage());
        }
    }

    /*
     * Helpers
     */
    protected static String stage(String state) {
        return "on" + StringUtils.capitalize(state.toLowerCase());
    }

    protected static String state(BaseDTO dto) {
        return dto instanceof StatusDTO s ? StatusFieldAccessor.with(s.getStatus()).getState() : null;
    }

    /**
     * Build the result of a transition: a copy of the base object, with metadata, spec and status
     * taken from the fsm context. Every other field is effectively final.
     */
    protected D merge(D base, D context) {
        D d = copy(base);
        d.setMetadata(context.getMetadata());
        d.setSpec(context.getSpec());
        d.setStatus(context.getStatus());
        return d;
    }

    protected D copy(D dto) {
        try {
            return JacksonMapper.deepClone(dto, typeClass);
        } catch (IOException e) {
            throw new SystemException("unable to copy dto " + dto.getId() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Propagate the current security context (the owner) to the executing thread
     */
    private static Runnable withContext(Runnable runnable) {
        SecurityContext context = SecurityContextHolder.getContext();
        return () -> {
            SecurityContext previous = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(context);
            try {
                runnable.run();
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        };
    }

    /**
     * Propagate the current security context (the owner) to the executing thread
     */
    private static <T> Callable<T> withContext(Callable<T> callable) {
        SecurityContext context = SecurityContextHolder.getContext();
        return () -> {
            SecurityContext previous = SecurityContextHolder.getContext();
            SecurityContextHolder.setContext(context);
            try {
                return callable.call();
            } finally {
                SecurityContextHolder.setContext(previous);
            }
        };
    }

    /*
     * Types
     */

    /**
     * A single FSM step: an optional event name (for events) and the logic to apply on the FSM
     */
    protected record Step<F, R>(@Nullable String event, @Nullable Serializable input, Function<F, Optional<R>> logic) {}

    private record StepResult<D, R>(
        D dto,
        @Nullable R output,
        boolean failed,
        @Nullable Transition<String, String, D> transition
    ) {}

    private record FsmOutcome<D, R>(
        String state,
        D context,
        @Nullable R output,
        @Nullable Transition<String, String, D> transition
    ) {}

    /**
     * Failure during a step: carries the dto to move to ERROR
     */
    private static class StepFailure extends RuntimeException {

        private final transient Object dto;

        StepFailure(Object dto, String message, Throwable cause) {
            super(message, cause, false, false);
            this.dto = dto;
        }
    }
}
