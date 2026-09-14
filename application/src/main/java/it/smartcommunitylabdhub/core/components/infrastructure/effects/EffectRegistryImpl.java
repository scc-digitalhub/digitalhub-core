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

package it.smartcommunitylabdhub.core.components.infrastructure.effects;

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.commons.infrastructure.EffectRegistry;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
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
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.util.Assert;

public class EffectRegistryImpl<
    D extends BaseDTO & SpecDTO & StatusDTO
> implements EffectRegistry<D>, ApplicationListener<ApplicationReadyEvent>, ResolvableTypeProvider {

    protected final Class<D> typeClass;

    // stage -> ordered list of processors — sealed after onApplicationEvent, read by getEffects
    // immutable map (Map.copyOf) assigned once on ApplicationReadyEvent before any request thread can read it
    private Map<String, List<Effect<D>>> processors = Map.of();

    private final ApplicationContext applicationContext;

    @SuppressWarnings("unchecked")
    public EffectRegistryImpl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;

        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.typeClass = (Class<D>) t;
    }

    public EffectRegistryImpl(ApplicationContext applicationContext, Class<D> typeClass) {
        Assert.notNull(applicationContext, "application context is required");
        Assert.notNull(typeClass, "type class is required");

        this.applicationContext = applicationContext;
        this.typeClass = typeClass;
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(EffectRegistryImpl.class, typeClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        Map<String, List<EffectEntry<D>>> staging = new HashMap<>();

        applicationContext
            .getBeansWithAnnotation(EffectType.class)
            .entrySet()
            .forEach(e -> {
                String name = e.getKey();
                Object bean = e.getValue();
                EffectType annotation = AnnotationUtils.findAnnotation(bean.getClass(), EffectType.class);

                if (bean instanceof Effect && annotation != null && annotation.type() == typeClass) {
                    Order orderAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), Order.class);
                    int order = orderAnnotation != null ? orderAnnotation.value() : Ordered.LOWEST_PRECEDENCE;
                    for (String stage : annotation.stages()) {
                        List<EffectEntry<D>> list = staging.computeIfAbsent(stage, k -> new ArrayList<>());
                        if (list.stream().noneMatch(p -> name.equals(p.name()))) {
                            list.add(new EffectEntry<>(name, order, (Effect<D>) bean));
                        }
                    }
                }
            });

        // sort by record.order then materialize into the read-only processors map
        Map<String, List<Effect<D>>> built = new HashMap<>();
        staging.forEach((stage, list) -> {
            list.sort(Comparator.comparingInt(EffectEntry::order));
            List<Effect<D>> sorted = new ArrayList<>(list.size());
            for (EffectEntry<D> e : list) {
                sorted.add(e.processor());
            }
            built.put(stage, List.copyOf(sorted));
        });
        processors = Map.copyOf(built);
    }

    @Override
    public List<Effect<D>> getEffects(String stage) {
        return processors.getOrDefault(stage, List.of());
    }

    private record EffectEntry<D extends BaseDTO & SpecDTO & StatusDTO>(String name, int order, Effect<D> processor) {}
}
