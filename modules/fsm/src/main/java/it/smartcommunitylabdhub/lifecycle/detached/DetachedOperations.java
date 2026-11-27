/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.lifecycle.detached;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * Runner for detached operations.
 *
 * - every launch is DELAYED, with a backoff growing with the number of attempts (capped)
 * - at most ONE live execution (running or scheduled) per key: further launches collapse into it
 * - executions run on virtual threads, without time bounds
 * - entries older than the long timeout are expired: the execution is interrupted (cooperative), the
 *   entry removed and the expiry handler invoked
 * - the current thread knows which detached operation it is running: work nested inside a detached
 *   execution runs inline, in the same thread, never registered
 *
 * Executions are given already wrapped with any context they need (ex. security).
 */
@Slf4j
public class DetachedOperations implements AutoCloseable {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofHours(12);
    private static final Duration DEFAULT_BASE_DELAY = Duration.ofSeconds(1);
    private static final Duration DEFAULT_MAX_DELAY = Duration.ofHours(1);
    private static final Duration DEFAULT_CHECK_INTERVAL = Duration.ofMinutes(1);

    /**
     * Shared fallback instance, non persistent, used by unwired managers. Created on first use.
     */
    public static DetachedOperations shared() {
        return SharedHolder.SHARED;
    }

    private static final class SharedHolder {

        private static final DetachedOperations SHARED = new DetachedOperations(new InMemoryDetachedRegistry());
    }

    //keys of the detached operations running on the current thread (outermost first)
    private static final ThreadLocal<List<String>> RUNNING = ThreadLocal.withInitial(ArrayList::new);

    private final DetachedRegistry registry;
    private final Map<String, Live> live = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler;
    private final ExecutorService executor;
    private ScheduledFuture<?> checker;

    private Duration timeout = DEFAULT_TIMEOUT;
    private Duration baseDelay = DEFAULT_BASE_DELAY;
    private Duration maxDelay = DEFAULT_MAX_DELAY;

    //expiry of entries without a live execution in this process (ex. lost after a failure)
    private Consumer<DetachedEntry> orphanExpiryHandler;

    public DetachedOperations(@NotNull DetachedRegistry registry) {
        Assert.notNull(registry, "registry is required");
        this.registry = registry;

        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "detached-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.executor = Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("detached-", 0).factory());

        startChecker(DEFAULT_CHECK_INTERVAL);
    }

    public DetachedRegistry getRegistry() {
        return registry;
    }

    public void setTimeout(@NotNull Duration timeout) {
        Assert.isTrue(timeout != null && !timeout.isNegative() && !timeout.isZero(), "timeout must be positive");
        this.timeout = timeout;
    }

    public void setBaseDelay(@NotNull Duration baseDelay) {
        Assert.isTrue(baseDelay != null && !baseDelay.isNegative(), "base delay can not be negative");
        this.baseDelay = baseDelay;
    }

    public void setMaxDelay(@NotNull Duration maxDelay) {
        Assert.isTrue(maxDelay != null && !maxDelay.isNegative(), "max delay can not be negative");
        this.maxDelay = maxDelay;
    }

    /**
     * Handler for expired entries without a live execution: the in-process handler is missing
     */
    public void setOrphanExpiryHandler(@Nullable Consumer<DetachedEntry> handler) {
        this.orphanExpiryHandler = handler;
    }

    public synchronized void setCheckInterval(@NotNull Duration interval) {
        Assert.isTrue(interval != null && !interval.isNegative() && !interval.isZero(), "interval must be positive");
        startChecker(interval);
    }

    private synchronized void startChecker(Duration interval) {
        if (checker != null) {
            checker.cancel(false);
        }
        long ms = interval.toMillis();
        checker = scheduler.scheduleWithFixedDelay(this::expire, ms, ms, TimeUnit.MILLISECONDS);
    }

    /*
     * Thread scope
     */

    /**
     * True when the current thread is running any detached operation
     */
    public boolean isInside() {
        return !RUNNING.get().isEmpty();
    }

    /**
     * True when the current thread is running a detached operation of the given object (any state)
     */
    public boolean isRunningObject(@NotNull String type, @NotNull String id) {
        String prefix = type + ":" + id + ":";
        return RUNNING.get().stream().anyMatch(k -> k.startsWith(prefix));
    }

    /**
     * True when a re-run of the given operation is scheduled by (or running in) another execution
     * than the current thread's one
     */
    public boolean isPending(@NotNull String type, @NotNull String id, @NotNull String state) {
        Live l = live.get(DetachedEntry.key(type, id, state));
        return l != null && l.owner != Thread.currentThread();
    }

    /**
     * True when a live execution (running or scheduled) exists for the given operation
     */
    public boolean isLive(@NotNull String type, @NotNull String id, @NotNull String state) {
        return live.containsKey(DetachedEntry.key(type, id, state));
    }

    /*
     * Scheduling
     */

