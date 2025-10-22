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

package it.smartcommunitylabdhub.runtime.flower.app;

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
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runtime.flower.app.specs.FlowerAppFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.app.specs.FlowerAppRunSpec;
import it.smartcommunitylabdhub.runtime.flower.app.specs.FlowerAppTrainTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.model.FABModel;
import it.smartcommunitylabdhub.runtime.flower.model.FlowerSourceCode;
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

public class FlowerAppTrainRunner {

    private static final int UID = 1000;
    private static final int GID = 1000;
    private static String defaultFederation = "core-federation";

    private final String image;
    private final int userId;
    private final int groupId;

    private final K8sBuilderHelper k8sBuilderHelper;

    private final Resource entrypoint = new ClassPathResource("runtime-flower/docker/app.sh");

    public FlowerAppTrainRunner(String image, Integer userId, Integer groupId, K8sBuilderHelper k8sBuilderHelper) {
        this.image = image;

        this.k8sBuilderHelper = k8sBuilderHelper;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
    }

    public K8sJobRunnable produce(Run run, Map<String, String> secretData) {
        FlowerAppRunSpec runSpec = new FlowerAppRunSpec(run.getSpec());
        FlowerAppTrainTaskSpec taskSpec = runSpec.getTaskTrainSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        FlowerAppFunctionSpec functionSpec = runSpec.getFunctionSpec();

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        coreEnvList.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:/shared/"));

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        // Parse run spec
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = new ArrayList<>();

        String federation = runSpec.getFederation() != null ? runSpec.getFederation() : defaultFederation;

        //write entrypoint
        try {
            ContextSource entry = ContextSource
                .builder()
                .name("app.sh")
                .base64(Base64.getEncoder().encodeToString(entrypoint.getContentAsByteArray()))
                .build();
            contextSources.add(entry);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading server entrypoint for runtime-flower");
        }

        if (functionSpec.getFabSource() != null) {
            FlowerSourceCode source = functionSpec.getFabSource();
            String serverPath = "server.py";
            String clientPath = "client.py";

            if (StringUtils.hasText(source.getSource())) {
                try {
                    //evaluate if local path (no scheme)
                    UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                    String scheme = uri.getScheme();

                    if (scheme != null) {
                        //write as ref
                        contextRefs = Collections.singletonList(ContextRef.from(source.getSource()));
                    } else {
                        if (StringUtils.hasText(serverPath)) {
                            //override path for local src
                            serverPath = uri.getPath();
                            if (serverPath.startsWith(".")) {
                                serverPath = serverPath.substring(1);
                            }
                        }
                        if (StringUtils.hasText(clientPath)) {
                            //override path for local src
                            clientPath = uri.getPath();
                            if (clientPath.startsWith(".")) {
                                clientPath = clientPath.substring(1);
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    //skip invalid source
                }
            }

            if (StringUtils.hasText(source.getClientbase64()) && StringUtils.hasText(source.getServerbase64())) {
                contextSources.add(ContextSource.builder().name(clientPath).base64(source.getClientbase64()).build());
                contextSources.add(ContextSource.builder().name(serverPath).base64(source.getServerbase64()).build());
                // generate toml in addition to source
                FABModel fabModel = new FABModel();
                fabModel.setName(runSpecAccessor.getFunction() + "-" + runSpecAccessor.getFunctionId());
                fabModel.setVersion("1.0.0");
                if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
                    fabModel.setDependencies(functionSpec.getRequirements());
                }
                fabModel.setServerApp("server:" + functionSpec.getFabSource().getServerapp());
                // fake client app for compatibility
                fabModel.setClientApp("client:" + functionSpec.getFabSource().getClientapp());
                fabModel.setDefaultFederation(defaultFederation);
                Map<String, Serializable> config = new HashMap<>();
                config.put("address", runSpec.getSuperlink());
                if (StringUtils.hasText(runSpec.getRootCertificates())) {
                    config.put("root-certificates", "certificates/ca.crt");
                } else {
                    config.put("insecure", true);
                }

                fabModel.setFederationConfigs(Collections.singletonMap(federation, config));

                fabModel.setConfig(runSpec.getParameters());
                String toml = fabModel.toTOML();
                // convert toml to base64
                String tomlBase64 = Base64.getEncoder().encodeToString(toml.getBytes(StandardCharsets.UTF_8));
                contextSources.add(ContextSource.builder().name("pyproject.toml").base64(tomlBase64).build());
            }
        }

        String federationConfig = "address=\"" + runSpec.getSuperlink() + "\"";
        if (StringUtils.hasText(runSpec.getRootCertificates())) {
            contextSources.add(
                ContextSource
                    .builder()
                    .name("certificates/ca.crt")
                    .base64(
                        Base64
                            .getEncoder()
                            .encodeToString(prepareCA(runSpec.getRootCertificates()).getBytes(StandardCharsets.UTF_8))
                    )
                    .build()
            );
            federationConfig += " root-certificates=\"certificates/ca.crt\"";
        } else {
            federationConfig += " insecure=true";
        }

        List<String> args = new ArrayList<>(
            List.of(
                "/shared/app.sh",
                "/shared",
                "run",
                "--format",
                "json",
                "--federation-config",
                federationConfig,
                "/shared/",
                federation
            )
        );

        String cmd = "/bin/bash";

        K8sJobRunnable k8sJobRunnable = K8sJobRunnable
            .builder()
            .runtime(FlowerAppRuntime.RUNTIME)
            .task(FlowerAppTrainTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(image)
            .command(cmd)
            .args(args.toArray(new String[0]))
            .contextRefs(contextRefs)
            .contextSources(contextSources)
            .envs(coreEnvList)
            .secrets(coreSecrets)
            .resources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(taskSpec.getResources()) : null)
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

        k8sJobRunnable.setId(run.getId());
        k8sJobRunnable.setProject(run.getProject());

        return k8sJobRunnable;
    }

    private String prepareCA(String ca) {
        return (
            "-----BEGIN CERTIFICATE-----\n" +
            ca.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "").trim() +
            "\n" +
            "-----END CERTIFICATE-----\n"
        );
    }
}
