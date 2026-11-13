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

package it.smartcommunitylabdhub.runtime.ollama.specs;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.runtime.modelserve.specs.ModelServeServeTaskSpec;
import it.smartcommunitylabdhub.runtime.ollama.OllamaServeRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = OllamaServeRuntime.RUNTIME, kind = OllamaServeTaskSpec.KIND, entity = Task.class)
public class OllamaServeTaskSpec extends ModelServeServeTaskSpec {

    public static final String KIND = OllamaServeRuntime.RUNTIME + "+serve";

    public static OllamaServeTaskSpec with(Map<String, Serializable> data) {
        OllamaServeTaskSpec spec = new OllamaServeTaskSpec();
        spec.configure(data);
        return spec;
    }
}
