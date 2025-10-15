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

package it.smartcommunitylabdhub.core.triggers.lifecycle;

import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.services.RunManager;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.commons.services.TemplateProcessor;
import it.smartcommunitylabdhub.lifecycle.KindAwareLifecycleManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.triggers.infrastructure.Actuator;
import it.smartcommunitylabdhub.triggers.infrastructure.ActuatorComponent;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerLifecycleManager;
import it.smartcommunitylabdhub.triggers.models.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseStatus;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
public class KindAwareTriggerLifecycleManager extends KindAwareLifecycleManager<Trigger> implements InitializingBean {

    protected EntityRepository<Trigger> entityRepository;
    protected ApplicationEventPublisher eventPublisher;

    private TaskService taskService;
    private RunManager runService;
    private LifecycleManager<Run> runManager;
    private TemplateProcessor templateProcessor;

    private Map<
        String,
        Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus>
    > actuators;

    @Autowired(required = false)
    public void setActuators(
        List<Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus>> actuators
    ) {
        this.actuators = actuators.stream().collect(Collectors.toMap(r -> getKindFromAnnotation(r), r -> r));
    }

    @Autowired(required = false)
    public void setManagers(List<LifecycleManager<Trigger>> managers) {
        this.managers =
            new HashMap<>(managers.stream().collect(Collectors.toMap(r -> getKindFromAnnotation(r), r -> r)));
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setEntityRepository(EntityRepository<Trigger> entityService) {
        this.entityRepository = entityService;
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setRunService(RunManager runService) {
        this.runService = runService;
    }

    @Autowired
    public void setRunManager(LifecycleManager<Run> runManager) {
        this.runManager = runManager;
    }

    @Autowired(required = false)
    public void setTemplateProcessor(TemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //check managers and build default if missing
        if (actuators != null) {
            for (String k : actuators.keySet()) {
                if (!managers.containsKey(k)) {
                    log.debug("no lifecycle manager for actuators {}, building default", k);
                    Actuator<?, ?, ?> actuator = actuators.get(k);
                    TriggerLifecycleManager<?, ?, ?> m = new TriggerLifecycleManager<>(actuator);

                    //inject deps
                    m.setEntityRepository(this.entityRepository);
                    m.setEventPublisher(this.eventPublisher);
                    m.setTaskService(taskService);
                    m.setRunService(runService);
                    m.setRunManager(runManager);
                    m.setTemplateProcessor(templateProcessor);

                    managers.put(k, m);
                }
            }
        }

        //seal managers
        this.managers = Map.copyOf(this.managers);
    }

    private String getKindFromAnnotation(Object bean) {
        Class<?> clazz = bean.getClass();
        if (clazz.isAnnotationPresent(ActuatorComponent.class)) {
            ActuatorComponent annotation = clazz.getAnnotation(ActuatorComponent.class);
            return annotation.actuator();
        }

        throw new IllegalArgumentException("No @ActuatorComponent annotation found for class: " + clazz.getName());
    }
}
