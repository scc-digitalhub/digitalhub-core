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

package it.smartcommunitylabdhub.core.components.infrastructure.runtimes;

import it.smartcommunitylabdhub.triggers.infrastructure.Actuator;
import it.smartcommunitylabdhub.triggers.infrastructure.ActuatorComponent;
import it.smartcommunitylabdhub.triggers.models.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseStatus;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ActuatorFactory {

    private final Map<
        String,
        Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus>
    > actuatorMap;

    public ActuatorFactory(
        List<Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus>> actuators
    ) {
        actuatorMap =
            actuators.stream().collect(Collectors.toMap(this::getActuatorFromAnnotation, Function.identity()));
    }

    private String getActuatorFromAnnotation(
        Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus> actuator
    ) {
        Class<?> runtimeClass = actuator.getClass();
        if (runtimeClass.isAnnotationPresent(ActuatorComponent.class)) {
            ActuatorComponent annotation = runtimeClass.getAnnotation(ActuatorComponent.class);
            return annotation.actuator();
        }
        throw new IllegalArgumentException(
            "No @ActuatorComponent annotation found for runtime: " + runtimeClass.getName()
        );
    }

    /**
     * Get the Runtime for the given platform.
     *
     * @param runtime The runtime platform
     * @return The Runtime for the specified platform.
     * @throws IllegalArgumentException If no Runtime is found for the given platform.
     */
    public Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus> getActuator(
        String actuator
    ) {
        Actuator<? extends TriggerBaseSpec, ? extends TriggerBaseStatus, ? extends TriggerRunBaseStatus> instance =
            actuatorMap.get(actuator);
        if (instance == null) {
            throw new IllegalArgumentException("No actuator found for name: " + actuator);
        }

        return instance;
    }
}
