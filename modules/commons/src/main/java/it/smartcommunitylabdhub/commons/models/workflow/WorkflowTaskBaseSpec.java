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

package it.smartcommunitylabdhub.commons.models.workflow;

import it.smartcommunitylabdhub.commons.models.task.TaskBaseSpec;
import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkflowTaskBaseSpec extends TaskBaseSpec {

    @NotBlank
    String workflow;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        WorkflowTaskBaseSpec spec = mapper.convertValue(data, WorkflowTaskBaseSpec.class);

        this.workflow = spec.getWorkflow();
    }

    public static WorkflowTaskBaseSpec from(Map<String, Serializable> map) {
        WorkflowTaskBaseSpec spec = new WorkflowTaskBaseSpec();
        spec.configure(map);

        return spec;
    }
}
