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

package it.smartcommunitylabdhub.runtime.vllm.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.vllm.VLLMServeRuntime;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = VLLMServeRuntime.RUNTIME, kind = VLLMServeRunSpec.KIND, entity = EntityName.RUN)
public class VLLMServeRunSpec extends RunBaseSpec {

    public static final String KIND = VLLMServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private VLLMServeFunctionSpec functionSpec;

    @JsonUnwrapped
    private VLLMServeTaskSpec taskServeSpec;

    @Schema(title = "fields.vllm.args.title", description = "fields.vllm.args.description")
    private List<String> args;

    @Schema(title = "fields.vllm.enableTelemetry.title", description = "fields.vllm.enableTelemetry.description")
    @JsonProperty("enable_telemetry")
    private Boolean enableTelemetry;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        VLLMServeRunSpec spec = mapper.convertValue(data, VLLMServeRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.args = spec.getArgs();
        this.enableTelemetry = spec.getEnableTelemetry();
    }

    public void setFunctionSpec(VLLMServeFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(VLLMServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static VLLMServeRunSpec with(Map<String, Serializable> data) {
        VLLMServeRunSpec spec = new VLLMServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
