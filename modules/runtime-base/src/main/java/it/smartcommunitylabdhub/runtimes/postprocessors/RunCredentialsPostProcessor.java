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

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.annotations.common.PostProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.PostProcessor;
import it.smartcommunitylabdhub.commons.infrastructure.SecuredRunnable;
import it.smartcommunitylabdhub.runs.Run;
import java.io.Serializable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Injects the credentials of the current user (the run OWNER, operations always run on its behalf)
 * into every secured runnable produced by run transitions.
 *
 * Runs on every stage, before any other post processor. Credentials are injected only when the
 * runnable has none: runtimes managing credentials on their own (ex. restricted pipelines) are
 * left untouched. Failures are explicit and fail the transition.
 */
@Slf4j
@Component
@PostProcessorType(stages = { PostProcessorType.ALL_STAGES }, type = Run.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RunCredentialsPostProcessor implements PostProcessor<Run> {

    private CredentialsService credentialsService;

    @Autowired(required = false)
    public void setCredentialsService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    @Override
    public Serializable process(String stage, Run run, Serializable output) throws CoreRuntimeException {
        if (credentialsService == null || !(output instanceof SecuredRunnable runnable)) {
            return null;
        }

        if (runnable.getCredentials() != null && !runnable.getCredentials().isEmpty()) {
            //credentials managed by the runtime
            return null;
        }

        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        if (auth == null) {
            log.debug("no user authentication available for run {}, skip credentials", run.getId());
            return null;
        }

        List<Credentials> credentials;
        try {
            credentials = credentialsService.getCredentials(auth);
        } catch (CoreRuntimeException e) {
            throw e;
        } catch (RuntimeException e) {
            //explicit failure: never dispatch a runnable without its credentials
            throw new CoreRuntimeException("unable to resolve credentials: " + e.getMessage(), e);
        }

        if (credentials == null || credentials.isEmpty()) {
            return null;
        }

        log.debug("inject {} credentials for run {} on stage {}", credentials.size(), run.getId(), stage);
        runnable.setCredentials(credentials);

        return runnable;
    }
}
