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

import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sBaseMonitor;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;

@Configuration
@Order(6)
@ConditionalOnKubernetes
public class MonitorConfig implements InitializingBean {

    @Autowired
    private ThreadPoolTaskScheduler threadPoolTaskScheduler;

    @Autowired
    private List<K8sBaseMonitor<?>> k8sJobMonitors;

    @Value("${monitors.delay}")
    private int delay;

    @Value("${monitors.min-delay}")
    private int minDelay;

    //TODO refactor with proper scan of annotation
    public void afterPropertiesSet() {
        Assert.isTrue(delay >= minDelay, "Delay must be greater than 0");

        // staggered start up for monitors
        Instant start = Instant.now();

        for (K8sBaseMonitor<?> monitor : k8sJobMonitors) {
            Class<?> builderClass = monitor.getClass();
            if (builderClass.isAnnotationPresent(MonitorComponent.class) && monitor instanceof Runnable) {
                MonitorComponent annotation = builderClass.getAnnotation(MonitorComponent.class);

                //TODO wrap with nested class and split/group frameworks
                String framework = annotation.framework();

                //use delay to ensure no concurrency may arise between invocations
                //TODO read from annotation
                // int delay = annotation.rate();

                start = start.plus(10, ChronoUnit.SECONDS);

                threadPoolTaskScheduler.scheduleWithFixedDelay(monitor, start, Duration.ofSeconds(delay));
                //     pollingService.createPoller(
                //         annotation.framework(),
                //         WorkflowFactory.builder().step(i -> {monitor.monitor(); return null}).build(),
                //         delay,
                //         true,
                //         false
                //     );

                //     pollingService.startOne(annotation.framework());
            }
        }
    }
}
