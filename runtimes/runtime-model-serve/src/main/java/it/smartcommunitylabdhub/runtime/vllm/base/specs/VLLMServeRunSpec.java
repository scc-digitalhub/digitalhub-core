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

package it.smartcommunitylabdhub.runtime.vllm.base.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class VLLMServeRunSpec extends RunBaseSpec {

    @Schema(title = "fields.vllm.args.title", description = "fields.vllm.args.description")
    private List<String> args;

    @Schema(title = "fields.vllm.enableTelemetry.title", description = "fields.vllm.enableTelemetry.description")
    @JsonProperty("enable_telemetry")
    private Boolean enableTelemetry;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        VLLMServeRunSpec spec = mapper.convertValue(data, VLLMServeRunSpec.class);
        this.args = spec.getArgs();
        this.enableTelemetry = spec.getEnableTelemetry();
    }

    public static VLLMServeRunSpec with(Map<String, Serializable> data) {
        VLLMServeRunSpec spec = new VLLMServeRunSpec();
        spec.configure(data);
        return spec;
    }
}
