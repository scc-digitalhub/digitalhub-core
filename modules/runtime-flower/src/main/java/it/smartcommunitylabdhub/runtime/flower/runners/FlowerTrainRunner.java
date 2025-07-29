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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCronJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.flower.FlowerRuntime;
import it.smartcommunitylabdhub.runtime.flower.model.FABModel;
import it.smartcommunitylabdhub.runtime.flower.model.FlowerSourceCode;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerRunSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerTrainTaskSpec;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class FlowerTrainRunner {

    private static ObjectMapper jsonMapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;

    private static final int UID = 1000;
    private static final int GID = 1000;
    private static String defaultFederation = "core-federation";

    private final String image;
    private final int userId;
    private final int groupId;
    private final FlowerFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;

    public FlowerTrainRunner(
        String image,
        Integer userId,
        Integer groupId,
        FlowerFunctionSpec functionPythonSpec,
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
        FlowerRunSpec runSpec = new FlowerRunSpec(run.getSpec());
        FlowerTrainTaskSpec taskSpec = runSpec.getTaskTrainSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());

        try {
            List<CoreEnv> coreEnvList = new ArrayList<>(
                List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
            );

            List<CoreEnv> coreSecrets = secretData == null
                ? null
                : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

            Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

            // Parse run spec
            RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

            //read source and build context
            List<ContextRef> contextRefs = null;
            List<ContextSource> contextSources = new ArrayList<>();

            FABModel fabModel = new FABModel();
            fabModel.setName(runSpecAccessor.getFunction());
            fabModel.setVersion(runSpecAccessor.getFunctionId());
            if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
                fabModel.setDependencies(functionSpec.getRequirements());
            }
            Map<String, Serializable> config = new HashMap<>();
            config.put("insecure", true);
            config.put("address", runSpec.getSuperlink());
            String federation = runSpec.getFederation() != null ? runSpec.getFederation() : defaultFederation;
            fabModel.setFederationConfigs(Collections.singletonMap(federation, config));
            String toml = fabModel.toTOML();
            // convert toml to base64
            String tomlBase64 = Base64.getEncoder().encodeToString(toml.getBytes(StandardCharsets.UTF_8));
            contextSources.add(ContextSource.builder().name("pyproject.toml").base64(tomlBase64).build());

            List<String> args = new ArrayList<>(
                List.of(
                    "run",
                    ".",
                    federation,
                    "--stream"
                )
            );

            K8sRunnable k8sJobRunnable = K8sJobRunnable
                .builder()
                .runtime(FlowerRuntime.RUNTIME)
                .task(FlowerTrainTaskSpec.KIND)
                .state(State.READY.name())
                .labels(
                    k8sBuilderHelper != null
                        ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                        : null
                )
                //base
                .image(image)
                .command("flwr")
                .args(args.toArray(new String[0]))
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
                .build();

            if (StringUtils.hasText(taskSpec.getSchedule())) {
                //build a cronJob
                k8sJobRunnable =
                    K8sCronJobRunnable
                        .builder()
                        .runtime(FlowerRuntime.RUNTIME)
                        .task(FlowerTrainTaskSpec.KIND)
                        .state(State.READY.name())
                        .labels(
                            k8sBuilderHelper != null
                                ? List.of(
                                    new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction())
                                )
                                : null
                        )
                        //base
                        .image(image)
                        .command("flwr")
                        .args(args.toArray(new String[0]))
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
                        .schedule(taskSpec.getSchedule())
                        .build();
            }

            k8sJobRunnable.setId(run.getId());
            k8sJobRunnable.setProject(run.getProject());

            return k8sJobRunnable;
        } catch (Exception e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }
}
