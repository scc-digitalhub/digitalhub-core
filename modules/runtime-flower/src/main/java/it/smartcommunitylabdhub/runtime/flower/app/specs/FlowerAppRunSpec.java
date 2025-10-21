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

package it.smartcommunitylabdhub.runtime.flower.app.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.flower.app.FlowerAppRuntime;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = FlowerAppRuntime.RUNTIME, kind = FlowerAppRunSpec.KIND, entity = EntityName.RUN)
public class FlowerAppRunSpec extends RunBaseSpec {

    public static final String KIND = FlowerAppTrainTaskSpec.KIND + ":run";

    @JsonUnwrapped
    private FlowerAppTrainTaskSpec taskTrainSpec;

    @JsonSchemaIgnore
    @JsonUnwrapped
    private FlowerAppFunctionSpec functionSpec;

    private Map<String, Serializable> parameters = new HashMap<>();

    @Schema(title = "fields.flower.federation.title", description = "fields.flower.federation.description")
    private String federation;

    @Schema(title = "fields.flower.superlink.title", description = "fields.flower.superlink.description")
    private String superlink;

    @JsonProperty("root_certificates")
    @Schema(
        title = "fields.flower.root_certificates.title",
        description = "fields.flower.root_certificates.description"
    )
    private String rootCertificates;

    public FlowerAppRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FlowerAppRunSpec spec = mapper.convertValue(data, FlowerAppRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();

        this.taskTrainSpec = spec.getTaskTrainSpec();

        this.parameters = spec.getParameters();
        this.federation = spec.getFederation();
        this.superlink = spec.getSuperlink();
        this.rootCertificates = spec.getRootCertificates();
    }

    public void setTaskTrainSpec(FlowerAppTrainTaskSpec taskTrainSpec) {
        this.taskTrainSpec = taskTrainSpec;
    }

    public void setFunctionSpec(FlowerAppFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }
}
