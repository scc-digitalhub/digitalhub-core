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

package it.smartcommunitylabdhub.runtime.container.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.container.ContainerRuntime;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
// @Setter
@NoArgsConstructor
@SpecType(runtime = ContainerRuntime.RUNTIME, kind = ContainerRunSpec.KIND, entity = EntityName.RUN)
public class ContainerRunSpec extends RunBaseSpec {

    public static final String KIND = ContainerRuntime.RUNTIME + "+run";

    // @JsonProperty("job_spec")
    @JsonUnwrapped
    private ContainerJobTaskSpec taskJobSpec;

    // @JsonProperty("deploy_spec")
    @JsonUnwrapped
    private ContainerDeployTaskSpec taskDeploySpec;

    // @JsonProperty("serve_spec")
    @JsonUnwrapped
    private ContainerServeTaskSpec taskServeSpec;

    // @JsonProperty("build_spec")
    @JsonUnwrapped
    private ContainerBuildTaskSpec taskBuildSpec;

    // @JsonProperty("function_spec")
    @JsonSchemaIgnore
    @JsonUnwrapped
    private ContainerFunctionSpec functionSpec;

    @Schema(title = "fields.container.args.title", description = "fields.container.args.description")
    private List<String> args;

    public ContainerRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        ContainerRunSpec spec = mapper.convertValue(data, ContainerRunSpec.class);

        this.taskJobSpec = spec.getTaskJobSpec();
        this.taskDeploySpec = spec.getTaskDeploySpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.functionSpec = spec.getFunctionSpec();
        this.taskBuildSpec = spec.getTaskBuildSpec();

        this.args = spec.getArgs();
    }

    public void setFunctionSpec(ContainerFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskJobSpec(ContainerJobTaskSpec taskJobSpec) {
        this.taskJobSpec = taskJobSpec;
    }

    public void setTaskDeploySpec(ContainerDeployTaskSpec taskDeploySpec) {
        this.taskDeploySpec = taskDeploySpec;
    }

    public void setTaskServeSpec(ContainerServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public void setTaskBuildSpec(ContainerBuildTaskSpec taskBuildSpec) {
        this.taskBuildSpec = taskBuildSpec;
    }
}
