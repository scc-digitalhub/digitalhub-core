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

package it.smartcommunitylabdhub.runtime.huggingface.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.huggingface.HuggingfaceServeRuntime;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = HuggingfaceServeRuntime.RUNTIME, kind = HuggingfaceServeRunSpec.KIND, entity = EntityName.RUN)
public class HuggingfaceServeRunSpec extends RunBaseSpec {

    public static final String KIND = HuggingfaceServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private HuggingfaceServeFunctionSpec functionSpec;

    @JsonUnwrapped
    private HuggingfaceServeTaskSpec taskServeSpec;

    @Schema(title = "fields.huggingface.args.title", description = "fields.huggingface.args.description")
    private List<String> args;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        HuggingfaceServeRunSpec spec = mapper.convertValue(data, HuggingfaceServeRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.args = spec.getArgs();
    }

    public void setFunctionSpec(HuggingfaceServeFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(HuggingfaceServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static HuggingfaceServeRunSpec with(Map<String, Serializable> data) {
        HuggingfaceServeRunSpec spec = new HuggingfaceServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
