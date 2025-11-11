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

package it.smartcommunitylabdhub.runtime.mlflow.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Dataset {

    @Schema(title = "fields.mlflow.inputdatasetname.title", description = "fields.mlflow.inputdatasetname.description")
    private String name;

    @Schema(
        title = "fields.mlflow.inputdatasetdigest.title",
        description = "fields.mlflow.inputdatasetdigest.description"
    )
    private String digest;

    @Schema(
        title = "fields.mlflow.inputdatasetprofile.title",
        description = "fields.mlflow.inputdatasetprofile.description"
    )
    private String profile;

    @Schema(
        title = "fields.mlflow.inputdatasetschema.title",
        description = "fields.mlflow.inputdatasetschema.description"
    )
    private String schema;

    @Schema(
        title = "fields.mlflow.inputdatasetsource.title",
        description = "fields.mlflow.inputdatasetsource.description"
    )
    private String source;

    @JsonProperty("source_type")
    @Schema(
        title = "fields.mlflow.inputdatasetsourcetype.title",
        description = "fields.mlflow.inputdatasetsourcetype.description"
    )
    private String sourceType;
}
