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

package it.smartcommunitylabdhub.triggers.specs;

import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import jakarta.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TriggerBaseSpec extends BaseSpec {

    @NotEmpty
    private String task;

    private String function;

    private String workflow;

    @NotEmpty
    private Map<String, Serializable> template;

    @Override
    public void configure(Map<String, Serializable> data) {
        TriggerBaseSpec spec = mapper.convertValue(data, TriggerBaseSpec.class);

        this.task = spec.getTask();
        this.function = spec.getFunction();
        this.workflow = spec.getWorkflow();

        this.template = spec.getTemplate();
    }

    public static TriggerBaseSpec from(Map<String, Serializable> data) {
        TriggerBaseSpec spec = new TriggerBaseSpec();
        spec.configure(data);

        return spec;
    }
}
