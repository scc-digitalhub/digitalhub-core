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

package it.smartcommunitylabdhub.runtime.hera.runners;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.runtime.hera.HeraRuntime;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraPipelineRunSpec;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraPipelineTaskSpec;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraWorkflowSpec;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class HeraPipelineRunner {

    public K8sArgoWorkflowRunnable produce(Run run) {
        HeraPipelineRunSpec runSpec = new HeraPipelineRunSpec(run.getSpec());
        HeraPipelineTaskSpec taskSpec = runSpec.getTaskPipelineSpec();
        HeraWorkflowSpec workflowSpec = runSpec.getWorkflowSpec();
        if (workflowSpec == null || workflowSpec.getBuild() == null || workflowSpec.getBuild().getBase64() == null) {
            throw new IllegalArgumentException("workflowSpec is null");
        }

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        Map<String, Serializable> parameters = new HashMap<>();
        if (runSpec.getParameters() != null) parameters.putAll(runSpec.getParameters());
        if (runSpec.getInputs() != null) parameters.putAll(runSpec.getInputs());

        String argoSpec = new String(
            Base64.getDecoder().decode(workflowSpec.getBuild().getBase64()),
            StandardCharsets.UTF_8
        );

        K8sArgoWorkflowRunnable argoRunnable = K8sArgoWorkflowRunnable
            .builder()
            .runtime(HeraRuntime.RUNTIME)
            .task(HeraPipelineTaskSpec.KIND)
            .state(State.READY.name())
            .workflowSpec(argoSpec)
            .parameters(parameters)
            .envs(coreEnvList)
            //support shared volumes
            .volumes(taskSpec.getVolumes())
            .build();

        argoRunnable.setId(run.getId());
        argoRunnable.setProject(run.getProject());

        return argoRunnable;
    }
}
