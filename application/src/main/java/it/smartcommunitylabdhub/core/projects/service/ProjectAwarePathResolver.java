/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.projects.service;

import it.smartcommunitylabdhub.commons.accessors.spec.ConfigSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.files.service.FilesService;
import it.smartcommunitylabdhub.files.service.ProjectPathResolver;
import it.smartcommunitylabdhub.projects.Project;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

/**
 * Project base path from the project configuration: the store configured for the project (or the
 * default one) + "/<project>/".
 *
 * NOTE: changing the store of a project does not move existing files: spec.path stays authoritative
 */
@Component
@Primary
@Slf4j
public class ProjectAwarePathResolver implements ProjectPathResolver {

    private final FilesService filesService;
    //lazy: read the project configuration at call time (cached reads)
    private final ObjectProvider<EntityRepository<Project>> projectRepository;

    public ProjectAwarePathResolver(
        FilesService filesService,
        ObjectProvider<EntityRepository<Project>> projectRepository
    ) {
        Assert.notNull(filesService, "files service is required");
        Assert.notNull(projectRepository, "project repository is required");
        this.filesService = filesService;
        this.projectRepository = projectRepository;
    }

    @Override
    public String resolve(@NotNull String project) {
        Map<String, Serializable> config = null;

        EntityRepository<Project> repository = projectRepository.getIfAvailable();
        if (repository != null) {
            try {
                Project prj = repository.find(project);
                if (prj != null) {
                    config = ConfigSpecAccessor.with(prj.getSpec()).getConfig();
                }
            } catch (StoreException e) {
                log.error("error reading project {} configuration: {}", project, e.getMessage());
            }
        }

        return filesService.getDefaultStore(config) + "/" + project + "/";
    }
}
