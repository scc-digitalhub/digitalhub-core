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

package it.smartcommunitylabdhub.core.config;

import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.log.Log;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.secret.Secret;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.core.artifacts.persistence.ArtifactEntity;
import it.smartcommunitylabdhub.core.artifacts.persistence.ArtifactRepository;
import it.smartcommunitylabdhub.core.dataitems.persistence.DataItemEntity;
import it.smartcommunitylabdhub.core.dataitems.persistence.DataItemRepository;
import it.smartcommunitylabdhub.core.functions.persistence.FunctionEntity;
import it.smartcommunitylabdhub.core.functions.persistence.FunctionRepository;
import it.smartcommunitylabdhub.core.logs.persistence.LogEntity;
import it.smartcommunitylabdhub.core.logs.persistence.LogRepository;
import it.smartcommunitylabdhub.core.models.persistence.ModelEntity;
import it.smartcommunitylabdhub.core.models.persistence.ModelRepository;
import it.smartcommunitylabdhub.core.projects.persistence.ProjectEntity;
import it.smartcommunitylabdhub.core.projects.persistence.ProjectRepository;
import it.smartcommunitylabdhub.core.runs.persistence.RunEntity;
import it.smartcommunitylabdhub.core.runs.persistence.RunRepository;
import it.smartcommunitylabdhub.core.secrets.persistence.SecretEntity;
import it.smartcommunitylabdhub.core.secrets.persistence.SecretRepository;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import it.smartcommunitylabdhub.core.services.EntityService;
import it.smartcommunitylabdhub.core.tasks.persistence.TaskEntity;
import it.smartcommunitylabdhub.core.tasks.persistence.TaskRepository;
import it.smartcommunitylabdhub.core.triggers.persistence.TriggerEntity;
import it.smartcommunitylabdhub.core.triggers.persistence.TriggerRepository;
import it.smartcommunitylabdhub.core.workflows.persistence.WorkflowEntity;
import it.smartcommunitylabdhub.core.workflows.persistence.WorkflowRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;

@Configuration
@Order(2)
public class PersistenceConfig {

    @Bean
    public EntityService<Project, ProjectEntity> projectEntityService(
        ProjectRepository repository,
        Converter<Project, ProjectEntity> entityBuilder,
        Converter<ProjectEntity, Project> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    @Bean
    public EntityService<Artifact, ArtifactEntity> artifactEntityService(
        ArtifactRepository repository,
        Converter<Artifact, ArtifactEntity> entityBuilder,
        Converter<ArtifactEntity, Artifact> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    @Bean
    public EntityService<DataItem, DataItemEntity> dataItemEntityService(
        DataItemRepository repository,
        Converter<DataItem, DataItemEntity> entityBuilder,
        Converter<DataItemEntity, DataItem> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    @Bean
    public EntityService<Model, ModelEntity> modelEntityService(
        ModelRepository repository,
        Converter<Model, ModelEntity> entityBuilder,
        Converter<ModelEntity, Model> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    @Bean
    public EntityService<Function, FunctionEntity> functionEntityService(
        FunctionRepository repository,
        Converter<Function, FunctionEntity> entityBuilder,
        Converter<FunctionEntity, Function> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    @Bean
    public EntityService<Log, LogEntity> logEntityService(
        LogRepository repository,
        Converter<Log, LogEntity> entityBuilder,
        Converter<LogEntity, Log> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    // @Bean
    // public EntityService<Project, ProjectEntity> projectEntityService(
    //     ProjectRepository repository,
    //     Converter<Project, ProjectEntity> entityBuilder,
    //     Converter<ProjectEntity, Project> dtoBuilder
    // ) {
    //     return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    // }

    @Bean
    public EntityService<Run, RunEntity> runEntityService(
        RunRepository repository,
        Converter<Run, RunEntity> entityBuilder,
        Converter<RunEntity, Run> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    @Bean
    public EntityService<Secret, SecretEntity> secretEntityService(
        SecretRepository repository,
        Converter<Secret, SecretEntity> entityBuilder,
        Converter<SecretEntity, Secret> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    @Bean
    public EntityService<Task, TaskEntity> taskEntityService(
        TaskRepository repository,
        Converter<Task, TaskEntity> entityBuilder,
        Converter<TaskEntity, Task> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    @Bean
    public EntityService<Workflow, WorkflowEntity> workflowEntityService(
        WorkflowRepository repository,
        Converter<Workflow, WorkflowEntity> entityBuilder,
        Converter<WorkflowEntity, Workflow> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }

    @Bean
    public EntityService<Trigger, TriggerEntity> triggerEntityService(
        TriggerRepository repository,
        Converter<Trigger, TriggerEntity> entityBuilder,
        Converter<TriggerEntity, Trigger> dtoBuilder
    ) {
        return new BaseEntityServiceImpl<>(repository, entityBuilder, dtoBuilder);
    }
}
