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

package it.smartcommunitylabdhub.runtime.kfp.runners;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.kfp.KFPRuntime;
import it.smartcommunitylabdhub.runtime.kfp.specs.KFPBuildRunSpec;
import it.smartcommunitylabdhub.runtime.kfp.specs.KFPBuildTaskSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * KFPBuildRunner
 * <p>
 * You can use this as a simple class or as a registered bean. If you want to retrieve this as bean from RunnerFactory
 * you have to register it using the following annotation:
 *
 * @RunnerComponent(runtime = "kfp", task = "build")
 */
public class KFPBuildRunner {

    private final String image;
    private final Map<String, String> secretData;

    public KFPBuildRunner(String image, Map<String, String> secretData) {
        this.image = image;
        this.secretData = secretData;
    }

    public K8sRunnable produce(Run run) {
        KFPBuildRunSpec runSpec = new KFPBuildRunSpec(run.getSpec());
        KFPBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(
                new CoreEnv("PROJECT_NAME", run.getProject()),
                new CoreEnv("RUN_ID", run.getId()),
                new CoreEnv("DHCORE_WORKFLOW_IMAGE", image)
            )
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        K8sRunnable k8sJobRunnable = K8sJobRunnable
            .builder()
            .runtime(KFPRuntime.RUNTIME)
            .task(KFPBuildTaskSpec.KIND)
            .image(image)
            .command("python")
            .args(List.of("wrapper.py").toArray(String[]::new))
            // .resources(taskSpec.getResources())
            .nodeSelector(taskSpec.getNodeSelector())
            .volumes(taskSpec.getVolumes())
            .secrets(coreSecrets)
            .envs(coreEnvList)
            .affinity(taskSpec.getAffinity())
            .tolerations(taskSpec.getTolerations())
            .state(State.READY.name())
            .build();

        k8sJobRunnable.setId(run.getId());
        k8sJobRunnable.setProject(run.getProject());

        return k8sJobRunnable;
    }
}
