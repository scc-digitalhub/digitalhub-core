/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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

package it.smartcommunitylabdhub.trigger.lifecycle.models;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.trigger.lifecycle.LifecycleActuator;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = LifecycleActuator.ACTUATOR, entity = EntityName.TRIGGER)
public class LifecycleTriggerSpec extends TriggerBaseSpec {

    @Pattern(regexp = "store://([^/]+)/(.+)")
    @NotBlank
    private String key;

    private List<LifecycleStates> states;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        LifecycleTriggerSpec spec = mapper.convertValue(data, LifecycleTriggerSpec.class);
        this.key = spec.getKey();
        this.states = spec.getStates();
    }

    public static LifecycleTriggerSpec from(Map<String, Serializable> data) {
        LifecycleTriggerSpec spec = new LifecycleTriggerSpec();
        spec.configure(data);
        return spec;
    }
}
