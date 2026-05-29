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

package it.smartcommunitylabdhub.core.config;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.core.events.EntityEventDispatcher;
import it.smartcommunitylabdhub.core.events.EntityOperationsDispatcher;
import it.smartcommunitylabdhub.core.events.RunnableEventListener;
import it.smartcommunitylabdhub.core.events.RunnableMessageDispatcher;
import it.smartcommunitylabdhub.framework.k8s.runnables.RunnableEventPublisher;
import it.smartcommunitylabdhub.runtimes.events.RunnableMessagePublisher;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.PartitionedChannel;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
@Order(5)
@EnableAsync
@EnableIntegration
public class AsyncConfig implements AsyncConfigurer {

    private static final int CORE_POOL_SIZE = 5;
    private static final int QUEUE_CAPACITY = 10000;

    @Bean(name = "applicationEventMulticaster")
    ApplicationEventMulticaster applicationEventMulticaster(@Qualifier("taskExecutor") Executor taskExecutor) {
        SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
        multicaster.setTaskExecutor(taskExecutor);
        return multicaster;
    }

    // -----------------------------------------------------------------
    // Runnable saga channel: DirectChannel entry point -> PartitionedChannel.
    // Publishers detach as soon as the task is handed to the rex- pool.
    // Messages for the same run id are always routed to the same thread,
    // giving per-run sequential processing without any locking.
    // An optional delayMs header defers dispatch to reduce contention.
    // -----------------------------------------------------------------
    @Bean(name = "runnableQueueChannel")
    MessageChannel runnableQueueChannel() {
        return new DirectChannel();
    }

    @Bean
    RunnableEventPublisher runnableEventPublisher(
        @Qualifier("runnableQueueChannel") MessageChannel runnableQueueChannel
    ) {
        return new RunnableEventPublisher(runnableQueueChannel);
    }

