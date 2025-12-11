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

package it.smartcommunitylabdhub.runtime.vllm.speech.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.runtime.vllm.base.specs.VLLMServeRunSpec;
import it.smartcommunitylabdhub.runtime.vllm.speech.VLLMServeSpeechRuntime;

import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = VLLMServeSpeechRuntime.RUNTIME, kind = VLLMServeSpeechRunSpec.KIND, entity = EntityName.RUN)
public class VLLMServeSpeechRunSpec extends VLLMServeRunSpec {

    public static final String KIND = VLLMServeSpeechServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private VLLMServeSpeechFunctionSpec functionSpec;

    @JsonUnwrapped
    private VLLMServeSpeechServeTaskSpec taskServeSpec;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        VLLMServeSpeechRunSpec spec = mapper.convertValue(data, VLLMServeSpeechRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
    }

    public void setFunctionSpec(VLLMServeSpeechFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(VLLMServeSpeechServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static VLLMServeSpeechRunSpec with(Map<String, Serializable> data) {
        VLLMServeSpeechRunSpec spec = new VLLMServeSpeechRunSpec();
        spec.configure(data);
        return spec;
    }
}
