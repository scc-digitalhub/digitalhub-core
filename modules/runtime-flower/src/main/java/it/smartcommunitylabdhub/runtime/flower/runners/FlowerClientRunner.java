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

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sDeploymentRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.flower.FlowerClientRuntime;
import it.smartcommunitylabdhub.runtime.flower.model.FABModel;
import it.smartcommunitylabdhub.runtime.flower.model.FlowerSourceCode;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerClientTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerClientFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerClientRunSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class FlowerClientRunner {
    // TODO allow for running without build

    private static final int UID = 49999;
    private static final int GID = 49999;

    private final int userId;
    private final int groupId;
    private final String image;
    private final FlowerClientFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;

    private final Resource entrypoint = new ClassPathResource("runtime-flower/docker/client.sh");

    public FlowerClientRunner(
        String image,
        Integer userId,
        Integer groupId,
        FlowerClientFunctionSpec functionPythonSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        this.image = image;
        this.functionSpec = functionPythonSpec;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
    }

    public K8sRunnable produce(Run run) {
        FlowerClientRunSpec runSpec = new FlowerClientRunSpec(run.getSpec());
        FlowerClientTaskSpec taskSpec = runSpec.getTaskDeploySpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        coreEnvList.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:/shared/"));

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = new ArrayList<>();

        //run args. TODO - improve
        String[] args = {"--insecure", "--superlink", runSpec.getSuperlink()};

        // Parse run spec
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

        //write entrypoint
        try {
            ContextSource entry = ContextSource
                .builder()
                .name("client.sh")
                .base64(Base64.getEncoder().encodeToString(entrypoint.getContentAsByteArray()))
                .build();
            contextSources.add(entry);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading client entrypoint for runtime-flower");
        }

        if (functionSpec.getSource() != null) {
            FlowerSourceCode source = functionSpec.getSource();
            String path = "main.py";

            if (StringUtils.hasText(source.getSource())) {
                try {
                    //evaluate if local path (no scheme)
                    UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                    String scheme = uri.getScheme();

                    if (scheme != null) {
                        //write as ref
                        contextRefs = Collections.singletonList(ContextRef.from(source.getSource()));
                    } else {
                        if (StringUtils.hasText(path)) {
                            //override path for local src
                            path = uri.getPath();
                            if (path.startsWith(".")) {
                                path = path.substring(1);
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    //skip invalid source
                }
            }

            if (StringUtils.hasText(source.getBase64())) {
                contextSources.add(ContextSource.builder().name(path).base64(source.getBase64()).build());
                // generate toml in addition to source
                FABModel fabModel = new FABModel();
                fabModel.setName(runSpecAccessor.getFunction() + "-" + runSpecAccessor.getFunctionId());
                fabModel.setVersion("1.0.0");
                if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
                    fabModel.setDependencies(functionSpec.getRequirements());
                }
                fabModel.setServerApp("");
                fabModel.setClientApp("main:" + functionSpec.getSource().getHandler());
                fabModel.setDefaultFederation("core-federation");
                fabModel.setConfig(runSpec.getParameters());
                String toml = fabModel.toTOML();
                // convert toml to base64
                String tomlBase64 = Base64.getEncoder().encodeToString(toml.getBytes(StandardCharsets.UTF_8));
                contextSources.add(ContextSource.builder()
                                    .name("pyproject.toml")
                                    .base64(tomlBase64)
                                    .build());
            }
        }

        String cmd = null;
        if (!StringUtils.hasText(functionSpec.getImage())) {
            //use image as command
            cmd = "/bin/sh";
            List<String> argList = new ArrayList<>(List.of("/shared/client.sh", "/shared"));
            argList.addAll(Arrays.asList(args));
            args = argList.toArray(new String[0]);
        }


        K8sRunnable k8sDeploymentRunnable = K8sDeploymentRunnable
            .builder()
            .runtime(FlowerClientRuntime.RUNTIME)
            .task(FlowerClientTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(StringUtils.hasText(functionSpec.getImage()) ? functionSpec.getImage() : image)
            .command(cmd)
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
            .build();

        k8sDeploymentRunnable.setId(run.getId());
        k8sDeploymentRunnable.setProject(run.getProject());

        return k8sDeploymentRunnable;
    }
}
