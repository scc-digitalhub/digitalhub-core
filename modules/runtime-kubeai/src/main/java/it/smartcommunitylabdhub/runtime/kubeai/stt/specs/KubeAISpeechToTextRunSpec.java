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

package it.smartcommunitylabdhub.runtime.kubeai.stt.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.runtime.kubeai.base.KubeAIServeRunSpec;
import it.smartcommunitylabdhub.runtime.kubeai.stt.KubeAISpeechToTextRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KubeAISpeechToTextRuntime.RUNTIME, kind = KubeAISpeechToTextRunSpec.KIND, entity = EntityName.RUN)
public class KubeAISpeechToTextRunSpec extends KubeAIServeRunSpec {

    public static final String KIND = KubeAISpeechToTextServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private KubeAISpeechToTextFunctionSpec functionSpec;

    @JsonUnwrapped
    private KubeAISpeechToTextServeTaskSpec taskServeSpec;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        KubeAISpeechToTextRunSpec spec = mapper.convertValue(data, KubeAISpeechToTextRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
    }

    public void setFunctionSpec(KubeAISpeechToTextFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(KubeAISpeechToTextServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static KubeAISpeechToTextRunSpec with(Map<String, Serializable> data) {
        KubeAISpeechToTextRunSpec spec = new KubeAISpeechToTextRunSpec();
        spec.configure(data);
        return spec;
    }
}
