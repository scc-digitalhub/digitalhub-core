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

package it.smartcommunitylabdhub.runtime.python.hydra.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.python.hydra.HydraRuntime;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = HydraRuntime.RUNTIME, kind = HydraSubtaskRunSpec.KIND, entity = Run.class)
public final class HydraSubtaskRunSpec extends HydraRunSpec {

    public static final String KIND = HydraSubtaskTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private HydraFunctionSpec functionSpec;

    @JsonUnwrapped
    private HydraSubtaskTaskSpec taskServeSpec;

    @JsonProperty("job_ref")
    private String jobRef;

    private Map<String, Serializable> parameters = new HashMap<>();

    public HydraSubtaskRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        HydraSubtaskRunSpec spec = mapper.convertValue(data, HydraSubtaskRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.jobRef = spec.getJobRef();
        this.parameters = spec.getParameters();
    }

    public void setFunctionSpec(HydraFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(HydraSubtaskTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public void setJobRef(String jobRef) {
        this.jobRef = jobRef;
    }
}
