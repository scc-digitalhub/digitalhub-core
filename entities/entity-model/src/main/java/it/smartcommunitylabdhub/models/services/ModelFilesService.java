/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.models.services;

import it.smartcommunitylabdhub.commons.accessors.spec.ConfigSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.files.base.BaseFilesService;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.projects.Project;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ModelFilesService extends BaseFilesService<Model> {

    private EntityRepository<Project> projectService;

    @Autowired
    public void setProjectService(EntityRepository<Project> projectService) {
        this.projectService = projectService;
    }

    @Override
    protected String resolveBasePath(String project, String name, String id, String filename) throws StoreException {
        Project prj = projectService.get(project);
        ConfigSpecAccessor cfg = ConfigSpecAccessor.with(prj.getSpec());

        return (
            filesService.getDefaultStore(cfg.getConfig()) +
            "/" +
            project +
            "/" +
            EntityUtils.getEntityName(Model.class).toLowerCase()
        );
    }
}