    @Bean
    IntegrationFlow runnableEventFlow(
        @Qualifier("runnableQueueChannel") MessageChannel runnableQueueChannel,
        RunnableEventListener handler
    ) {
        // PartitionedChannel(int partitions, Function<Message<?>, Object> partitionKeyStrategy)
        // creates CORE_POOL_SIZE single-threaded internal executors — one per partition.
        // Same run id always maps to the same partition → sequential per-run processing.
        PartitionedChannel partitionedChannel = new PartitionedChannel(CORE_POOL_SIZE, msg -> {
            Object payload = msg.getPayload();
            if (payload instanceof it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent<?> event) {
                return event.getId();
            }
            return msg.getHeaders().getId();
        });
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
        partitionedChannel.setThreadFactory(r -> {
            Thread t = new Thread(r);
            t.setName("rex-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        // Each partition gets its own bounded queue. Divide by the number of partitions
        // so that total capacity across all partitions equals QUEUE_CAPACITY.
        partitionedChannel.setWorkerQueueSize(QUEUE_CAPACITY / CORE_POOL_SIZE);

        return IntegrationFlow.from(runnableQueueChannel)
            .delay(d -> d.messageGroupId("runnable-channel-group").delayExpression("headers['delayMs'] ?: 0"))
            .channel(partitionedChannel)
            .handle(handler, "handle")
            .get();
    }

    @Bean(name = "runnableMessageChannel")
    MessageChannel runnableMessageChannel() {
        return new DirectChannel();
    }

    @Bean
    RunnableMessagePublisher runnableMessagePublisher(
        @Qualifier("runnableMessageChannel") MessageChannel runnableMessageChannel
    ) {
        return new RunnableMessagePublisher(runnableMessageChannel);
    }

    @Bean
    IntegrationFlow runnableMessageFlow(
        @Qualifier("runnableMessageChannel") MessageChannel runnableMessageChannel,
        RunnableMessageDispatcher dispatcher
    ) {
        // PartitionedChannel(int partitions, Function<Message<?>, Object> partitionKeyStrategy)
        // creates CORE_POOL_SIZE single-threaded internal executors — one per partition.
        // Same run id always maps to the same partition → sequential per-run processing.
        PartitionedChannel partitionedChannel = new PartitionedChannel(CORE_POOL_SIZE, msg -> {
            Object payload = msg.getPayload();
            if (payload instanceof RunRunnable run) {
                return run.getId();
            }
            return msg.getHeaders().getId();
        });
        java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();
        partitionedChannel.setThreadFactory(r -> {
            Thread t = new Thread(r);
            t.setName("mex-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        // Each partition gets its own bounded queue. Divide by the number of partitions
        // so that total capacity across all partitions equals QUEUE_CAPACITY.
        partitionedChannel.setWorkerQueueSize(QUEUE_CAPACITY / CORE_POOL_SIZE);

        return IntegrationFlow.from(runnableMessageChannel)
            .delay(d -> d.messageGroupId("runnable-message-group").delayExpression("headers['delayMs'] ?: 0"))
            .channel(partitionedChannel)
            .handle(dispatcher, "receive")
            .get();
    }

    // -----------------------------------------------------------------
    // Entity event saga channel: DirectChannel entry point -> ExecutorChannel.
    // Published from @TransactionalEventListener(AFTER_COMMIT); the
    // transaction thread detaches as soon as the task is queued in eev-.
    // -----------------------------------------------------------------

    @Bean(name = "entityEventQueueChannel")
    MessageChannel entityEventQueueChannel() {
        return new DirectChannel();
    }

    @Bean(name = "entityEventsExecutor")
    AsyncTaskExecutor entityEventsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(CORE_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("eev-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        // use a delegating executor to propagate security context
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Bean
    IntegrationFlow entityEventFlow(
        @Qualifier("entityEventQueueChannel") MessageChannel entityEventQueueChannel,
        @Qualifier("entityEventsExecutor") Executor entityEventsExecutor,
        EntityEventDispatcher dispatcher
    ) {
        return IntegrationFlow.from(entityEventQueueChannel)
            .channel(new ExecutorChannel(entityEventsExecutor))
            .handle(dispatcher, "handle")
            .get();
    }

    // -----------------------------------------------------------------
    // Entity operations saga channel: DirectChannel entry point -> ExecutorChannel.
    // Shares the eev- executor pool with entity events.
    // -----------------------------------------------------------------

    @Bean(name = "entityOperationsQueueChannel")
    MessageChannel entityOperationsQueueChannel() {
        return new DirectChannel();
    }

    @Bean
    IntegrationFlow entityOperationsFlow(
        @Qualifier("entityOperationsQueueChannel") MessageChannel entityOperationsQueueChannel,
        @Qualifier("entityEventsExecutor") Executor entityEventsExecutor,
        EntityOperationsDispatcher handler
    ) {
        return IntegrationFlow.from(entityOperationsQueueChannel)
            .channel(new ExecutorChannel(entityEventsExecutor))
            .handle(handler, "handle")
            .get();
    }

    /*
     * Async executions
     */
    @Bean
    @Primary
    AsyncTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        // use a delegating executor to propagate security context
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Bean(name = "processorExecutor")
    Executor processorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2 * CORE_POOL_SIZE);
        executor.setMaxPoolSize(2 * CORE_POOL_SIZE);
        // bounded queue: AbortPolicy rejects when full, completing the future exceptionally
        // rather than falling back to the calling rex- thread via CallerRunsPolicy
        executor.setQueueCapacity(100 * CORE_POOL_SIZE);
        executor.setThreadNamePrefix("processor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        // propagate security context from calling thread to processor threads
        return new DelegatingSecurityContextExecutor(executor.getThreadPoolExecutor());
    }

    @Bean(name = "taskScheduler")
    ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(10);
        threadPoolTaskScheduler.setThreadNamePrefix("scheduler-");
        return threadPoolTaskScheduler;
    }
}
