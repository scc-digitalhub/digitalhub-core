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

package it.smartcommunitylabdhub.autoscaler.spec;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.extensions.annotations.ExtensionType;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.runs.Run;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@SpecType(kind = RunAutoscalerSpec.KIND, entity = Extension.class, uiSchema = "autoscaler/uiSchema.json")
@ExtensionType(appliesTo = { Run.class })
public class RunAutoscalerSpec extends BaseSpec {

    public static final String KIND = "autoscaler";

    // auto stop in seconds, if null no auto stop
    @JsonProperty("auto_stop")
    @Schema(title = "Auto Stop", description = "Time in seconds after which the run will be automatically stopped")
    private Integer autoStop;

    @Override
    public void configure(Map<String, Serializable> data) {
        RunAutoscalerSpec spec = mapper.convertValue(data, RunAutoscalerSpec.class);
        this.autoStop = spec.getAutoStop();
    }

    public static RunAutoscalerSpec with(Map<String, Serializable> data) {
        RunAutoscalerSpec spec = new RunAutoscalerSpec();
        spec.configure(data);

        return spec;
    }
}
