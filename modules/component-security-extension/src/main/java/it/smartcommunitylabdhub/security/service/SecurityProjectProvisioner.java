/*
 * Copyright 2026 the original author or authors.
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
 */

package it.smartcommunitylabdhub.security.service;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.security.config.SecurityExtensionProperties;
import it.smartcommunitylabdhub.security.model.SecurityAnalyzer;
import it.smartcommunitylabdhub.triggers.Trigger;
import it.smartcommunitylabdhub.triggers.TriggerManager;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerEvent;
import it.smartcommunitylabdhub.triggers.lifecycle.TriggerState;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SecurityProjectProvisioner {

    private final SecurityExtensionProperties properties;
    private final SecurityAnalyzerRegistry registry;
    private final FunctionManager functionManager;
    private final TriggerManager triggerManager;
    private final LifecycleManager<Trigger> triggerLifecycleManager;

    public SecurityProjectProvisioner(
        SecurityExtensionProperties properties,
        SecurityAnalyzerRegistry registry,
        FunctionManager functionManager,
        TriggerManager triggerManager,
        @Lazy LifecycleManager<Trigger> triggerLifecycleManager
    ) {
        this.properties = properties;
        this.registry = registry;
        this.functionManager = functionManager;
        this.triggerManager = triggerManager;
        this.triggerLifecycleManager = triggerLifecycleManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void provision(String project, String user) throws Exception {
        if (!properties.isEnabled()) {
            return;
        }

        for (SecurityAnalyzer analyzer : registry.list()) {
            BaseMetadata metadata = new BaseMetadata();
            metadata.setName(analyzer.getName());
            metadata.setDescription(analyzer.getDescription());
            metadata.setLabels(Set.of("security-analyzer"));

            Function function = functionManager.createFunction(
                Function.builder()
                    .project(project)
                    .name(analyzer.getName())
                    .kind(analyzer.getFunction().getKind())
                    .user(user)
                    .metadata(metadata.toMap())
                    .spec(new HashMap<>(analyzer.getFunction().getSpec()))
                    .build()
            );

            if (analyzer.getTrigger() == null) {
                continue;
            }

            Map<String, Serializable> spec = new HashMap<>(analyzer.getTrigger().getSpec());
            if (spec.get("key") instanceof String key) {
                spec.put("key", key.replace("${project}", project));
            }
            spec.put(
                Fields.FUNCTION,
                TaskSpecAccessor.build(function.getKind(), project, function.getName(), function.getId())
            );

            Trigger trigger = triggerManager.createTrigger(
                Trigger.builder()
                    .project(project)
                    .user(user)
                    .name(analyzer.getName())
                    .kind(analyzer.getTrigger().getKind())
                    .spec(spec)
                    .build()
            );
            trigger = triggerLifecycleManager.perform(trigger, TriggerEvent.RUN.name());
            if (!TriggerState.RUNNING.name().equals(StatusFieldAccessor.with(trigger.getStatus()).getState())) {
                throw new IllegalStateException("failed to activate security trigger " + analyzer.getName());
            }
        }
    }
}
