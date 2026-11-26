/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.lifecycle.locks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager.EntityLock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LocalEntityLockManagerTest {

    private static final class TypeA {}

    private static final class TypeB {}

    private LocalEntityLockManager manager;
    private ExecutorService executor;

    @BeforeEach
    void setUp() {
        manager = new LocalEntityLockManager(Duration.ofMillis(500));
        executor = Executors.newFixedThreadPool(4);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void turnIsReentrantForSameThread() {
        try (EntityLock outer = manager.acquire(TypeA.class, "1")) {
            try (EntityLock inner = manager.acquire(TypeA.class, "1")) {
                assertThat(manager.isHeldByCurrentThread(TypeA.class, "1")).isTrue();
            }
            //still held after inner release
            assertThat(manager.isHeldByCurrentThread(TypeA.class, "1")).isTrue();
        }
        assertThat(manager.isHeldByCurrentThread(TypeA.class, "1")).isFalse();
    }

    @Test
    void sameObjectOperationsRunOneAtATime() throws Exception {
        AtomicInteger inside = new AtomicInteger();
        AtomicInteger maxInside = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            futures.add(
                executor.submit(() -> {
                    try (EntityLock lock = manager.acquire(TypeA.class, "1")) {
                        int n = inside.incrementAndGet();
                        maxInside.accumulateAndGet(n, Math::max);
                        Thread.sleep(20);
                        inside.decrementAndGet();
                    }
                    return null;
                })
            );
        }

        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }

        assertThat(maxInside.get()).isEqualTo(1);
    }

    @Test
    void differentObjectsAreIndependent() throws Exception {
        try (EntityLock lock = manager.acquire(TypeA.class, "1")) {
            //same id, different type
            Future<Boolean> otherType = executor.submit(() -> {
                try (EntityLock l = manager.acquire(TypeB.class, "1")) {
                    return true;
                }
            });
            //same type, different id
            Future<Boolean> otherId = executor.submit(() -> {
                try (EntityLock l = manager.acquire(TypeA.class, "2")) {
                    return true;
                }
            });

            assertThat(otherType.get(1, TimeUnit.SECONDS)).isTrue();
            assertThat(otherId.get(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void timeoutRaisesExceptionNamingHolder() throws Exception {
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        Future<?> holder = executor.submit(() -> {
            Thread.currentThread().setName("holder-thread");
            try (EntityLock lock = manager.acquire(TypeA.class, "1")) {
                held.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return null;
        });

        assertThat(held.await(1, TimeUnit.SECONDS)).isTrue();

        assertThatThrownBy(() -> manager.acquire(TypeA.class, "1"))
            .isInstanceOf(EntityLockException.class)
            .hasMessageContaining("holder-thread");

        release.countDown();
        holder.get(1, TimeUnit.SECONDS);

        //available again once released
        try (EntityLock lock = manager.acquire(TypeA.class, "1")) {
            assertThat(manager.isHeldByCurrentThread(TypeA.class, "1")).isTrue();
        }
    }

    @Test
    void unusedTurnsAreReleasedFromMemory() throws Exception {
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            String id = "id-" + (i % 10);
            futures.add(
                executor.submit(() -> {
                    try (EntityLock lock = manager.acquire(TypeA.class, id)) {
                        Thread.sleep(1);
                    }
                    return null;
                })
            );
        }
        for (Future<?> f : futures) {
            f.get(5, TimeUnit.SECONDS);
        }

        assertThat(manager.size()).isZero();

        //timeouts must release as well
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        Future<?> holder = executor.submit(() -> {
            try (EntityLock lock = manager.acquire(TypeA.class, "x")) {
                held.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return null;
        });
        held.await(1, TimeUnit.SECONDS);
        assertThatThrownBy(() -> manager.acquire(TypeA.class, "x")).isInstanceOf(EntityLockException.class);
        release.countDown();
        holder.get(1, TimeUnit.SECONDS);

        assertThat(manager.size()).isZero();
    }

    @Test
    void closingTwiceIsHarmless() {
        EntityLock lock = manager.acquire(TypeA.class, "1");
        lock.close();
        lock.close();

        assertThat(manager.size()).isZero();
        assertThat(manager.isHeldByCurrentThread(TypeA.class, "1")).isFalse();
    }

    @Test
    void interruptedWaitRaisesAndKeepsInterruptFlag() throws Exception {
        LocalEntityLockManager slow = new LocalEntityLockManager(Duration.ofSeconds(10));
        CountDownLatch held = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        List<Object> results = Collections.synchronizedList(new ArrayList<>());

        executor.submit(() -> {
            try (EntityLock lock = slow.acquire(TypeA.class, "1")) {
                held.countDown();
                release.await(5, TimeUnit.SECONDS);
            }
            return null;
        });
        held.await(1, TimeUnit.SECONDS);

        Thread waiter = new Thread(() -> {
            try {
                slow.acquire(TypeA.class, "1");
                results.add("acquired");
            } catch (EntityLockException e) {
                results.add(e);
                results.add(Thread.currentThread().isInterrupted());
            }
        });
        waiter.start();
        Thread.sleep(100);
        waiter.interrupt();
        waiter.join(2000);
        release.countDown();

        assertThat(results).hasSize(2);
        assertThat(results.get(0)).isInstanceOf(EntityLockException.class);
        assertThat(results.get(1)).isEqualTo(true);
    }
}
