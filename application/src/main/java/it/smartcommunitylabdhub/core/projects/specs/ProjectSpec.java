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

package it.smartcommunitylabdhub.core.projects.specs;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.project.ProjectBaseSpec;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SpecType(kind = "project", entity = EntityName.PROJECT)
public class ProjectSpec extends ProjectBaseSpec {

    private List<Function> functions = new ArrayList<>();

    private List<Artifact> artifacts = new ArrayList<>();

    private List<Workflow> workflows = new ArrayList<>();

    private List<DataItem> dataitems = new ArrayList<>();

    private List<Model> models = new ArrayList<>();

    @Override
    public void configure(Map<String, Serializable> data) {
        ProjectSpec spec = mapper.convertValue(data, ProjectSpec.class);

        this.functions = spec.getFunctions();
        this.artifacts = spec.getArtifacts();
        this.dataitems = spec.getDataitems();
        this.models = spec.getModels();
        this.workflows = spec.getWorkflows();

        super.configure(data);
    }
}
