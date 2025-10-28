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

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeServeTaskSpec;
import it.smartcommunitylabdhub.runtime.vllm.VLLMServeRuntime;

import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = VLLMServeRuntime.RUNTIME, kind = VLLMServeTaskSpec.KIND, entity = EntityName.TASK)
public class VLLMServeTaskSpec extends ModelServeServeTaskSpec {

    public static final String KIND = VLLMServeRuntime.RUNTIME + "+serve";


    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
    }

    public static VLLMServeTaskSpec with(Map<String, Serializable> data) {
        VLLMServeTaskSpec spec = new VLLMServeTaskSpec();
        spec.configure(data);
        return spec;
    }
}