    /**
     * Launch a detached operation after the backoff delay.
     *
     * Registers the operation if absent and records the attempt. Collapses into an existing live
     * execution owned by another thread (running or scheduled).
     *
     * @param task     the execution, already wrapped with its context
     * @param onExpire invoked when the operation exceeds the long timeout
     */
    public void schedule(
        @NotNull String type,
        @NotNull String id,
        @NotNull String state,
        @Nullable String event,
        @NotNull Runnable task,
        @Nullable Runnable onExpire
    ) {
        Assert.notNull(task, "task can not be null");
        String key = DetachedEntry.key(type, id, state);

        Live next = new Live(onExpire);
        Live current = live.compute(key, (k, existing) -> {
            if (existing != null && existing.owner != Thread.currentThread()) {
                //collapse: someone else is running or will run it
                return existing;
            }
            return next;
        });

        if (current != next) {
            log.debug("detached {} already live, collapsing", key);
            return;
        }

        registry.register(type, id, state, event);
        DetachedEntry entry = registry.attempt(type, id, state);
        int attempts = entry != null ? entry.attempts() : 1;
        Duration delay = backoff(attempts);
        log.debug("schedule detached {} attempt {} in {}", key, attempts, delay);

        next.future = scheduler.schedule(
            () -> {
                //the execution may have been replaced or expired meanwhile
                if (live.get(key) != next) {
                    return;
                }
                next.future = executor.submit(() -> run(key, next, task));
            },
            delay.toMillis(),
            TimeUnit.MILLISECONDS
        );
    }

    private void run(String key, Live owner, Runnable task) {
        owner.owner = Thread.currentThread();
        RUNNING.get().add(key);
        try {
            task.run();
        } catch (RuntimeException e) {
            log.error("detached {} failed: {}", key, e.getMessage(), e);
        } finally {
            RUNNING.get().remove(key);
            owner.owner = null;
            //release the live slot if still ours and nothing was scheduled in the meantime
            live.remove(key, owner);
        }
    }

    /**
     * Run the given work as part of the detached operation, in the current thread, without
     * registering it (ex. nested inline in another detached operation)
     */
    public void runAs(@NotNull String type, @NotNull String id, @NotNull String state, @NotNull Runnable work) {
        String key = DetachedEntry.key(type, id, state);
        List<String> running = RUNNING.get();
        boolean added = !running.contains(key);
        if (added) {
            running.add(key);
        }
        try {
            work.run();
        } finally {
            if (added) {
                running.remove(key);
            }
        }
    }

    /**
     * Complete the operation: the entry is removed. Callers must not complete an operation with a
     * pending re-run (see {@link #isPending}).
     */
    public void complete(@NotNull String type, @NotNull String id, @NotNull String state) {
        String key = DetachedEntry.key(type, id, state);
        log.debug("complete detached {}", key);
        registry.remove(type, id, state);
    }

    /**
     * Backoff for the given attempt: base * 2^(attempts-1), capped
     */
    private Duration backoff(int attempts) {
        long base = baseDelay.toMillis();
        long max = maxDelay.toMillis();
        int exp = Math.max(0, Math.min(attempts - 1, 30));
        long delay = base <= 0 ? 0 : base * (1L << exp);
        if (delay < 0 || delay > max) {
            delay = max;
        }
        return Duration.ofMillis(delay);
    }

    /*
     * Expiry
     */

    /**
     * Expire the operations older than the timeout: interrupt, remove, notify
     */
    private void expire() {
        Instant limit = Instant.now().minus(timeout);
        try {
            for (DetachedEntry entry : registry.list()) {
                if (entry.startedAt().isAfter(limit)) {
                    continue;
                }

                String key = entry.key();
                log.warn("detached {} started at {} exceeded timeout {}, expiring", key, entry.startedAt(), timeout);

                registry.remove(entry.type(), entry.id(), entry.state());
                Live l = live.remove(key);
                if (l == null && orphanExpiryHandler != null) {
                    Consumer<DetachedEntry> handler = orphanExpiryHandler;
                    executor.execute(() -> {
                        try {
                            handler.accept(entry);
                        } catch (RuntimeException e) {
                            log.error("error handling expiry of detached {}: {}", key, e.getMessage(), e);
                        }
                    });
                }
                if (l != null) {
                    if (l.future != null) {
                        l.future.cancel(true);
                    }
                    if (l.onExpire != null) {
                        //never block the scheduler: the handler waits for the object turn
                        executor.execute(() -> {
                            try {
                                l.onExpire.run();
                            } catch (RuntimeException e) {
                                log.error("error handling expiry of detached {}: {}", key, e.getMessage(), e);
                            }
                        });
                    }
                }
            }
        } catch (RuntimeException e) {
            log.error("error checking detached operations: {}", e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
        executor.shutdownNow();
    }

    /*
     * Live execution: scheduled or running
     */
    private static final class Live {

        private final Runnable onExpire;
        private volatile Future<?> future;
        private volatile Thread owner;

        private Live(Runnable onExpire) {
            this.onExpire = onExpire;
        }
    }
}
