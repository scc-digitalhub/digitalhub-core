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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Signature {

    @Schema(title = "fields.mlflow.signatureinputs.title", description = "fields.mlflow.signatureinputs.description")
    private String inputs;

    @Schema(title = "fields.mlflow.signatureoutputs.title", description = "fields.mlflow.signatureoutputs.description")
    private String outputs;

    @Schema(title = "fields.mlflow.signatureparams.title", description = "fields.mlflow.signatureparams.description")
    private String params;
}
