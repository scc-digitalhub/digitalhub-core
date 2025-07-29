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
import it.smartcommunitylabdhub.runtime.flower.FlowerRuntime;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = FlowerRuntime.RUNTIME, kind = FlowerRunSpec.KIND, entity = EntityName.RUN)
public class FlowerRunSpec extends RunBaseSpec {

    public static final String KIND = FlowerRuntime.RUNTIME + "+run";

    @JsonUnwrapped
    private FlowerTrainTaskSpec taskTrainSpec;
    @JsonUnwrapped
    private FlowerClientTaskSpec taskClientSpec;
    @JsonUnwrapped
    private FlowerServerTaskSpec taskServerSpec;

    @JsonUnwrapped
    private FlowerBuildClientTaskSpec taskBuildClientSpec;
    @JsonUnwrapped
    private FlowerBuildServerTaskSpec taskBuildServerSpec;

    @JsonSchemaIgnore
    @JsonUnwrapped
    private FlowerFunctionSpec functionSpec;

    private Map<String, String> inputs = new HashMap<>();

    private Map<String, Serializable> parameters = new HashMap<>();
    
    @Schema(title = "fields.flower.superlink.title", description = "fields.flower.superlink.description")
    private String superlink;

    @Schema(title = "fields.flower.federation.title", description = "fields.flower.federation.description")
    private String federation;

    public FlowerRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FlowerRunSpec spec = mapper.convertValue(data, FlowerRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();
    
        this.taskTrainSpec = spec.getTaskTrainSpec();
        this.taskClientSpec = spec.getTaskClientSpec();
        this.taskServerSpec = spec.getTaskServerSpec();
        this.taskBuildClientSpec = spec.getTaskBuildClientSpec();
        this.taskBuildServerSpec = spec.getTaskBuildServerSpec();

        this.inputs = spec.getInputs();
        this.parameters = spec.getParameters();
        this.superlink = spec.getSuperlink();
        this.federation = spec.getFederation();
    }
    public void setTaskTrainSpec(FlowerTrainTaskSpec taskTrainSpec) {
        this.taskTrainSpec = taskTrainSpec;
    }

    public void setTaskClientSpec(FlowerClientTaskSpec taskClientSpec) {
        this.taskClientSpec = taskClientSpec;
    }
    public void setTaskServerSpec(FlowerServerTaskSpec taskServerSpec) {
        this.taskServerSpec = taskServerSpec;
    }
    public void setTaskBuildClientSpec(FlowerBuildClientTaskSpec taskBuildClientSpec) {
        this.taskBuildClientSpec = taskBuildClientSpec;
    }
    public void setTaskBuildServerSpec(FlowerBuildServerTaskSpec taskBuildServerSpec) {
        this.taskBuildServerSpec = taskBuildServerSpec;
    }
    public void setFunctionSpec(FlowerFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }
}
