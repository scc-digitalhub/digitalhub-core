/*
 * Copyright 2026 the original author or authors.
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
 */

package it.smartcommunitylabdhub.security.processors;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.security.service.SecurityAnalysisService;
import java.io.Serializable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Propagates the state of security analysis runs to the linked security report extension.
 */
@ProcessorType(
    stages = { "onRunning", "onCompleted", "onError", "onStopped" },
    type = Run.class,
    spec = Status.class
)
@Component
@Slf4j
public class SecurityReportRunProcessor implements Processor<Run, Status> {

    @Autowired
    private SecurityAnalysisService analysisService;

    @Override
    public Status process(String stage, Run dto, Serializable input) throws CoreRuntimeException {
        try {
            String reportId = SecurityAnalysisService.getReportId(dto);
            if (reportId != null) {
                log.debug("run {} for security report {} on stage {}", dto.getId(), reportId, stage);
                analysisService.track(reportId, dto);
            }
        } catch (Exception e) {
            log.error("Error processing run {} for stage {}: {}", dto.getId(), stage, e.getMessage());
        }

        //status is never modified
        return null;
    }
}
