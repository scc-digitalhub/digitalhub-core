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

import it.smartcommunitylabdhub.commons.annotations.common.EffectType;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.logs.LogStore;
import it.smartcommunitylabdhub.runs.Run;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@EffectType(stages = { "onDeleting" }, type = Run.class)
@Component
@Slf4j
public class RunLogsFinalizer implements Effect<Run> {

    private LogStore logStore;

    //TODO remove metrics

    @Autowired(required = false)
    public void setLogStore(LogStore logStore) {
        this.logStore = logStore;
    }

    @Override
    public void process(String stage, Run run, Serializable output) {
        try {
            cleanup(run);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage(), e);
        }
    }

    private void cleanup(@NotNull Run run) throws StoreException {
        log.debug("finalize run with id {}", run.getId());

        String id = run.getId();

        if (logStore != null) {
            log.debug("cascade delete logs for run with id {}", String.valueOf(id));
            logStore.deleteLogsByRunId(id);
        }
    }
}
