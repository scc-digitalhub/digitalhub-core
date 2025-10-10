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

package it.smartcommunitylabdhub.triggers.lifecycle;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.services.RunManager;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.commons.services.TemplateProcessor;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.lifecycle.BaseLifecycleManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunEvent;
import it.smartcommunitylabdhub.triggers.infrastructure.Actuator;
import it.smartcommunitylabdhub.triggers.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.triggers.models.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec;
import it.smartcommunitylabdhub.triggers.specs.TriggerBaseStatus;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Slf4j
public class TriggerLifecycleManager<
    S extends TriggerBaseSpec, Z extends TriggerBaseStatus, R extends TriggerRunBaseStatus
>
    extends BaseLifecycleManager<Trigger, TriggerState, TriggerEvent> {

    private TaskService taskService;
    private RunManager runService;
    private LifecycleManager<Run> runManager;
    private TemplateProcessor templateProcessor;

    public TriggerLifecycleManager(TriggerFsmFactory<S, Z, R> fsmFactory) {
        this.setFsmFactory(fsmFactory);
    }

    public TriggerLifecycleManager(Actuator<S, Z, R> actuator) {
        this.setFsmFactory(new TriggerFsmFactory<>(actuator));
    }

    @Autowired
    public void setTaskService(TaskService taskService) {
        this.taskService = taskService;
    }

    @Autowired
    public void setRunService(RunManager runService) {
        this.runService = runService;
    }

    @Autowired
    public void setRunManager(LifecycleManager<Run> runManager) {
        this.runManager = runManager;
    }

    @Autowired(required = false)
    public void setTemplateProcessor(TemplateProcessor templateProcessor) {
        this.templateProcessor = templateProcessor;
    }

    @Override
    public <I, RT> Trigger perform(
        @NotNull Trigger tr,
        @NotNull String event,
        I input,
        BiConsumer<Trigger, RT> effect
    ) {
        if (effect == null && TriggerEvent.FIRE.name().equals(event) && input instanceof TriggerRun<?>) {
            // on fire, we will produce a new run
            effect =
                (trigger, ret) -> {
                    TriggerRun<?> run = (TriggerRun<?>) input;

                    //by default we expect a partial status for the new run
                    Optional<TriggerRunBaseStatus> status = Optional.ofNullable(
                        ret instanceof TriggerRunBaseStatus ? (TriggerRunBaseStatus) ret : null
                    );

                    TriggerRunBaseStatus trRunStatus = TriggerRunBaseStatus.builder().trigger(run).build();

                    Map<String, Serializable> actuatorRunStatus = status.isPresent() ? status.get().toMap() : null;
                    Map<String, Serializable> triggerRunStatus = MapUtils.mergeMultipleMaps(
                        actuatorRunStatus,
                        trRunStatus.toMap()
                    );

                    log.debug("build run from template for trigger {}", tr.getId());

                    //access task details from ref, same as run
                    RunSpecAccessor specAccessor = RunSpecAccessor.with(tr.getSpec());
                    if (!StringUtils.hasText(specAccessor.getTaskId())) {
                        throw new IllegalArgumentException("spec: invalid task");
                    }

                    //fetch for build
                    TriggerBaseSpec baseSpec = TriggerBaseSpec.from(tr.getSpec());
                    Task task = taskService.getTask(specAccessor.getTaskId());

                    //build meta
                    List<RelationshipDetail> rels = new ArrayList<>();
                    rels.add(new RelationshipDetail(RelationshipName.PRODUCEDBY, null, tr.getKey()));
                    if (run.getJob().getRelationships() != null) {
                        rels.addAll(run.getJob().getRelationships());
                    }

                    RelationshipsMetadata relMetadata = RelationshipsMetadata.builder().relationships(rels).build();

                    //status
                    RunBaseStatus baseStatus = RunBaseStatus.baseBuilder().state(State.CREATED.name()).build();
                    Map<String, Serializable> runStatus = MapUtils.mergeMultipleMaps(
                        triggerRunStatus,
                        baseStatus.toMap()
                    );

                    //build either function or workflow run
                    Map<String, Serializable> addSpec = StringUtils.hasText(baseSpec.getFunction())
                        ? Map.of(Fields.FUNCTION, baseSpec.getFunction(), Fields.TASK, baseSpec.getTask())
                        : Map.of(Fields.WORKFLOW, baseSpec.getWorkflow(), Fields.TASK, baseSpec.getTask());

                    //TODO validate spec against task spec

                    //build template
                    Map<String, Serializable> template = baseSpec.getTemplate();

                    if (templateProcessor != null) {
                        try {
                            //process template with details
                            template = templateProcessor.process(baseSpec.getTemplate(), run.getDetails());
                        } catch (IOException e) {
                            log.error(null, e);
                            throw new RuntimeException("error processing template", e);
                        }
                    }

                    //build run from trigger template
                    String runKind = specAccessor.getTask() + ":run"; //TODO hardcoded, to fix
                    Run taskRun = Run
                        .builder()
                        .kind(runKind)
                        .project(tr.getProject())
                        .user(run.getUser())
                        .spec(MapUtils.mergeMultipleMaps(template, addSpec))
                        .metadata(relMetadata.toMap())
                        .status(runStatus)
                        .build();

                    if (log.isTraceEnabled()) {
                        log.trace("built run: {}", taskRun);
                    }

                    try {
                        //create run via service as CREATED
                        taskRun = runService.createRun(taskRun);

                        //build now
                        taskRun = runManager.perform(taskRun, RunEvent.BUILD.name());

                        //dispatch for run
                        //TODO evaluate detaching via async
                        taskRun = runManager.perform(taskRun, RunEvent.RUN.name());
                    } catch (NoSuchEntityException | SystemException | DuplicatedEntityException | BindException e) {
                        log.error("Error creating run for trigger {} FIRE: {} ", tr.getId(), e.getMessage());
                    }
                };
        }

        return super.perform(tr, event, input, effect);
    }
}
