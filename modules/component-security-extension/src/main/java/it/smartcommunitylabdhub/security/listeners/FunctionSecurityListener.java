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

package it.smartcommunitylabdhub.security.listeners;

import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.functions.persistence.FunctionEntity;
import it.smartcommunitylabdhub.security.service.SecurityAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.Assert;

/**
 * Triggers a security analysis whenever a new function (or function version) is created.
 */
@Component
@Slf4j
public class FunctionSecurityListener {

    private final SecurityAnalysisService analysisService;
    private final Converter<FunctionEntity, Function> converter;

    public FunctionSecurityListener(
        SecurityAnalysisService analysisService,
        Converter<FunctionEntity, Function> converter
    ) {
        Assert.notNull(analysisService, "analysis service is required");
        Assert.notNull(converter, "converter is required");
        this.analysisService = analysisService;
        this.converter = converter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void receive(EntityEvent<FunctionEntity> event) {
        if (!analysisService.isEnabled() || event.getEntity() == null || event.getAction() != EntityAction.CREATE) {
            return;
        }

        try {
            Function function = converter.convert(event.getEntity());
            log.debug("new function {}, trigger security analysis", event.getEntity().getId());
            analysisService.analyze(function);
        } catch (Exception e) {
            log.error("error triggering security analysis for {}: {}", event.getEntity().getId(), e.getMessage());
        }
    }
}
