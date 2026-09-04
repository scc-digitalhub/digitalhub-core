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
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.events.EntityOperation;
import it.smartcommunitylabdhub.extensions.ExtensionManager;
import it.smartcommunitylabdhub.extensions.persistence.ExtensionBuilder;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.lifecycle.RunState;
import it.smartcommunitylabdhub.runs.listeners.RunOperationsListener;
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

@EffectType(stages = { "onRunning" }, type = Run.class)
@Component
@Slf4j
public class RunAutoscalerProcessor implements Effect<Run> {

    @Autowired(required = false)
    private ExtensionManager extManager;

    @Autowired(required = false)
    private RunOperationsListener runOperations;

    @Override
    public K8sRunnable process(String stage, Run run, Serializable input) throws CoreRuntimeException {
        if (run == null) {
            return null;
        }

        if ((run.getExtensions() == null || run.getExtensions().isEmpty()) && extManager == null) {
            return null;
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
                                log.debug("Run {} has exceeded auto stop time, marking for deletion", run.getId());

                                if (runOperations != null) {
                                    //set stop and dispatch, will trigger transition to STOPPED
                                    run.setStatus(
                                        MapUtils.mergeMultipleMaps(
                                            run.getStatus(),
                                            Map.of("state", RunState.STOP.name())
                                        )
                                    );
                                    runOperations.dispatch(new EntityOperation<Run>(run, EntityAction.UPDATE));
                                }

                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error processing extensions for {}: {}", run.getId(), e.getMessage());
        }

        //no output, let the chain continue
        return null;
    }
}
