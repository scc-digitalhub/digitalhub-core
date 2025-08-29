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

package it.smartcommunitylabdhub.core.config;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.core.runs.persistence.RunnableRepository;
import it.smartcommunitylabdhub.core.runs.store.RunnableStoreImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(4)
public class InfrastructureConfig {

    // @Bean
    // protected RuntimeFactory runtimeFactory(
    //     List<
    //         Runtime<? extends ExecutableBaseSpec, ? extends RunBaseSpec, ? extends RunBaseStatus, ? extends RunRunnable>
    //     > runtimes
    // ) {
    //     return new RuntimeFactory(runtimes);
    // }

    // @Bean
    // protected ActuatorFactory actuatorFactory(
    //     List<Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus>> actuators
    // ) {
    //     return new ActuatorFactory(actuators);
    // }

    @Bean
    protected RunnableStore.StoreSupplier runnableStoreService(RunnableRepository runnableRepository) {
        return new RunnableStore.StoreSupplier() {
            @Override
            public <T extends RunRunnable> RunnableStore<T> get(Class<T> clazz) {
                return new RunnableStoreImpl<>(clazz, runnableRepository);
            }
        };
    }
}
