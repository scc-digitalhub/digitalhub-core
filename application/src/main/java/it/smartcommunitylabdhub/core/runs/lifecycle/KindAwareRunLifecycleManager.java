/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.core.runs.lifecycle;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.ProcessorRegistry;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.Metadata;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.core.CoreApplication;
import it.smartcommunitylabdhub.lifecycle.KindAwareLifecycleManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunLifecycleManager;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Primary;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@Primary
public class KindAwareRunLifecycleManager extends KindAwareLifecycleManager<Run> implements InitializingBean {

    private Map<String, Runtime<? extends RunBaseSpec, ? extends RunBaseStatus, ? extends RunRunnable>> runtimes;
    List<Pair<SpecType, Class<? extends Spec>>> specs = new ArrayList<>();

    protected EntityRepository<Run> entityRepository;
    protected ApplicationEventPublisher eventPublisher;
    protected ProcessorRegistry<Run, Metadata> metadataProcessorRegistry;
    protected ProcessorRegistry<Run, Spec> specProcessorRegistry;
    protected ProcessorRegistry<Run, Status> statusProcessorRegistry;

    @Autowired(required = false)
    public void setRuntimes(
        List<Runtime<? extends RunBaseSpec, ? extends RunBaseStatus, ? extends RunRunnable>> runtimes
    ) {
        this.runtimes = runtimes.stream().collect(Collectors.toMap(r -> getRuntimeFromAnnotation(r), r -> r));
    }

    @Autowired(required = false)
    public void setManagers(List<LifecycleManager<Run>> managers) {
        this.managers =
            new HashMap<>(managers.stream().collect(Collectors.toMap(r -> getRuntimeFromAnnotation(r), r -> r)));
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setEntityRepository(EntityRepository<Run> entityService) {
        this.entityRepository = entityService;
    }

    @Autowired(required = false)
    public void setMetadataProcessorRegistry(ProcessorRegistry<Run, Metadata> metadataProcessorRegistry) {
        this.metadataProcessorRegistry = metadataProcessorRegistry;
    }

    @Autowired(required = false)
    public void setSpecProcessorRegistry(ProcessorRegistry<Run, Spec> specProcessorRegistry) {
        this.specProcessorRegistry = specProcessorRegistry;
    }

    @Autowired(required = false)
    public void setStatusProcessorRegistry(ProcessorRegistry<Run, Status> statusProcessorRegistry) {
        this.statusProcessorRegistry = statusProcessorRegistry;
    }

    @PostConstruct
    @SuppressWarnings("unchecked")
    public void scanForSpecTypes() {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(SpecType.class));

        List<String> basePackages = getBasePackages();
        log.info("Scanning for specTypes under packages {}", basePackages);

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(basePackage);

            for (BeanDefinition beanDefinition : candidateComponents) {
                String className = beanDefinition.getBeanClassName();
                try {
                    // Load the class and check for SpecType annotation.
                    Class<? extends Spec> specClass = (Class<? extends Spec>) Class.forName(className);
                    SpecType type = specClass.getAnnotation(SpecType.class);
                    String kind = type.kind();
                    EntityName entity = type.entity();
                    String runtime = type.runtime();

                    if (EntityName.RUN != entity) {
                        //ignore non run specs
                        continue;
                    }

                    if (StringUtils.hasText(runtime)) {
                        //enforce runtime prefix rule on kind
                        //TODO remove
                        if (!kind.startsWith(runtime)) {
                            throw new IllegalArgumentException("invalid kind " + kind + "for runtime " + runtime);
                        }
                    }

                    log.debug("discovered spec for {}:{} with class {}", entity, kind, specClass.getName());
                    specs.add(Pair.of(type, specClass));
                } catch (IllegalArgumentException | ClassNotFoundException e) {
                    log.error("error registering spec {}: {}", className, e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(entityRepository, "entity service is required");
        Assert.notNull(eventPublisher, "event publisher is required");

        //check managers and build default if missing
        if (specs != null) {
            for (Pair<SpecType, Class<? extends Spec>> p : specs) {
                SpecType type = p.getFirst();
                String kind = type.kind();
                if (!managers.containsKey(kind)) {
                    log.warn("no lifecycle manager for spec {}, building default", kind);

                    //check if we have a runtime for this kind
                    if (!runtimes.containsKey(type.runtime())) {
                        log.warn("no runtime for spec {}, cannot build default manager", kind);
                        continue;
                    }

                    Runtime<?, ?, ?> runtime = runtimes.get(type.runtime());
                    RunLifecycleManager<?, ?, ?> m = new RunLifecycleManager<>(runtime);
                    //inject deps
                    m.setEntityRepository(this.entityRepository);
                    m.setEventPublisher(this.eventPublisher);
                    m.setMetadataProcessorRegistry(metadataProcessorRegistry);
                    m.setSpecProcessorRegistry(specProcessorRegistry);
                    m.setStatusProcessorRegistry(statusProcessorRegistry);

                    managers.put(kind, m);
                }
            }
        }

        //seal managers
        this.managers = Map.copyOf(this.managers);
    }

    private List<String> getBasePackages() {
        List<String> basePackages = new ArrayList<>();
        ComponentScan componentScan = CoreApplication.class.getAnnotation(ComponentScan.class);
        if (componentScan != null) {
            Collections.addAll(basePackages, componentScan.basePackages());
        }
        if (basePackages.isEmpty()) {
            throw new IllegalArgumentException("Base package not specified in @ComponentScan");
        }
        return basePackages;
    }

    private String getRuntimeFromAnnotation(Object bean) {
        Class<?> clazz = bean.getClass();
        if (clazz.isAnnotationPresent(RuntimeComponent.class)) {
            RuntimeComponent annotation = clazz.getAnnotation(RuntimeComponent.class);
            return annotation.runtime();
        }

        throw new IllegalArgumentException("No @RuntimeComponent annotation found for class: " + clazz.getName());
    }
}
