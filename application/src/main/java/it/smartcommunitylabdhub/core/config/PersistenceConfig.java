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
import it.smartcommunitylabdhub.core.models.entities.ArtifactEntity;
import it.smartcommunitylabdhub.core.models.entities.DataItemEntity;
import it.smartcommunitylabdhub.core.models.entities.FunctionEntity;
import it.smartcommunitylabdhub.core.models.entities.LogEntity;
import it.smartcommunitylabdhub.core.models.entities.ModelEntity;
import it.smartcommunitylabdhub.core.models.entities.ProjectEntity;
import it.smartcommunitylabdhub.core.models.entities.RunEntity;
import it.smartcommunitylabdhub.core.models.entities.SecretEntity;
import it.smartcommunitylabdhub.core.models.entities.TaskEntity;
import it.smartcommunitylabdhub.core.models.entities.TriggerEntity;
import it.smartcommunitylabdhub.core.models.entities.WorkflowEntity;
import it.smartcommunitylabdhub.core.repositories.ArtifactRepository;
import it.smartcommunitylabdhub.core.repositories.DataItemRepository;
import it.smartcommunitylabdhub.core.repositories.FunctionRepository;
import it.smartcommunitylabdhub.core.repositories.LogRepository;
import it.smartcommunitylabdhub.core.repositories.ModelRepository;
import it.smartcommunitylabdhub.core.repositories.ProjectRepository;
import it.smartcommunitylabdhub.core.repositories.RunRepository;
import it.smartcommunitylabdhub.core.repositories.SecretRepository;
import it.smartcommunitylabdhub.core.repositories.TaskRepository;
import it.smartcommunitylabdhub.core.repositories.TriggerRepository;
import it.smartcommunitylabdhub.core.repositories.WorkflowRepository;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import it.smartcommunitylabdhub.core.services.EntityService;
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
