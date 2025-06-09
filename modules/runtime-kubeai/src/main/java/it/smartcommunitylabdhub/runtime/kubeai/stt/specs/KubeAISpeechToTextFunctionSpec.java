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

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.runtime.kubeai.base.KubeAIServeFunctionSpec;
import it.smartcommunitylabdhub.runtime.kubeai.stt.KubeAISpeechToTextRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@SpecType(
    runtime = KubeAISpeechToTextRuntime.RUNTIME,
    kind = KubeAISpeechToTextRuntime.RUNTIME,
    entity = EntityName.FUNCTION
)
public class KubeAISpeechToTextFunctionSpec extends KubeAIServeFunctionSpec {

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
    }

    public static KubeAISpeechToTextFunctionSpec with(Map<String, Serializable> data) {
        KubeAISpeechToTextFunctionSpec spec = new KubeAISpeechToTextFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
