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
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.FunctionBaseSpec;
import it.smartcommunitylabdhub.runtime.mlflow.MlflowServeRuntime;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SpecType(runtime = MlflowServeRuntime.RUNTIME, kind = MlflowServeRuntime.RUNTIME, entity = EntityName.FUNCTION)
public class MlflowServeFunctionSpec extends FunctionBaseSpec {

    @JsonProperty("path")
    @NotNull
    @Pattern(regexp = "^(store://([^/]+)/model/mlflow/.*)" + "|" + Keys.FOLDER_PATTERN + "|" + Keys.ZIP_PATTERN)
    @Schema(title = "fields.path.title", description = "fields.mlflow.path.description")
    private String path;

    @JsonProperty("model_name")
    @Schema(
        title = "fields.modelserve.modelname.title",
        description = "fields.modelserve.modelname.description",
        defaultValue = "model"
    )
    private String modelName;

    @JsonProperty("image")
    @Pattern(regexp = "^seldonio\\/mlserver?:.*-mlflow$")
    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        MlflowServeFunctionSpec spec = mapper.convertValue(data, MlflowServeFunctionSpec.class);
        this.modelName = spec.getModelName();
        this.path = spec.getPath();
        this.image = spec.getImage();
    }

    public static MlflowServeFunctionSpec with(Map<String, Serializable> data) {
        MlflowServeFunctionSpec spec = new MlflowServeFunctionSpec();
        spec.configure(data);
        return spec;
    }
}
