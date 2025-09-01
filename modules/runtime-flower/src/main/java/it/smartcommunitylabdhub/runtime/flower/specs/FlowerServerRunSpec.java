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

package it.smartcommunitylabdhub.runtime.flower.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.flower.FlowerServerRuntime;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = FlowerServerRuntime.RUNTIME, kind = FlowerServerRunSpec.KIND, entity = EntityName.RUN)
public class FlowerServerRunSpec extends RunBaseSpec {

    public static final String KIND = FlowerServerRuntime.RUNTIME + "+run";

    @JsonUnwrapped
    private FlowerServerTaskSpec taskDeploySpec;

    @JsonUnwrapped
    private FlowerBuildServerTaskSpec taskBuildSpec;

    @JsonSchemaIgnore
    @JsonUnwrapped
    private FlowerServerFunctionSpec functionSpec;

    @JsonProperty("auth_public_keys")
    @Schema(title = "fields.flower.auth_public_keys.title", description = "fields.flower.auth_public_keys.description")
    private List<String> authPublicKeys;

    public FlowerServerRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        FlowerServerRunSpec spec = mapper.convertValue(data, FlowerServerRunSpec.class);

        this.functionSpec = spec.getFunctionSpec();

        this.taskDeploySpec = spec.getTaskDeploySpec();
        this.taskBuildSpec = spec.getTaskBuildSpec();

        this.authPublicKeys = spec.getAuthPublicKeys();
    }

    public void setTaskDeploySpec(FlowerServerTaskSpec taskDeploySpec) {
        this.taskDeploySpec = taskDeploySpec;
    }

    public void setTaskBuildSpec(FlowerBuildServerTaskSpec taskBuildSpec) {
        this.taskBuildSpec = taskBuildSpec;
    }

    public void setFunctionSpec(FlowerServerFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }
}
