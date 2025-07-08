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

package it.smartcommunitylabdhub.runtime.container.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCronJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.container.ContainerRuntime;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerFunctionSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerFunctionSpec.SourceCodeLanguages;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerJobTaskSpec;
import it.smartcommunitylabdhub.runtime.container.specs.ContainerRunSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class ContainerJobRunner {

    private final ContainerFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;

    public ContainerJobRunner(
        ContainerFunctionSpec functionContainerSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        this.functionSpec = functionContainerSpec;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    public K8sRunnable produce(Run run) {
        ContainerRunSpec runSpec = new ContainerRunSpec(run.getSpec());
        ContainerJobTaskSpec taskSpec = runSpec.getTaskJobSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );
        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = null;

        if (functionSpec.getSource() != null && StringUtils.hasText(functionSpec.getSource().getSource())) {
            SourceCode<SourceCodeLanguages> source = functionSpec.getSource();

            try {
                //evaluate if local path (no scheme)
                UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                String scheme = uri.getScheme();

                if (scheme != null) {
                    //write as ref
                    contextRefs = Collections.singletonList(ContextRef.from(source.getSource()));
                } else {
                    //write as source
                    String path = source.getSource();
                    if (StringUtils.hasText(source.getBase64())) {
                        contextSources =
                            Collections.singletonList(
                                (ContextSource.builder().name(path).base64(source.getBase64()).build())
                            );
                    }
                }
            } catch (IllegalArgumentException e) {
                //skip invalid source
            }
        }

        K8sRunnable k8sJobRunnable = K8sJobRunnable
            .builder()
            .runtime(ContainerRuntime.RUNTIME)
            .task(ContainerJobTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(functionSpec.getImage())
            .imagePullPolicy(functionSpec.getImagePullPolicy())
            .command(functionSpec.getCommand())
            .args(runSpec.getArgs() != null ? runSpec.getArgs().toArray(new String[0]) : null)
            .envs(coreEnvList)
            .secrets(coreSecrets)
            .resources(taskSpec.getResources())
            .volumes(taskSpec.getVolumes())
            .nodeSelector(taskSpec.getNodeSelector())
            .affinity(taskSpec.getAffinity())
            .tolerations(taskSpec.getTolerations())
            .runtimeClass(taskSpec.getRuntimeClass())
            .priorityClass(taskSpec.getPriorityClass())
            .template(taskSpec.getProfile())
            //securityContext
            .fsGroup(taskSpec.getFsGroup())
            .runAsGroup(taskSpec.getRunAsGroup())
            .runAsUser(taskSpec.getRunAsUser())
            //specific
            .contextRefs(contextRefs)
            .contextSources(contextSources)
            .build();

        if (StringUtils.hasText(taskSpec.getSchedule())) {
            //build a cronJob
            k8sJobRunnable =
                K8sCronJobRunnable
                    .builder()
                    .runtime(ContainerRuntime.RUNTIME)
                    .task(ContainerJobTaskSpec.KIND)
                    .state(State.READY.name())
                    //base
                    .image(functionSpec.getImage())
                    .command(functionSpec.getCommand())
                    .args(runSpec.getArgs() != null ? runSpec.getArgs().toArray(new String[0]) : null)
                    .envs(coreEnvList)
                    .secrets(coreSecrets)
                    .resources(taskSpec.getResources())
                    .volumes(taskSpec.getVolumes())
                    .nodeSelector(taskSpec.getNodeSelector())
                    .affinity(taskSpec.getAffinity())
                    .tolerations(taskSpec.getTolerations())
                    .runtimeClass(taskSpec.getRuntimeClass())
                    .priorityClass(taskSpec.getPriorityClass())
                    .template(taskSpec.getProfile())
                    //securityContext
                    .fsGroup(taskSpec.getFsGroup())
                    .runAsGroup(taskSpec.getRunAsGroup())
                    .runAsUser(taskSpec.getRunAsUser())
                    //specific
                    .contextRefs(contextRefs)
                    .contextSources(contextSources)
                    .schedule(taskSpec.getSchedule())
                    .build();
        }

        k8sJobRunnable.setId(run.getId());
        k8sJobRunnable.setProject(run.getProject());

        return k8sJobRunnable;
    }
}
