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

package it.smartcommunitylabdhub.runtime.mlflow.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.runtime.mlflow.MlflowServeRuntime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = MlflowServeRuntime.RUNTIME, kind = MlflowServeRunSpec.KIND, entity = Run.class)
public class MlflowServeRunSpec extends MlflowRunSpec {

    public static final String KIND = MlflowServeTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private MlflowServeFunctionSpec functionSpec;

    @JsonUnwrapped
    private MlflowServeTaskSpec taskServeSpec;

    @JsonProperty("path")
    @NotNull
    @Pattern(regexp = "^(store://([^/]+)/model/mlflow/.*)" + "|" + Keys.FOLDER_PATTERN + "|" + Keys.ZIP_PATTERN)
    @Schema(title = "fields.path.title", description = "fields.mlflow.path.description")
    private String path;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        MlflowServeRunSpec spec = mapper.convertValue(data, MlflowServeRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskServeSpec = spec.getTaskServeSpec();
        this.path = spec.getPath();
    }

    public void setFunctionSpec(MlflowServeFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskServeSpec(MlflowServeTaskSpec taskServeSpec) {
        this.taskServeSpec = taskServeSpec;
    }

    public static MlflowServeRunSpec with(Map<String, Serializable> data) {
        MlflowServeRunSpec spec = new MlflowServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
