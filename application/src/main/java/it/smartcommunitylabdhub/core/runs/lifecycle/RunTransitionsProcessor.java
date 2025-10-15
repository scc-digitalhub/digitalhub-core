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

package it.smartcommunitylabdhub.core.runs.lifecycle;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.core.runs.specs.RunTransitionsSpec;
import it.smartcommunitylabdhub.core.runs.specs.RunTransitionsSpec.Transition;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedList;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@ProcessorType(
    stages = {
        "onBuilt",
        "onReady",
        "onPending",
        "onInitializing",
        "onRunning",
        "onCompleted",
        "onError",
        "onStopping",
        "onStopped",
        "onResuming",
        "onDeleting",
        "onDeleted",
    },
    type = Run.class,
    spec = Status.class
)
@Component
@Slf4j
public class RunTransitionsProcessor implements Processor<Run, RunTransitionsSpec> {

    @Override
    public RunTransitionsSpec process(String stage, Run run, Object input) {
        RunBaseStatus status = RunBaseStatus.with(run.getStatus());

        LinkedList<RunTransitionsSpec.Transition> list = new LinkedList<>();

        //keep old in order
        Optional
            .ofNullable(run.getStatus())
            .ifPresent(s -> {
                RunTransitionsSpec rts = new RunTransitionsSpec();
                rts.configure(s);

                if (rts.getTransitions() != null) {
                    list.addAll(rts.getTransitions());
                }
            });

        //current
        Transition tr = RunTransitionsSpec.Transition
            .builder()
            .event(null) //TODO add explicit event
            .status(status != null ? status.getState() : null)
            .message(status != null ? status.getMessage() : null)
            .time(OffsetDateTime.ofInstant(Instant.now(), ZoneOffset.UTC))
            .build();

        if (log.isTraceEnabled()) {
            log.trace("transition for {}: {}", run.getId(), tr);
        }

        //avoid duplicated events
        //TODO depends on receiving event
        //workaround, skip duplicate status
        if (list.isEmpty() || !list.get(0).getStatus().equals(tr.getStatus())) {
            list.addFirst(tr);
        }

        return RunTransitionsSpec.builder().transitions(list).build();
    }
}
