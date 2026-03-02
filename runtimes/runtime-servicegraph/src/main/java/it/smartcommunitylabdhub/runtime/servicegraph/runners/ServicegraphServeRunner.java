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

package it.smartcommunitylabdhub.runtime.servicegraph.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runtime.servicegraph.ServicegraphRuntime;
import it.smartcommunitylabdhub.runtime.servicegraph.model.ServicegraphSourceCode;
import it.smartcommunitylabdhub.runtime.servicegraph.specs.ServicegraphFunctionSpec;
import it.smartcommunitylabdhub.runtime.servicegraph.specs.ServicegraphRunSpec;
import it.smartcommunitylabdhub.runtime.servicegraph.specs.ServicegraphServeRunSpec;
import it.smartcommunitylabdhub.runtime.servicegraph.specs.ServicegraphServeTaskSpec;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.util.StringUtils;

public class ServicegraphServeRunner {

    private static final int HTTP_PORT = 8080;

    private static final int UID = 8877;
    private static final int GID = 999;

    private final int userId;
    private final int groupId;

    private final String volumeSizeSpec;
    private final String image;

    private final String command;

    private final K8sBuilderHelper k8sBuilderHelper;

    private final FunctionManager functionService;
    
    protected record Context(
        List<ContextRef> contextRefs, 
        List<ContextSource> contextSources,
        List<CoreEnv> coreEnvList,
        List<CoreEnv> coreSecrets
    ) {}

    public ServicegraphServeRunner(
        String image,
        String volumeSizeSpec,
        Integer userId,
        Integer groupId,
        String command,
        K8sBuilderHelper k8sBuilderHelper,
        FunctionManager functionService
    ) {
        this.image = image;
        this.command = command;

        this.k8sBuilderHelper = k8sBuilderHelper;

        this.functionService = functionService;
        this.volumeSizeSpec = volumeSizeSpec;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
    }

    public K8sRunnable produce(Run run, Map<String, String> secretData) {
        
        ServicegraphServeRunSpec runSpec = new ServicegraphServeRunSpec(run.getSpec());
        ServicegraphServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(runSpec.getTaskServeSpec().toMap());
        
        //prepare context
        Context ctx = prepareContext(run, secretData, runSpec, runSpec.getTaskServeSpec(), runSpec.getFunctionSpec());  

        List<CoreVolume> coreVolumes = new ArrayList<>(
            taskSpec.getVolumes() != null ? taskSpec.getVolumes() : List.of()
        );
        //check if scratch disk is requested as resource or set default
        String volumeSize = taskSpec.getResources() != null && taskSpec.getResources().getDisk() != null
            ? taskSpec.getResources().getDisk()
            : volumeSizeSpec;
        CoreResource diskResource = new CoreResource();
        diskResource.setDisk(volumeSize);
        Optional
            .ofNullable(k8sBuilderHelper)
            .ifPresent(helper -> {
                Optional.ofNullable(helper.buildSharedVolume(diskResource)).ifPresent(coreVolumes::add);
            });


        List<String> args = List.of("/shared/servicegraph.yaml");

        //evaluate service names
        List<String> serviceNames = new ArrayList<>();
        if (runSpec.getTaskServeSpec().getServiceName() != null && StringUtils.hasText(runSpec.getTaskServeSpec().getServiceName())) {
            //prepend with function name
            serviceNames.add(taskAccessor.getFunction() + "-" + runSpec.getTaskServeSpec().getServiceName());
        }

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
            .runtime(ServicegraphRuntime.RUNTIME)
            .task(ServicegraphServeTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(image)
            .command(command)
            .args(args.toArray(new String[0]))
            .contextRefs(ctx.contextRefs())
            .contextSources(ctx.contextSources())
            .envs(ctx.coreEnvList())
            .secrets(ctx.coreSecrets())
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(runSpec.getTaskServeSpec().getResources()) : null)
            .volumes(coreVolumes)
            .template(taskSpec.getProfile())
            //securityContext
            .fsGroup(groupId)
            .runAsGroup(groupId)
            .runAsUser(userId)
            //specific
            .replicas(taskSpec.getReplicas())
            // http and grpc ports
             .servicePorts(List.of(new CorePort(HTTP_PORT, HTTP_PORT)))
            .serviceType(taskSpec.getServiceType())
            .serviceNames(serviceNames != null && !serviceNames.isEmpty() ? serviceNames : null)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }

    protected Context prepareContext(Run run, Map<String, String> secretData, ServicegraphRunSpec runSpec, ServicegraphServeTaskSpec taskSpec, ServicegraphFunctionSpec functionSpec) {

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        ServicegraphSourceCode servicegraphSourceCode = functionSpec.getSource();
        String servicegraphSpec = new String(Base64.getDecoder().decode(servicegraphSourceCode.getBase64()), StandardCharsets.UTF_8);

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = new ArrayList<>();

        //function definition
        ContextSource fn = ContextSource
            .builder()
            .name("servicegraph.yaml")
            .base64(Base64.getEncoder().encodeToString(servicegraphSpec.getBytes(StandardCharsets.UTF_8)))
            .build();
        contextSources.add(fn);

        return new Context(contextRefs, contextSources, coreEnvList, coreSecrets);
    } 
}

