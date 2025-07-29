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

package it.smartcommunitylabdhub.runtime.flower.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.FunctionManager;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.runtime.flower.FlowerRuntime;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerClientTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerRunSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerServerTaskSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FlowerServerRunner {

    private static final int UID = 1000;
    private static final int GID = 1000;
    private static final List<Integer> HTTP_PORTS = List.of(9091, 9092, 9093);

    private final int userId;
    private final int groupId;
    private final FlowerFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final FunctionManager functionService;

    public FlowerServerRunner(
        Integer userId,
        Integer groupId,
        FlowerFunctionSpec functionPythonSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper,
        FunctionManager functionService
    ) {
        this.functionSpec = functionPythonSpec;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.functionService = functionService;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
    }

    public K8sRunnable produce(Run run) {
        FlowerRunSpec runSpec = new FlowerRunSpec(run.getSpec());
        FlowerServerTaskSpec taskSpec = runSpec.getTaskServerSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        //run args. TODO - improve
        String[] args = {"--insecure"};

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = new ArrayList<>();

        //expose ports
        List<CorePort> servicePorts = HTTP_PORTS.stream()
            .map(port -> new CorePort(port, port))
            .toList();

        //evaluate service names
        List<String> serviceNames = new ArrayList<>();

        if (functionService != null) {
            //check if latest
            Function latest = functionService.getLatestFunction(run.getProject(), taskAccessor.getFunction());
            if (taskAccessor.getFunctionId().equals(latest.getId())) {
                //prepend with function name
                serviceNames.add(taskAccessor.getFunction() + "-latest");
            }
        }

        K8sRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(FlowerRuntime.RUNTIME)
            .task(FlowerClientTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(functionSpec.getServerImage())
            .args(args)
            .contextRefs(contextRefs)
            .contextSources(contextSources)
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
            .fsGroup(groupId)
            .runAsGroup(groupId)
            .runAsUser(userId)
            //specific
            .replicas(1)
            .servicePorts(servicePorts)
            .serviceType(CoreServiceType.ClusterIP)
            .serviceNames(serviceNames != null && !serviceNames.isEmpty() ? serviceNames : null)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }
}
