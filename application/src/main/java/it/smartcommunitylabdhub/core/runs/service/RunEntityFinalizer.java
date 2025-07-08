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

package it.smartcommunitylabdhub.core.runs.service;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.core.services.EntityFinalizer;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RunEntityFinalizer implements EntityFinalizer<Run>, InitializingBean {

    private LogService logService;

    //TODO remove metrics

    @Autowired(required = false)
    public void setLogService(LogService logService) {
        this.logService = logService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {}

    @Override
    public void finalize(@NotNull Run run) throws StoreException {
        log.debug("finalize run with id {}", run.getId());

        String id = run.getId();

        if (logService != null) {
            log.debug("cascade delete logs for run with id {}", String.valueOf(id));
            logService.deleteLogsByRunId(id);
        }
    }
}
