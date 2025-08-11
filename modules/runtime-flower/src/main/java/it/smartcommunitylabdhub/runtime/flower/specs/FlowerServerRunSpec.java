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

package it.smartcommunitylabdhub.runtime.flower.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.flower.FlowerServerRuntime;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = FlowerServerRuntime.RUNTIME, kind = FlowerServerRunSpec.KIND, entity = EntityName.RUN)
public class FlowerServerRunSpec extends RunBaseSpec {

    public static final String KIND = FlowerServerRuntime.RUNTIME + "+run";

    @JsonUnwrapped
    private FlowerTrainTaskSpec taskTrainSpec;
    @JsonUnwrapped
    private FlowerServerTaskSpec taskDeploySpec;
    @JsonUnwrapped
    private FlowerBuildServerTaskSpec taskBuildSpec;

    @JsonSchemaIgnore
    @JsonUnwrapped
    private FlowerServerFunctionSpec functionSpec;

    private Map<String, String> inputs = new HashMap<>();

    private Map<String, Serializable> parameters = new HashMap<>();
    
    @Schema(title = "fields.flower.federation.title", description = "fields.flower.federation.description")
    private String federation;
    @Schema(title = "fields.flower.superlink.title", description = "fields.flower.superlink.description")
    private String superlink;

    public FlowerServerRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FlowerServerRunSpec spec = mapper.convertValue(data, FlowerServerRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();
    
        this.taskTrainSpec = spec.getTaskTrainSpec();
        this.taskDeploySpec = spec.getTaskDeploySpec();
        this.taskBuildSpec = spec.getTaskBuildSpec();

        this.inputs = spec.getInputs();
        this.parameters = spec.getParameters();
        this.federation = spec.getFederation();
        this.superlink = spec.getSuperlink();
    }
    public void setTaskTrainSpec(FlowerTrainTaskSpec taskTrainSpec) {
        this.taskTrainSpec = taskTrainSpec;
    }
    public void setTaskDeploySpec(FlowerServerTaskSpec taskDeploySpec) {
        this.taskDeploySpec = taskDeploySpec;
    }
    public void setTaskBuildSpec(FlowerBuildServerTaskSpec taskBuildSpec) {
        this.taskBuildSpec = taskBuildSpec;
    }
    public void setFunctionSpec(FlowerServerFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }
}
