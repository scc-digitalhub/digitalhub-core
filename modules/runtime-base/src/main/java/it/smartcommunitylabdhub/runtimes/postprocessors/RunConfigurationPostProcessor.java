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

package it.smartcommunitylabdhub.runtimes.postprocessors;

import it.smartcommunitylabdhub.commons.annotations.common.PostProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurableRunnable;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.PostProcessor;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.runs.Run;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Injects configurations into every configurable runnable produced by run transitions.
 *
 * Runs on every stage, before any other post processor. Configurations are APPENDED: runtimes can
 * still provide their own specific ones. Failures are explicit and fail the transition.
 */
@Slf4j
@Component
@PostProcessorType(stages = { PostProcessorType.ALL_STAGES }, type = Run.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RunConfigurationPostProcessor implements PostProcessor<Run> {

    private ConfigurationService configurationService;

    @Autowired(required = false)
    public void setConfigurationService(ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    @Override
    public Serializable process(String stage, Run run, Serializable output) throws CoreRuntimeException {
        if (configurationService == null || !(output instanceof ConfigurableRunnable runnable)) {
            return null;
        }

        List<Configuration> configurations;
        try {
            configurations = configurationService.getConfigurations();
        } catch (CoreRuntimeException e) {
            throw e;
        } catch (RuntimeException e) {
            //explicit failure: never dispatch a runnable without its configuration
            throw new CoreRuntimeException("unable to resolve configurations: " + e.getMessage(), e);
        }

        if (configurations == null || configurations.isEmpty()) {
            return null;
        }

        log.debug("inject {} configurations for run {} on stage {}", configurations.size(), run.getId(), stage);

        //append to runtime provided configurations
        List<Configuration> merged = new ArrayList<>();
        if (runnable.getConfigurations() != null) {
            merged.addAll(runnable.getConfigurations());
        }
        merged.addAll(configurations);
        runnable.setConfigurations(merged);

        return runnable;
    }
}
