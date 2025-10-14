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

package it.smartcommunitylabdhub.runtime.flower.client.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.runtime.flower.client.FlowerClientRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = FlowerClientRuntime.RUNTIME, kind = FlowerClientDeployRunSpec.KIND, entity = EntityName.RUN)
public class FlowerClientDeployRunSpec extends FlowerClientRunSpec {

    public static final String KIND = FlowerClientDeployTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private FlowerClientFunctionSpec functionSpec;

    @JsonUnwrapped
    private FlowerClientDeployTaskSpec taskDeploySpec;

    public FlowerClientDeployRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FlowerClientDeployRunSpec spec = mapper.convertValue(data, FlowerClientDeployRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskDeploySpec = spec.getTaskDeploySpec();
    }

    public void setFunctionSpec(FlowerClientFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskDeploySpec(FlowerClientDeployTaskSpec taskDeploySpec) {
        this.taskDeploySpec = taskDeploySpec;
    }
}
