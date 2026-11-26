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

package it.smartcommunitylabdhub.autoscaler;

import it.smartcommunitylabdhub.autoscaler.spec.RunAutoscalerSpec;
import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.extensions.ExtensionManager;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionBuilder;
import it.smartcommunitylabdhub.fsm.exceptions.InvalidTransitionException;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.RunManager;
import it.smartcommunitylabdhub.runs.lifecycle.RunEvent;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import it.smartcommunitylabdhub.runs.specs.RunTransitionsSpec;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;

@EffectType(stages = { "onRunning", "onError" }, type = Run.class)
@Component
@Slf4j
public class RunAutoscalerProcessor implements Effect<Run> {

    @Autowired(required = false)
    private ExtensionManager extManager;

    @Autowired(required = false)
    private RunManager runManager;

    @Override
    public void process(String stage, Run run, Serializable output) throws CoreRuntimeException {
        if (run == null) {
            return;
        }

        if ((run.getExtensions() == null || run.getExtensions().isEmpty()) && extManager == null) {
            return;
        }

        try {
            //extensions are either inlined or we fetch
            List<RunAutoscalerSpec> specs = new ArrayList<>();

            if (run.getExtensions() != null) {
                specs.addAll(
                    run
                        .getExtensions()
                        .stream()
                        .filter(ext -> RunAutoscalerSpec.KIND.equals(ext.get("kind")) && ext.get("spec") instanceof Map)
                        .map(ext -> RunAutoscalerSpec.with((Map<String, Serializable>) ext.get("spec")))
                        .toList()
                );
            }

            if (specs.isEmpty() && extManager != null) {
                String parentId = ExtensionBuilder.from(run).getParent();
                extManager
                    .listExtensionsByParent(parentId, RunAutoscalerSpec.KIND)
                    .forEach(ext -> specs.add(RunAutoscalerSpec.with(ext.getSpec())));
            }

            if (!specs.isEmpty()) {
                log.debug("Processing run {} with {} extensions", run.getId(), specs.size());

                switch (stage) {
                    case "onRunning" -> onRunning(run, specs);
                    case "onError" -> onError(run, specs);
                    default -> {
                        //ignore
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing extensions for {}: {}", run.getId(), e.getMessage());
        }

        //side effect only, no output
    }

    private void onRunning(Run run, List<RunAutoscalerSpec> specs) {
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);

        // evaluate auto shutdown from extensions, if any
        // start date is fetched from transition time to running, if not available we use creation date
        BaseMetadata metadata = BaseMetadata.from(run.getMetadata());
        RunTransitionsSpec rts = RunTransitionsSpec.from(run.getStatus());
        OffsetDateTime start =
            rts.getTransitions() != null
                ? rts
                      .getTransitions()
                      .stream()
                      .filter(t -> "RUNNING".equals(t.getStatus()))
                      .findFirst()
                      .map(t -> t.getTime())
                      .orElse(metadata.getCreated())
                : metadata.getCreated();

        if (start != null) {
            for (RunAutoscalerSpec spec : specs) {
                Integer autoStop = spec.getAutoStop();
                if (autoStop != null && autoStop > 0) {
                    OffsetDateTime stopTime = start.plusSeconds(autoStop);
                    if (now.isAfter(stopTime)) {
                        log.debug("Run {} has exceeded auto stop time, stopping", run.getId());

                        if (runManager != null) {
                            //request STOP via lifecycle: only the state changes, via FSM
                            try {
                                runManager.perform(run, RunEvent.STOP.name());
                            } catch (InvalidTransitionException e) {
                                //already stopping or no longer running
                                log.debug("Run {} can not be stopped: {}", run.getId(), e.getMessage());
                            }
                        }

                        break;
                    }
                }
            }
        }
    }

    private void onError(Run run, List<RunAutoscalerSpec> specs) {
        OffsetDateTime now = OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        //TODO implement exponential backoff for auto restart

        for (RunAutoscalerSpec spec : specs) {
            Boolean autoRestart = spec.getAutoRestart();
            if (autoRestart != null && autoRestart) {
                log.debug("Run {} is in error state and auto restart is enabled", run.getId());
                if (runManager != null) {
                    //fully clone the run
                    Run clonedRun = Run.builder()
                        .id(null)
                        .kind(run.getKind())
                        .name(null)
                        .project(run.getProject())
                        .user(run.getUser())
                        .metadata(run.getMetadata())
                        .spec(run.getSpec())
                        .status(run.getStatus())
                        .extensions(run.getExtensions())
                        .build();

                    //create as BUILT (the service walks the lifecycle CREATED -> BUILT), then run
                    clonedRun.setStatus(Map.of("state", RunState.BUILT.name()));
                    try {
                        Run created = runManager.create(clonedRun);
                        runManager.perform(created, RunEvent.RUN.name());
                    } catch (
                        NoSuchEntityException
                        | SystemException
                        | DuplicatedEntityException
                        | BindException
                        | InvalidTransitionException
                        | IllegalArgumentException
                        | StoreException e
                    ) {
                        log.error("Error restarting run {}: {}", run.getId(), e.getMessage());
                    }
                }
                break;
            }
        }
    }
}
