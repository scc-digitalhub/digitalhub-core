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

package it.smartcommunitylabdhub.runtime.openinference.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.runtime.openinference.OpeninferenceRuntime;
import it.smartcommunitylabdhub.runtime.openinference.model.TensorModel;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = OpeninferenceRuntime.RUNTIME, kind = OpeninferenceRuntime.RUNTIME, entity = Function.class)
public class OpeninferenceFunctionSpec extends PythonFunctionSpec {

    @JsonProperty("model_name")
    @NotNull
    @Schema(title = "fields.openinference.modelname.title", description = "fields.openinference.modelname.description")
    private String modelName;

    @Schema(title = "fields.openinference.inputs.title", description = "fields.openinference.inputs.description")
    List<TensorModel> inputs;
    @Schema(title = "fields.openinference.outputs.title", description = "fields.openinference.outputs.description")
    List<TensorModel> outputs;

    public OpeninferenceFunctionSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        OpeninferenceFunctionSpec spec = mapper.convertValue(data, OpeninferenceFunctionSpec.class);

        this.modelName = spec.getModelName();
        this.inputs = spec.getInputs();
        this.outputs = spec.getOutputs();
    }
}
