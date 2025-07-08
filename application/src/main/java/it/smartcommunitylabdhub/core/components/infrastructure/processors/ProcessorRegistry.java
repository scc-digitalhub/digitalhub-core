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

package it.smartcommunitylabdhub.core.components.infrastructure.processors;

import it.smartcommunitylabdhub.commons.annotations.common.RunProcessorType;
import it.smartcommunitylabdhub.commons.infrastructure.RunProcessor;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

@Component
public class ProcessorRegistry implements ApplicationListener<ContextRefreshedEvent> {

    private final Map<String, List<Map.Entry<String, RunProcessor<? extends RunBaseStatus>>>> registry =
        new ConcurrentHashMap<>();

    private final ApplicationContext applicationContext;

    @Autowired
    public ProcessorRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(@NotNull ContextRefreshedEvent event) {
        applicationContext
            .getBeansWithAnnotation(RunProcessorType.class)
            .entrySet()
            .forEach(e -> {
                String name = e.getKey();
                Object bean = e.getValue();
                RunProcessorType annotation = bean.getClass().getAnnotation(RunProcessorType.class);

                if (bean instanceof RunProcessor && (annotation != null)) {
                    for (String stage : annotation.stages()) {
                        //register if missing
                        List<Entry<String, RunProcessor<?>>> processors = registry.computeIfAbsent(
                            stage,
                            k -> new ArrayList<>()
                        );

                        if (processors.stream().noneMatch(p -> name.equals(p.getKey()))) {
                            processors.add(Map.entry(name, (RunProcessor<?>) bean));
                        }
                    }
                }
            });
    }

    public List<RunProcessor<? extends RunBaseStatus>> getProcessors(String stage) {
        return registry
            .getOrDefault(stage, Collections.emptyList())
            .stream()
            .map(Entry::getValue)
            .collect(Collectors.toList());
    }
}
