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
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.channel.QueueChannel;
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

    @Bean(name = "applicationEventMulticaster")
    ApplicationEventMulticaster applicationEventMulticaster(@Qualifier("taskExecutor") Executor taskExecutor) {
        SimpleApplicationEventMulticaster multicaster = new SimpleApplicationEventMulticaster();
        multicaster.setTaskExecutor(taskExecutor);
        return multicaster;
    }

    // -----------------------------------------------------------------
    // Runnable saga channels: single queue for all Runnable events, dispatched
    // by RunnableEventPublisher to the correct listener.
    // Runs on a dedicated pool - no CallerRunsPolicy risk because the caller
    // here is never a committing transaction thread.
    // -----------------------------------------------------------------
    @Bean(name = "runnableQueueChannel")
    MessageChannel runnableQueueChannel() {
        // bounded buffer = backpressure + ordering buffer
        return new QueueChannel(1000);
    }

    @Bean(name = "runnableExecutorChannel")
    MessageChannel runnableExecutorChannel(@Qualifier("runnableExecutor") Executor runnableExecutor) {
        return new ExecutorChannel(runnableExecutor);
    }

    @Bean
    IntegrationFlow runnableMessageFlow(
        @Qualifier("runnableQueueChannel") MessageChannel runnableQueueChannel,
        @Qualifier("runnableExecutorChannel") MessageChannel runnableExecutorChannel,
        RunnableListener handler
    ) {
        return IntegrationFlow.from(runnableQueueChannel)
            .channel(runnableExecutorChannel)
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
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("rex-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        // use a delegating executor to propagate security context
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    // -----------------------------------------------------------------
    // Entity saga channels: single queue for all EntityEvent sagas,
    // dispatched by EntityEventDispatcher to the correct listener.
    // Runs on a dedicated pool - no CallerRunsPolicy risk because the
    // caller here is never a committing transaction thread.
    // -----------------------------------------------------------------

    @Bean(name = "entityEventQueueChannel")
    MessageChannel entityEventQueueChannel() {
        return new QueueChannel(1000);
    }

    @Bean(name = "entityEventsExecutor")
    AsyncTaskExecutor entityEventsExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("eev-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        // use a delegating executor to propagate security context
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Bean(name = "entityEventExecutorChannel")
    MessageChannel entityEventExecutorChannel(@Qualifier("entityEventsExecutor") Executor entityEventsExecutor) {
        return new ExecutorChannel(entityEventsExecutor);
    }

    @Bean
    IntegrationFlow entityEventFlow(
        @Qualifier("entityEventQueueChannel") MessageChannel entityEventQueueChannel,
        @Qualifier("entityEventExecutorChannel") MessageChannel entityEventExecutorChannel,
        EntityEventDispatcher dispatcher
    ) {
        return IntegrationFlow.from(entityEventQueueChannel)
            .channel(entityEventExecutorChannel)
            .handle(dispatcher, "handle")
            .get();
    }

    // -----------------------------------------------------------------
    // RunOperations saga channel (EntityOperation<Run> events)
    // -----------------------------------------------------------------

    @Bean(name = "entityOperationsQueueChannel")
    MessageChannel entityOperationsQueueChannel() {
        return new QueueChannel(500);
    }

    @Bean(name = "entityOperationsExecutorChannel")
    MessageChannel entityOperationsExecutorChannel(@Qualifier("entityEventsExecutor") Executor entityEventsExecutor) {
        return new ExecutorChannel(entityEventsExecutor);
    }

    @Bean
    IntegrationFlow entityOperationsFlow(
        @Qualifier("entityOperationsQueueChannel") MessageChannel entityOperationsQueueChannel,
        @Qualifier("entityOperationsExecutorChannel") MessageChannel entityOperationsExecutorChannel,
        EntityOperationsDispatcher handler
    ) {
        return IntegrationFlow.from(entityOperationsQueueChannel)
            .channel(entityOperationsExecutorChannel)
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
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(50);
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
