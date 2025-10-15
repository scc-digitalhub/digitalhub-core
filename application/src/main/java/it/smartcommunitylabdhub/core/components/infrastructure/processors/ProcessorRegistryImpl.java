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

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.infrastructure.ProcessorRegistry;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

public class ProcessorRegistryImpl<D extends BaseDTO & SpecDTO & StatusDTO, Z extends Spec>
    implements ProcessorRegistry<D, Z>, ApplicationListener<ContextRefreshedEvent> {

    protected final Class<D> typeClass;
    protected final Class<Z> specClass;

    private final Map<String, List<Map.Entry<String, Processor<D, ? extends Z>>>> registry = new ConcurrentHashMap<>();

    private final ApplicationContext applicationContext;

    @SuppressWarnings("unchecked")
    public ProcessorRegistryImpl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;

        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.typeClass = (Class<D>) t;
        Type s = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.specClass = (Class<Z>) s;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onApplicationEvent(@NotNull ContextRefreshedEvent event) {
        applicationContext
            .getBeansWithAnnotation(ProcessorType.class)
            .entrySet()
            .forEach(e -> {
                String name = e.getKey();
                Object bean = e.getValue();
                ProcessorType annotation = bean.getClass().getAnnotation(ProcessorType.class);

                if (bean instanceof Processor && (annotation != null)) {
                    if (annotation.type() != typeClass || annotation.spec() != specClass) {
                        // skip if not matching type
                        return;
                    }

                    for (String stage : annotation.stages()) {
                        //register if missing
                        List<Entry<String, Processor<D, ? extends Z>>> processors = registry.computeIfAbsent(
                            stage,
                            k -> new ArrayList<>()
                        );

                        if (processors.stream().noneMatch(p -> name.equals(p.getKey()))) {
                            processors.add(Map.entry(name, (Processor<D, Z>) bean));
                        }
                    }
                }
            });
    }

    @Override
    public List<Processor<D, ? extends Z>> getProcessors(String stage) {
        return registry
            .getOrDefault(stage, Collections.emptyList())
            .stream()
            .map(Entry::getValue)
            .collect(Collectors.toList());
    }
}
