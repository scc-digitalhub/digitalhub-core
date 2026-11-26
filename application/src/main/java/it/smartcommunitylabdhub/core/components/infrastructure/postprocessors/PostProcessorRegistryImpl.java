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

package it.smartcommunitylabdhub.core.components.infrastructure.postprocessors;

import it.smartcommunitylabdhub.commons.annotations.common.PostProcessorType;
import it.smartcommunitylabdhub.commons.infrastructure.PostProcessor;
import it.smartcommunitylabdhub.commons.infrastructure.PostProcessorRegistry;
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

/**
 * Registry of post processors for a dto type, built from beans annotated with {@link PostProcessorType}.
 *
 * Post processors registered on {@link PostProcessorType#ALL_STAGES} are included in every stage.
 * Ordering follows {@code @Order}, missing order defaults to lowest precedence.
 */
public class PostProcessorRegistryImpl<D extends BaseDTO & SpecDTO & StatusDTO>
    implements PostProcessorRegistry<D>, ApplicationListener<ApplicationReadyEvent>, ResolvableTypeProvider {

    protected final Class<D> typeClass;

    // stage -> ordered list, sealed after onApplicationEvent
    private Map<String, List<PostProcessor<D>>> processors = Map.of();
    // ordered list for stages without specific post processors (wildcard only)
    private List<PostProcessor<D>> wildcard = List.of();

    private final ApplicationContext applicationContext;

    @SuppressWarnings("unchecked")
    public PostProcessorRegistryImpl(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.typeClass = (Class<D>) t;
    }

    public PostProcessorRegistryImpl(ApplicationContext applicationContext, Class<D> typeClass) {
        Assert.notNull(applicationContext, "application context is required");
        Assert.notNull(typeClass, "type class is required");
        this.applicationContext = applicationContext;
        this.typeClass = typeClass;
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(PostProcessorRegistryImpl.class, typeClass);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onApplicationEvent(@NotNull ApplicationReadyEvent event) {
        Map<String, List<Entry<D>>> staging = new HashMap<>();
        List<Entry<D>> all = new ArrayList<>();

        applicationContext
            .getBeansWithAnnotation(PostProcessorType.class)
            .forEach((name, bean) -> {
                PostProcessorType annotation = AnnotationUtils.findAnnotation(bean.getClass(), PostProcessorType.class);
                if (bean instanceof PostProcessor && annotation != null && annotation.type() == typeClass) {
                    Order orderAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), Order.class);
                    int order = orderAnnotation != null ? orderAnnotation.value() : Ordered.LOWEST_PRECEDENCE;
                    Entry<D> entry = new Entry<>(name, order, (PostProcessor<D>) bean);

                    for (String stage : annotation.stages()) {
                        List<Entry<D>> list = PostProcessorType.ALL_STAGES.equals(stage)
                            ? all
                            : staging.computeIfAbsent(stage, k -> new ArrayList<>());
                        if (list.stream().noneMatch(p -> name.equals(p.name()))) {
                            list.add(entry);
                        }
                    }
                }
            });

        // merge wildcard into every stage, then sort by order
        Map<String, List<PostProcessor<D>>> built = new HashMap<>();
        staging.forEach((stage, list) -> {
            List<Entry<D>> merged = new ArrayList<>(list);
            all.stream().filter(a -> merged.stream().noneMatch(m -> m.name().equals(a.name()))).forEach(merged::add);
            built.put(stage, sorted(merged));
        });

        wildcard = sorted(all);
        processors = Map.copyOf(built);
    }

    private static <D extends BaseDTO & SpecDTO & StatusDTO> List<PostProcessor<D>> sorted(List<Entry<D>> list) {
        return list.stream().sorted(Comparator.comparingInt(Entry::order)).map(Entry::processor).toList();
    }

    @Override
    public List<PostProcessor<D>> getPostProcessors(String stage) {
        return processors.getOrDefault(stage, wildcard);
    }

    private record Entry<D extends BaseDTO & SpecDTO & StatusDTO>(String name, int order, PostProcessor<D> processor) {}
}
