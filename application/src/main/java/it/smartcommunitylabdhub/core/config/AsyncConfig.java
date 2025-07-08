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

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.security.task.DelegatingSecurityContextAsyncTaskExecutor;

@Configuration
@Order(5)
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean
    @Primary
    AsyncTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(50);
        executor.setMaxPoolSize(100);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        //use a delegating executor to propagate security context
        return new DelegatingSecurityContextAsyncTaskExecutor(executor);
    }

    @Bean(name = "taskScheduler")
    @Primary
    public ThreadPoolTaskScheduler threadPoolTaskScheduler() {
        ThreadPoolTaskScheduler threadPoolTaskScheduler = new ThreadPoolTaskScheduler();
        threadPoolTaskScheduler.setPoolSize(10);
        threadPoolTaskScheduler.setThreadNamePrefix("scheduler-");
        return threadPoolTaskScheduler;
    }
    // @Bean
    // PollingService pollingService(@Qualifier("taskExecutor") TaskExecutor executor) {
    //     // Create new Polling service instance
    //     PollingService pollingService = new PollingService(executor);

    //     // CREATE POLLERS EXAMPLE
    //     //
    //     // List<Workflow> test = new ArrayList<>();
    //     // Function<Integer, Integer> doubleFunction = num -> {
    //     // Random randomno = new Random();
    //     // long randomDelay = (long) (randomno.nextDouble() * 40 + 20); // Random delay
    //     // between 3 and 10
    //     // // seconds
    //     // System.out.println("RANDOM DELAY TEST WORKFLOW " + randomDelay);
    //     // try {
    //     // Thread.sleep(randomDelay * 1000);
    //     // } catch (InterruptedException e) {
    //     // // Handle interrupted exception if necessary
    //     // }

    //     // return 9;
    //     // };
    //     // test.add(WorkflowFactory
    //     // .builder().step(doubleFunction, 5).build());
    //     // pollingService.createPoller("TEST-POLLER", test, 3, true);

    //     // Start polling
    //     pollingService.startPolling();

    //     return pollingService;
    // }
}
