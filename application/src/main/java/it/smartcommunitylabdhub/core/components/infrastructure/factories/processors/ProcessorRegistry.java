package it.smartcommunitylabdhub.core.components.infrastructure.factories.processors;

import it.smartcommunitylabdhub.commons.annotations.common.RunProcessorType;
import it.smartcommunitylabdhub.commons.infrastructure.RunProcessor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ProcessorRegistry implements ApplicationListener<ContextRefreshedEvent> {

    private final Map<String, List<RunProcessor<?>>> processorRegistry = new ConcurrentHashMap<>();

    private final ApplicationContext applicationContext;

    @Autowired
    public ProcessorRegistry(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(@NotNull ContextRefreshedEvent event) {
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(RunProcessorType.class);

        for (Object bean : beans.values()) {
            if (bean instanceof RunProcessor) {
                RunProcessorType annotation = bean.getClass().getAnnotation(RunProcessorType.class);
                if (annotation != null) {
                    for (String stage : annotation.stages()) {
                        processorRegistry
                                .computeIfAbsent(stage, k -> new ArrayList<>())
                                .add((RunProcessor<?>) bean);
                    }
                }
            }
        }
    }

    public List<RunProcessor<?>> getProcessors(String stage) {
        return processorRegistry.getOrDefault(stage, Collections.emptyList());
    }
}