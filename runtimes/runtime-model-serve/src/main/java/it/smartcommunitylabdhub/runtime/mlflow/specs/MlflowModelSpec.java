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
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.model.ModelBaseSpec;
import it.smartcommunitylabdhub.runtime.mlflow.models.Dataset;
import it.smartcommunitylabdhub.runtime.mlflow.models.Signature;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = "mlflow", entity = EntityName.MODEL)
public class MlflowModelSpec extends ModelBaseSpec {

    @JsonProperty("parameters")
    private Map<String, Serializable> parameters = new LinkedHashMap<>();

    @Schema(title = "fields.mlflow.flavor.title", description = "fields.mlflow.flavor.description")
    private String flavor;

    @JsonProperty("model_config")
    @Schema(title = "fields.mlflow.modelconfig.title", description = "fields.mlflow.modelconfig.description")
    private Map<String, String> modelConfig;

    @JsonProperty("input_datasets")
    @Schema(title = "fields.mlflow.inputdatasets.title", description = "fields.mlflow.inputdatasets.description")
    private List<Dataset> inputDatasets;

    @Schema(title = "fields.mlflow.signature.title", description = "fields.mlflow.signature.description")
    private Signature signature;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        MlflowModelSpec spec = mapper.convertValue(data, MlflowModelSpec.class);

        this.parameters = spec.getParameters();
        this.flavor = spec.getFlavor();
        this.signature = spec.getSignature();
        this.inputDatasets = spec.getInputDatasets();
        this.modelConfig = spec.getModelConfig();
    }
}
