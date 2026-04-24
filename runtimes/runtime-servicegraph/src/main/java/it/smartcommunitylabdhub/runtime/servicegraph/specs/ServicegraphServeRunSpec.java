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

package it.smartcommunitylabdhub.runtime.servicegraph.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.servicegraph.ServicegraphRuntime;

import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = ServicegraphRuntime.RUNTIME, kind = ServicegraphServeRunSpec.KIND, entity = Run.class)
public final class ServicegraphServeRunSpec extends ServicegraphRunSpec {

    public static final String KIND = ServicegraphServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private ServicegraphFunctionSpec functionSpec;

    @JsonUnwrapped
    private ServicegraphServeTaskSpec taskServeSpec;

    public ServicegraphServeRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        ServicegraphServeRunSpec spec = mapper.convertValue(data, ServicegraphServeRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
    }

    public void setFunctionSpec(ServicegraphFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(ServicegraphServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }
}
