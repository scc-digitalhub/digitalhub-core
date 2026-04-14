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
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

public class ProcessorRegistryImpl<D extends BaseDTO & SpecDTO & StatusDTO, Z extends Spec>
    implements ProcessorRegistry<D, Z>, ApplicationListener<ApplicationReadyEvent> {

    private record ProcessorEntry<D extends BaseDTO & SpecDTO & StatusDTO, Z extends Spec>(
        String name,
        int order,
        Processor<D, ? extends Z> processor
    ) {}

    protected final Class<D> typeClass;
    protected final Class<Z> specClass;

    // stage -> ordered list of processors — sealed after onApplicationEvent, read by getProcessors
    // immutable map (Map.copyOf) assigned once on ApplicationReadyEvent before any request thread can read it
    private Map<String, List<Processor<D, ? extends Z>>> processors = Map.of();

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
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        Map<String, List<ProcessorEntry<D, Z>>> staging = new HashMap<>();

        applicationContext
            .getBeansWithAnnotation(ProcessorType.class)
            .entrySet()
            .forEach(e -> {
                String name = e.getKey();
                Object bean = e.getValue();
                ProcessorType annotation = AnnotationUtils.findAnnotation(bean.getClass(), ProcessorType.class);

                if (
                    bean instanceof Processor &&
                    annotation != null &&
                    annotation.type() == typeClass &&
                    annotation.spec() == specClass
                ) {
                    Order orderAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), Order.class);
                    int order = orderAnnotation != null ? orderAnnotation.value() : Ordered.LOWEST_PRECEDENCE;
                    for (String stage : annotation.stages()) {
                        List<ProcessorEntry<D, Z>> list = staging.computeIfAbsent(stage, k -> new ArrayList<>());
                        if (list.stream().noneMatch(p -> name.equals(p.name()))) {
                            list.add(new ProcessorEntry<>(name, order, (Processor<D, Z>) bean));
                        }
                    }
                }
            });

        // sort by record.order then materialize into the read-only processors map
        Map<String, List<Processor<D, ? extends Z>>> built = new HashMap<>();
        staging.forEach((stage, list) -> {
            list.sort(Comparator.comparingInt(ProcessorEntry::order));
            List<Processor<D, ? extends Z>> sorted = new ArrayList<>(list.size());
            for (ProcessorEntry<D, Z> e : list) {
                sorted.add(e.processor());
            }
            built.put(stage, List.copyOf(sorted));
        });
        processors = Map.copyOf(built);
    }

    @Override
    public List<Processor<D, ? extends Z>> getProcessors(String stage) {
        return processors.getOrDefault(stage, List.of());
    }
}
