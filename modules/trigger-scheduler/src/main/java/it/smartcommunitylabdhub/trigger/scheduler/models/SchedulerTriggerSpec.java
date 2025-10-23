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

package it.smartcommunitylabdhub.trigger.scheduler.models;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = "scheduler", entity = EntityName.TRIGGER)
public class SchedulerTriggerSpec extends TriggerBaseSpec {

    @Pattern(regexp = Keys.CRONTAB_PATTERN)
    @NotBlank
    private String schedule;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        SchedulerTriggerSpec spec = mapper.convertValue(data, SchedulerTriggerSpec.class);
        this.schedule = spec.getSchedule();
    }

    public static SchedulerTriggerSpec from(Map<String, Serializable> data) {
        SchedulerTriggerSpec spec = new SchedulerTriggerSpec();
        spec.configure(data);
        return spec;
    }
}
