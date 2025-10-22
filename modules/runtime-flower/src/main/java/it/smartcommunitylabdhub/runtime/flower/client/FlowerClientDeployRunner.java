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

package it.smartcommunitylabdhub.runtime.flower.client;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sDeploymentRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientDeployRunSpec;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientDeployTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientRunSpec.IsolationType;
import it.smartcommunitylabdhub.runtime.flower.model.FABModel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

public class FlowerClientDeployRunner {

    private static final int UID = 49999;
    private static final int GID = 49999;

    private final int userId;
    private final int groupId;
    private final String image;

    private final SecretService secretService;
    private final K8sBuilderHelper k8sBuilderHelper;

    private final Resource entrypoint = new ClassPathResource("runtime-flower/docker/client.sh");

    public FlowerClientDeployRunner(
        String image,
        Integer userId,
        Integer groupId,
        SecretService secretService,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        this.image = image;
        this.secretService = secretService;
        this.k8sBuilderHelper = k8sBuilderHelper;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;
    }

    public K8sRunnable produce(Run run, Map<String, String> secretData) {
        FlowerClientDeployRunSpec runSpec = new FlowerClientDeployRunSpec(run.getSpec());
        FlowerClientDeployTaskSpec taskSpec = runSpec.getTaskDeploySpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        FlowerClientFunctionSpec functionSpec = runSpec.getFunctionSpec();

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

        //run args
        // disable REST api scenario for now
        // String[] args = {"--insecure", "--superlink", createSuperlinkAddress(runSpec.getSuperlink()), "--rest"};
        List<String> args = new ArrayList<>();
        args.addAll(List.of("/shared/client.sh", "--path_to_project", "/shared"));

        if (StringUtils.hasText(runSpec.getRootCertificates())) {
            String ca = prepareCA(runSpec.getRootCertificates());
            contextSources.add(
                ContextSource
                    .builder()
                    .name("certificates/ca.crt")
                    .base64(Base64.getEncoder().encodeToString(ca.getBytes(StandardCharsets.UTF_8)))
                    .build()
            );
            args.addAll(List.of("--certificate", "certificates/ca.crt"));
        }
        if (StringUtils.hasText(runSpec.getSuperlink())) {
            args.addAll(List.of("--superlink", createSuperlinkAddress(runSpec.getSuperlink())));
        } else {
            throw new CoreRuntimeException("Superlink is required for Flower Client Run");
        }

        args.addAll(
            List.of(
                "--isolation",
                runSpec.getIsolation() != null ? runSpec.getIsolation().name() : IsolationType.subprocess.name()
            )
        );

        if (runSpec.getPrivateKeySecret() != null && runSpec.getPublicKeySecret() != null) {
            Map<String, String> secretDataMap = secretService.getSecretData(
                run.getProject(),
                Set.of(runSpec.getPrivateKeySecret(), runSpec.getPublicKeySecret())
            );
            if (
                secretDataMap.get(runSpec.getPrivateKeySecret()) != null &&
                secretDataMap.get(runSpec.getPublicKeySecret()) != null
            ) {
                String privateKey = secretDataMap.get(runSpec.getPrivateKeySecret());
                String publicKey = secretDataMap.get(runSpec.getPublicKeySecret());
                contextSources.add(
                    ContextSource
                        .builder()
                        .name("keys/auth_key.pub")
                        .base64(Base64.getEncoder().encodeToString(publicKey.getBytes(StandardCharsets.UTF_8)))
                        .build()
                );
                contextSources.add(
                    ContextSource
                        .builder()
                        .name("keys/auth_key")
                        .base64(Base64.getEncoder().encodeToString(privateKey.getBytes(StandardCharsets.UTF_8)))
                        .build()
                );
                args.addAll(List.of("--private_key", "keys/auth_key", "--public_key", "keys/auth_key.pub"));
            } else if (secretDataMap.get(runSpec.getPrivateKeySecret()) != null) {
                throw new CoreRuntimeException("Public key secret not found: " + runSpec.getPublicKeySecret());
            } else if (secretDataMap.get(runSpec.getPublicKeySecret()) != null) {
                throw new CoreRuntimeException("Private key secret not found: " + runSpec.getPrivateKeySecret());
            }
        }

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

        FABModel fabModel = new FABModel();
        fabModel.setName("flowerapp");
        fabModel.setVersion("1.0.0");
        fabModel.setDependencies(new LinkedList<>());
        // disable REST api scenario for now
        // fabModel.getDependencies().add("flwr[rest]");
        if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
            fabModel.getDependencies().addAll(functionSpec.getRequirements());
        }
        fabModel.setDefaultFederation("core-federation");
        String toml = fabModel.toTOML();
        // convert toml to base64
        String tomlBase64 = Base64.getEncoder().encodeToString(toml.getBytes(StandardCharsets.UTF_8));
        contextSources.add(ContextSource.builder().name("pyproject.toml").base64(tomlBase64).build());

        if (runSpec.getNodeConfig() != null && !runSpec.getNodeConfig().isEmpty()) {
            String config = StringUtils.collectionToDelimitedString(
                runSpec
                    .getNodeConfig()
                    .entrySet()
                    .stream()
                    .map(e -> {
                        return e.getKey() + "=" + e.getValue();
                    })
                    .collect(Collectors.toList()),
                " "
            );
            args.addAll(List.of("--node_config", config));
        }

        String cmd = "/bin/bash";

        K8sRunnable k8sDeploymentRunnable = K8sDeploymentRunnable
            .builder()
            .runtime(FlowerClientRuntime.RUNTIME)
            .task(FlowerClientDeployTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            //base
            .image(StringUtils.hasText(functionSpec.getImage()) ? functionSpec.getImage() : image)
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
            //specific
            .replicas(1)
            .build();

        k8sDeploymentRunnable.setId(run.getId());
        k8sDeploymentRunnable.setProject(run.getProject());

        return k8sDeploymentRunnable;
    }

    /**
     * Prepare CA certificate for Flower Client.
     * It ensures the certificate is properly formatted with BEGIN and END lines.
     *
     * @param ca The CA certificate string.
     * @return The formatted CA certificate string.
     */
    private String prepareCA(String ca) {
        return (
            "-----BEGIN CERTIFICATE-----\n" +
            ca.replace("-----BEGIN CERTIFICATE-----", "").replace("-----END CERTIFICATE-----", "").trim() +
            "\n-----END CERTIFICATE-----\n"
        );
    }

    private String createSuperlinkAddress(String superlink) {
        // disable REST api scenario for now
        // return superlink.startsWith("http")
        //     ? superlink
        //     : "http://" + superlink;
        return superlink;
    }
}
