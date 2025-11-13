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

package it.smartcommunitylabdhub.framework.k8s.processors;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.framework.k8s.model.K8sRunStatus;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.io.Serializable;
import java.util.Map;
import org.springframework.stereotype.Component;

@ProcessorType(
    stages = { "onPending", "onRunning", "onCompleted", "onError", "onStopped", "onDeleted" },
    type = Run.class,
    spec = Status.class
)
@Component
public class K8sProcessor implements Processor<Run, K8sRunStatus> {

    @Override
    public <I> K8sRunStatus process(String stage, Run run, I input) throws CoreRuntimeException {
        if (input instanceof K8sRunnable) {
            Map<String, Serializable> res = ((K8sRunnable) input).getResults();

            if (res != null && !res.isEmpty()) {
                //extract k8s details
                return K8sRunStatus.builder().k8s(res).build();
            }
        }
        return null;
    }
}
