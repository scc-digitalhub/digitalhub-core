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

package it.smartcommunitylabdhub.runtime.hera.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.hera.HeraRuntime;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = HeraRuntime.RUNTIME, kind = HeraRunSpec.KIND, entity = EntityName.RUN)
public class HeraRunSpec extends RunBaseSpec {

    public static final String KIND = HeraRuntime.RUNTIME + "+run";

    private Map<String, String> inputs = new HashMap<>();

    private Map<String, String> outputs = new HashMap<>();

    private Map<String, Serializable> parameters = new HashMap<>();

    // @JsonProperty("workflow_spec")
    @JsonSchemaIgnore
    @JsonUnwrapped
    private HeraWorkflowSpec workflowSpec;

    // @JsonProperty("pipeline_spec")
    @JsonUnwrapped
    private HeraPipelineTaskSpec taskPipelineSpec;

    @JsonUnwrapped
    private HeraBuildTaskSpec taskBuildSpec;

    public HeraRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        HeraRunSpec spec = mapper.convertValue(data, HeraRunSpec.class);
        this.inputs = spec.getInputs();
        this.outputs = spec.getOutputs();
        this.parameters = spec.getParameters();

        this.taskPipelineSpec = spec.getTaskPipelineSpec();
        this.taskBuildSpec = spec.getTaskBuildSpec();
        this.workflowSpec = spec.getWorkflowSpec();
    }

    public void setWorkflowSpec(HeraWorkflowSpec workflowSpec) {
        this.workflowSpec = workflowSpec;
    }

    public void setTaskPipelineSpec(HeraPipelineTaskSpec taskPipelineSpec) {
        this.taskPipelineSpec = taskPipelineSpec;
    }

    public void setTaskBuildSpec(HeraBuildTaskSpec taskBuildSpec) {
        this.taskBuildSpec = taskBuildSpec;
    }
}
