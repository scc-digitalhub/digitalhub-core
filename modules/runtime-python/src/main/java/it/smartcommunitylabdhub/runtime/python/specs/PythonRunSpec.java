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

package it.smartcommunitylabdhub.runtime.python.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = PythonRuntime.RUNTIME, kind = PythonRunSpec.KIND, entity = EntityName.RUN)
public class PythonRunSpec extends RunBaseSpec {

    public static final String KIND = PythonRuntime.RUNTIME + "+run";

    @JsonUnwrapped
    private PythonJobTaskSpec taskJobSpec;

    @JsonUnwrapped
    private PythonServeTaskSpec taskServeSpec;

    @JsonUnwrapped
    private PythonBuildTaskSpec taskBuildSpec;

    @JsonSchemaIgnore
    @JsonUnwrapped
    private PythonFunctionSpec functionSpec;

    private Map<String, String> inputs = new HashMap<>();

    //DISABLED: currently not supported by sdk
    // private Map<String, String> outputs = new HashMap<>();

    private Map<String, Serializable> parameters = new HashMap<>();

    @JsonProperty("init_parameters")
    private Map<String, Serializable> initParameters = new HashMap<>();

    public PythonRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        PythonRunSpec spec = mapper.convertValue(data, PythonRunSpec.class);

        this.taskJobSpec = spec.getTaskJobSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.functionSpec = spec.getFunctionSpec();
        this.taskBuildSpec = spec.getTaskBuildSpec();

        this.inputs = spec.getInputs();
        // this.outputs = spec.getOutputs();
        this.parameters = spec.getParameters();
        this.initParameters = spec.getInitParameters();
    }

    public void setTaskJobSpec(PythonJobTaskSpec taskJobSpec) {
        this.taskJobSpec = taskJobSpec;
    }

    public void setTaskServeSpec(PythonServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public void setTaskBuildSpec(PythonBuildTaskSpec buildTaskSpec) {
        this.taskBuildSpec = buildTaskSpec;
    }

    public void setFunctionSpec(PythonFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }
}
