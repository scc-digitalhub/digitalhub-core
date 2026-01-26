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

package it.smartcommunitylabdhub.runtime.guardrail.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runtime.guardrail.GuardrailRuntime;
import it.smartcommunitylabdhub.runtime.guardrail.specs.GuardrailServeRunSpec;
import it.smartcommunitylabdhub.runtime.guardrail.specs.GuardrailServeTaskSpec;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

public class GuardrailServeRunner extends GuardrailBaseRunner {

    private static final int UID = 8877;
    private static final int GID = 999;
    private static final int HTTP_PORT = 5051;

    private final int userId;
    private final int groupId;

    private final FunctionManager functionService;

    private final Resource entrypoint = new ClassPathResource("runtime-python/docker/entrypoint.sh");
    
    public GuardrailServeRunner(
        Map<String, String> images,
        Integer userId,
        Integer groupId,
        String command,
        K8sBuilderHelper k8sBuilderHelper,
        FunctionManager functionService
    ) {
        super(images, command, k8sBuilderHelper);
        this.functionService = functionService;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
        try {
            this.handlerMustache = mustacheFactory.compile(new InputStreamReader( handlerTemplate.getInputStream()), "guardrail-handler");
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading handler template for runtime-guardrail");
        }
    }

    public K8sRunnable produce(Run run, Map<String, String> secretData) {
        
        GuardrailServeRunSpec runSpec = new GuardrailServeRunSpec(run.getSpec());
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(runSpec.getTaskServeSpec().toMap());
        
        //prepare context
        Context ctx = prepareContext(run, secretData, runSpec, runSpec.getTaskServeSpec(), runSpec.getFunctionSpec());  

        //write entrypoint
        try {
            ContextSource entry = ContextSource
                .builder()
                .name("entrypoint.sh")
                .base64(Base64.getEncoder().encodeToString(entrypoint.getContentAsByteArray()))
                .build();
            ctx.contextSources().add(entry);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading entrypoint for runtime-guardrail");
        }

        List<String> args = new ArrayList<>(
            List.of(
                "/shared/entrypoint.sh",
                "--processor",
                command,
                "--config",
                "/shared/function.yaml",
                "--requirements",
                "/shared/requirements.txt"
            )
        );

        //expose http trigger only
        CorePort servicePort = new CorePort(HTTP_PORT, HTTP_PORT);

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

        String image = images.get(runSpec.getFunctionSpec().getPythonVersion().name());

        K8sRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(GuardrailRuntime.RUNTIME)
            .task(GuardrailServeTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(StringUtils.hasText(runSpec.getFunctionSpec().getImage()) ? runSpec.getFunctionSpec().getImage() : image)
            .command("/bin/bash")
            .args(args.toArray(new String[0]))
            .contextRefs(ctx.contextRefs())
            .contextSources(ctx.contextSources())
            .envs(ctx.coreEnvList())
            .secrets(ctx.coreSecrets())
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(runSpec.getTaskServeSpec().getResources()) : null)
            .volumes(runSpec.getTaskServeSpec().getVolumes())
            .template(runSpec.getTaskServeSpec().getProfile())
            //securityContext
            .fsGroup(groupId)
            .runAsGroup(groupId)
            .runAsUser(userId)
            //specific
            .replicas(runSpec.getTaskServeSpec().getReplicas())
            .servicePorts(List.of(servicePort))
            .serviceType(runSpec.getTaskServeSpec().getServiceType())
            .serviceNames(serviceNames != null && !serviceNames.isEmpty() ? serviceNames : null)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }
}

