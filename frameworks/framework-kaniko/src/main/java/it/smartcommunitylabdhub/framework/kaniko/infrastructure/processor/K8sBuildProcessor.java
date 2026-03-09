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

package it.smartcommunitylabdhub.framework.kaniko.infrastructure.processor;

import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

@ProcessorType(stages = { "onRunning" }, type = Run.class, spec = Status.class)
@Component
public class K8sBuildProcessor implements Processor<Run, K8sBuildStatus> {

    @Override
    public <I> K8sBuildStatus process(String stage, Run run, I input) throws CoreRuntimeException {
        if (input instanceof K8sContainerBuilderRunnable runnable) {
            String dockerfile = runnable.getDockerFile();

            if (dockerfile != null && !dockerfile.isEmpty()) {
                K8sBuildStatus rs = K8sBuildStatus.with(run.getStatus());
                rs.setDockerfile(Base64.getEncoder().encodeToString(dockerfile.getBytes(StandardCharsets.UTF_8)));
                return rs;
            }
        }
        return null;
    }
}
