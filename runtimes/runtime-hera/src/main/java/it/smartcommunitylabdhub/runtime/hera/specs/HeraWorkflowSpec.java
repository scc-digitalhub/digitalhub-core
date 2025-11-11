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

package it.smartcommunitylabdhub.runtime.hera.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.commons.models.workflow.WorkflowBaseSpec;
import it.smartcommunitylabdhub.runtime.hera.HeraRuntime;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = HeraRuntime.RUNTIME, kind = HeraRuntime.RUNTIME, entity = EntityName.WORKFLOW)
public class HeraWorkflowSpec extends WorkflowBaseSpec {

    @NotNull
    @Schema(title = "fields.sourceCode.title", description = "fields.sourceCode.description")
    private SourceCode<HeraSourceCodeLanguages> source;

    @Schema(title = "fields.container.image.title", description = "fields.container.image.description")
    private String image;

    @Schema(title = "fields.container.tag.title", description = "fields.container.tag.description")
    private String tag;

    @JsonSchemaIgnore
    private SourceCode<HeraWorkflowCodeLanguages> build;

    public HeraWorkflowSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        HeraWorkflowSpec spec = mapper.convertValue(data, HeraWorkflowSpec.class);

        this.source = spec.getSource();
        this.image = spec.getImage();
        this.tag = spec.getTag();
        this.build = spec.getBuild();
    }

    public enum HeraSourceCodeLanguages {
        python,
    }

    public enum HeraWorkflowCodeLanguages {
        yaml,
    }
}
