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

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Runtime;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.lifecycle.KindAwareLifecycleManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunLifecycleManager;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Primary
public class KindAwareRunLifecycleManager extends KindAwareLifecycleManager<Run> implements InitializingBean {

    private Map<String, Runtime<? extends RunBaseSpec, ? extends RunBaseStatus, ? extends RunRunnable>> runtimes;

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

    @Override
    public void afterPropertiesSet() throws Exception {
        //check managers and build default if missing
        if (runtimes != null) {
            for (String k : runtimes.keySet()) {
                String kind = k + "+run"; //TODO remove hardcoded suffix
                if (!managers.containsKey(kind)) {
                    log.debug("no lifecycle manager for runtime {}, building default", k);
                    Runtime<?, ?, ?> runtime = runtimes.get(k);
                    RunLifecycleManager<?, ?, ?> m = new RunLifecycleManager<>(runtime);
                    //inject deps
                    m.setEntityRepository(this.entityRepository);
                    m.setEventPublisher(this.eventPublisher);
                    m.setProcessorRegistry(this.processorRegistry);

                    managers.put(kind, m);
                }
            }
        }

        //seal managers
        this.managers = Map.copyOf(this.managers);
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
