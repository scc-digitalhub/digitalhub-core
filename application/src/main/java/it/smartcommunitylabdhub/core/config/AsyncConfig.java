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

import it.smartcommunitylabdhub.core.events.EntityEventDispatcher;
import it.smartcommunitylabdhub.core.events.EntityOperationsDispatcher;
import it.smartcommunitylabdhub.core.runs.lifecycle.RunnableListener;
import it.smartcommunitylabdhub.runtimes.events.RunnableEventPublisher;
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
    // Runnable saga channel: DirectChannel entry point -> ExecutorChannel.
    // Publishers detach as soon as the task is handed to the rex- pool.
    // An optional delayMs header defers dispatch to reduce contention.
    // -----------------------------------------------------------------
    @Bean(name = "runnableQueueChannel")
    MessageChannel runnableQueueChannel() {
        return new DirectChannel();
    }

    @Bean
    IntegrationFlow runnableMessageFlow(
        @Qualifier("runnableQueueChannel") MessageChannel runnableQueueChannel,
        @Qualifier("runnableExecutor") Executor runnableExecutor,
        RunnableListener handler
    ) {
        return IntegrationFlow.from(runnableQueueChannel)
            .delay(d -> d.messageGroupId("runnable-channel-group").delayExpression("headers['delayMs'] ?: 0"))
            .channel(new ExecutorChannel(runnableExecutor))
            .handle(handler, "handle")
            .get();
    }

    @Bean
    RunnableEventPublisher runnableEventPublisher(
        @Qualifier("runnableQueueChannel") MessageChannel runnableQueueChannel
    ) {
        return new RunnableEventPublisher(runnableQueueChannel);
    }

    @Bean(name = "runnableExecutor")
    AsyncTaskExecutor runnableExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(CORE_POOL_SIZE);
        executor.setMaxPoolSize(CORE_POOL_SIZE);
        executor.setQueueCapacity(QUEUE_CAPACITY);
        executor.setThreadNamePrefix("rex-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();

        // use a delegating executor to propagate security context
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
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
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(0); // no queue: CallerRunsPolicy kicks in immediately when pool is full
        executor.setThreadNamePrefix("processor-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
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
