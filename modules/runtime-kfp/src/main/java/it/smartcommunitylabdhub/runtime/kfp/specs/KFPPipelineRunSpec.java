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

package it.smartcommunitylabdhub.runtime.kfp.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.runtime.kfp.KFPRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KFPRuntime.RUNTIME, kind = KFPPipelineRunSpec.KIND, entity = EntityName.RUN)
public class KFPPipelineRunSpec extends KFPRunSpec {

    public static final String KIND = KFPPipelineTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private KFPWorkflowSpec workflowSpec;

    @JsonUnwrapped
    private KFPPipelineTaskSpec taskPipelineSpec;

    public KFPPipelineRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        KFPPipelineRunSpec spec = mapper.convertValue(data, KFPPipelineRunSpec.class);
        this.workflowSpec = spec.getWorkflowSpec();
        this.taskPipelineSpec = spec.getTaskPipelineSpec();
    }

    public void setWorkflowSpec(KFPWorkflowSpec workflowSpec) {
        this.workflowSpec = workflowSpec;
    }

    public void setTaskPipelineSpec(KFPPipelineTaskSpec taskPipelineSpec) {
        this.taskPipelineSpec = taskPipelineSpec;
    }
}
