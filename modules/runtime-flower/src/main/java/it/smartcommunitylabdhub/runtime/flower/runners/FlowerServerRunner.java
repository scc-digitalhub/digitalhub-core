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
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
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
import it.smartcommunitylabdhub.runtime.flower.FlowerServerRuntime;
import it.smartcommunitylabdhub.runtime.flower.model.FABModel;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerServerFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerServerRunSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerServerTaskSpec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;

public class FlowerServerRunner {

    private static final int UID = 49999;
    private static final int GID = 49999;
    // disable REST api scenario for now
    // private static final List<Integer> HTTP_PORTS = List.of(9091, 9093, 9095);
    private static final List<Integer> HTTP_PORTS = List.of(9091, 9093, 9092);
    private final int userId;
    private final int groupId;
    private final String image;
    private final FlowerServerFunctionSpec functionSpec;
    private final Map<String, String> secretData;

    private final K8sBuilderHelper k8sBuilderHelper;
    private final FunctionManager functionService;

    private final Resource entrypoint = new ClassPathResource("runtime-flower/docker/server.sh");

    private final String caCert;
    private final String caKey;
    private final String tlsConf;
    private final String tlsIntDomain;
    private final String tlsExtDomain;
    

    public FlowerServerRunner(
        String image,
        Integer userId,
        Integer groupId,
        String caCert,
        String caKey,
        String tlsConf,
        String tlsIntDomain,
        String tlsExtDomain,
        FlowerServerFunctionSpec functionPythonSpec,
        Map<String, String> secretData,
        K8sBuilderHelper k8sBuilderHelper,
        FunctionManager functionService
    ) {
        this.image = image;
        this.functionSpec = functionPythonSpec;
        this.secretData = secretData;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.functionService = functionService;

        this.userId = userId != null ? userId : UID;
        this.groupId = groupId != null ? groupId : GID;

        this.caCert = caCert;
        this.caKey = caKey;
        this.tlsConf = tlsConf;
        this.tlsIntDomain = tlsIntDomain;
        this.tlsExtDomain = tlsExtDomain;
    }

    public K8sRunnable produce(Run run) {
        FlowerServerRunSpec runSpec = new FlowerServerRunSpec(run.getSpec());
        FlowerServerTaskSpec taskSpec = runSpec.getTaskDeploySpec();
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


        //run args
        // disable REST api scenario for now
        // String[] args = {"--insecure", "--fleet-api-type", "rest"};
        String[] args = null;
        if (StringUtils.hasText(caCert) && StringUtils.hasText(tlsConf)) {
            String dns1 = k8sBuilderHelper.getServiceName("flower-server", FlowerServerTaskSpec.KIND, run.getId());
            String dns2 = k8sBuilderHelper.getServiceName("flower-server", run.getProject(),  taskAccessor.getFunction() + "-latest");
            contextSources.add(ContextSource.builder()
                    .name("certificates/ca.crt")
                    .base64(Base64.getEncoder().encodeToString(caCert.getBytes(StandardCharsets.UTF_8)))
                    .build());
            contextSources.add(ContextSource.builder()
                    .name("certificates/ca.key")
                    .base64(Base64.getEncoder().encodeToString(caKey.getBytes(StandardCharsets.UTF_8)))
                    .build());
            contextSources.add(ContextSource.builder()
                    .name("certificates/tls.conf")
                    .base64(Base64.getEncoder().encodeToString(preprocessConf(tlsConf, tlsIntDomain, tlsExtDomain, new String[]{dns1, dns2}).getBytes(StandardCharsets.UTF_8)))
                    .build());

            args = new String[] {
                "--ssl-ca-certfile", "certificates/ca.crt",
                "--ssl-certfile", "certificates/server.pem",
                "--ssl-keyfile", "certificates/server.key"
            };
        } else  {
            args = new String[] {"--insecure"};
        }

        if (runSpec.getAuthPublicKeys() != null && !runSpec.getAuthPublicKeys().isEmpty()) {
            //add auth public keys
            String authPublicKeys = String.join(",", runSpec.getAuthPublicKeys());
            contextSources.add(ContextSource.builder()
                    .name("keys/client_public_keys.csv")
                    .base64(Base64.getEncoder().encodeToString(authPublicKeys.getBytes(StandardCharsets.UTF_8)))
                    .build());

            args = Arrays.copyOf(args, args.length + 2);
            args[args.length - 2] = "--auth-list-public-keys";
            args[args.length - 1] = "keys/client_public_keys.csv";
        }

        //write entrypoint
        try {
            ContextSource entry = ContextSource
                .builder()
                .name("server.sh")
                .base64(Base64.getEncoder().encodeToString(entrypoint.getContentAsByteArray()))
                .build();
            contextSources.add(entry);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading server entrypoint for runtime-flower");
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
        contextSources.add(ContextSource.builder()
                            .name("pyproject.toml")
                            .base64(tomlBase64)
                            .build());

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

        String cmd = null;
        if (!StringUtils.hasText(functionSpec.getImage())) {
            //use image as command
            cmd = "/bin/sh";
            List<String> argList = new ArrayList<>(List.of("/shared/server.sh", "/shared"));
            argList.addAll(Arrays.asList(args));
            args = argList.toArray(new String[0]);
        }
        K8sRunnable k8sServeRunnable = K8sServeRunnable
            .builder()
            .runtime(FlowerServerRuntime.RUNTIME)
            .task(FlowerServerTaskSpec.KIND)
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
            .servicePorts(servicePorts)
            .serviceType(CoreServiceType.ClusterIP)
            .serviceNames(serviceNames != null && !serviceNames.isEmpty() ? serviceNames : null)
            .build();

        k8sServeRunnable.setId(run.getId());
        k8sServeRunnable.setProject(run.getProject());

        return k8sServeRunnable;
    }

    private String preprocessConf(String content, String tlsIntDomain, String tlsExtDomain, String[] intNames) {
        // TODO: revise!!!
        tlsIntDomain = tlsIntDomain.startsWith(".") ? tlsIntDomain : "." + tlsIntDomain;
        tlsExtDomain = tlsExtDomain.startsWith(".") ? tlsExtDomain : "." + tlsExtDomain;
        String res = content;
        for (int i = 1; i <= intNames.length; i++) {
            res = res.replace("${dns"+i+"}", intNames[i-1] + tlsIntDomain);
        }
        return res.replace("${extDomain}", "*" + tlsExtDomain);
    }

}
