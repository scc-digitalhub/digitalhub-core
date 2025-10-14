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

package it.smartcommunitylabdhub.runtime.hera;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.providers.AccessCredentials;
import it.smartcommunitylabdhub.authorization.providers.AccessCredentialsProvider;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.jackson.YamlMapperFactory;
import it.smartcommunitylabdhub.commons.models.base.Executable;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.commons.services.WorkflowManager;
import it.smartcommunitylabdhub.framework.argo.objects.K8sWorkflowObject;
import it.smartcommunitylabdhub.framework.argo.runnables.K8sArgoWorkflowRunnable;
import it.smartcommunitylabdhub.framework.k8s.base.K8sBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.hera.dtos.NodeStatusDTO;
import it.smartcommunitylabdhub.runtime.hera.mapper.NodeStatusMapper;
import it.smartcommunitylabdhub.runtime.hera.runners.HeraBuildRunner;
import it.smartcommunitylabdhub.runtime.hera.runners.HeraPipelineRunner;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraBuildRunSpec;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraPipelineRunSpec;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraPipelineTaskSpec;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraRunSpec;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraRunStatus;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraWorkflowSpec;
import it.smartcommunitylabdhub.runtime.hera.specs.HeraWorkflowSpec.HeraWorkflowCodeLanguages;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

@RuntimeComponent(runtime = HeraRuntime.RUNTIME)
@Slf4j
public class HeraRuntime extends K8sBaseRuntime<HeraWorkflowSpec, HeraRunSpec, HeraRunStatus, K8sRunnable> {

    public static final Integer DEFAULT_DURATION = 3600 * 8; // 8 hour
    public static final Integer MIN_DURATION = 300; // 5 minutes

    public static final String RUNTIME = "hera";
    public static final String[] KINDS = { HeraBuildRunSpec.KIND, HeraPipelineRunSpec.KIND };

    @Autowired
    SecretService secretService;

    @Autowired
    private WorkflowManager workflowService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired(required = false)
    private AccessCredentialsProvider accessCredentialsProvider;

    @Autowired
    private ConfigurationService configurationService;

    private NodeStatusMapper nodeStatusMapper = new NodeStatusMapper();

    @Value("${runtime.hera.image}")
    private String image;

    private Integer duration = DEFAULT_DURATION;

    public HeraRuntime() {}

    @Autowired
    public void setDuration(@Value("${runtime.hera.duration}") Integer duration) {
        if (duration != null && duration > MIN_DURATION) {
            // set a minimum duration of 5 minutes
            this.duration = duration;
        } else {
            log.warn("Invalid Hera runtime duration {}. Using default {}", duration, DEFAULT_DURATION);
            this.duration = DEFAULT_DURATION;
        }
    }

    @Override
    public HeraRunSpec build(@NotNull Executable workflow, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        HeraWorkflowSpec workSpec = new HeraWorkflowSpec(workflow.getSpec());
        HeraRunSpec runSpec =
            switch (run.getKind()) {
                case HeraPipelineRunSpec.KIND -> new HeraPipelineRunSpec(run.getSpec());
                case HeraBuildRunSpec.KIND -> new HeraBuildRunSpec(run.getSpec());
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build task spec as defined
        Map<String, Serializable> taskSpec =
            switch (task.getKind()) {
                case HeraPipelineTaskSpec.KIND -> {
                    yield new HeraPipelineTaskSpec(task.getSpec()).toMap();
                }
                case HeraBuildTaskSpec.KIND -> {
                    yield new HeraBuildTaskSpec(task.getSpec()).toMap();
                }
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.forEach(map::putIfAbsent);

        //ensure workflow is not modified
        map.putAll(workSpec.toMap());

        //update run spec
        runSpec.configure(map);

        return runSpec;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        //read base task spec to extract secrets
        K8sFunctionTaskBaseSpec taskSpec = new K8sFunctionTaskBaseSpec();
        taskSpec.configure(run.getSpec());
        Map<String, String> secrets = secretService.getSecretData(run.getProject(), taskSpec.getSecrets());

        // Create string run accessor from task
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case HeraPipelineTaskSpec.KIND -> new HeraPipelineRunner().produce(run);
                case HeraBuildTaskSpec.KIND -> new HeraBuildRunner(image, secrets).produce(run);
                default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
            };

        //extract auth from security context to inflate secured credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        if (auth != null) {
            //get only core credentials from providers
            if (accessCredentialsProvider != null) {
                if (HeraPipelineTaskSpec.KIND.equals(runAccessor.getTask())) {
                    //get custom duration credentials
                    List<Credentials> credentials = List.of(
                        accessCredentialsProvider.get((UserAuthentication<?>) auth, duration)
                    );
                    runnable.setCredentials(credentials);
                } else {
                    //keep standard duration
                    List<Credentials> credentials = List.of(
                        accessCredentialsProvider.get((UserAuthentication<?>) auth)
                    );
                    runnable.setCredentials(credentials);
                }
            } else {
                //keep globally provided access credentials
                List<Credentials> credentials = credentialsService
                    .getCredentials((UserAuthentication<?>) auth)
                    .stream()
                    .filter(c -> c instanceof AccessCredentials)
                    .toList();
                runnable.setCredentials(credentials);
            }
        }

        //inject configuration
        List<Configuration> configurations = configurationService.getConfigurations();
        runnable.setConfigurations(configurations);

        return runnable;
    }

    @Override
    public HeraRunStatus onComplete(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        if (HeraBuildTaskSpec.KIND.equals(runAccessor.getTask())) {
            if (run.getStatus() != null && run.getStatus().containsKey("results")) {
                @SuppressWarnings({ "rawtypes" })
                String raw = (String) ((Map) run.getStatus().get("results")).get("workflow");

                //result is base64 encoded
                String workflow = new String(Base64.getDecoder().decode(raw), StandardCharsets.UTF_8);

                // extract workflow spec part and convert to String again
                try {
                    IoArgoprojWorkflowV1alpha1Workflow argoWorkflow = YamlMapperFactory
                        .yamlObjectMapper()
                        .readValue(workflow, IoArgoprojWorkflowV1alpha1Workflow.class);
                    workflow = YamlMapperFactory.yamlObjectMapper().writeValueAsString(argoWorkflow.getSpec());
                } catch (JsonProcessingException e) {
                    log.error("Error storing Workflow specification", e);
                    return null;
                }

                String wId = runAccessor.getWorkflowId();
                Workflow wf = workflowService.getWorkflow(wId);

                log.debug("update workflow {} spec to use built workflow", wId);

                SourceCode<HeraWorkflowCodeLanguages> build = new SourceCode<>();
                build.setBase64(Base64.getEncoder().encodeToString(workflow.getBytes(StandardCharsets.UTF_8)));
                build.setLang(HeraWorkflowCodeLanguages.yaml);

                HeraWorkflowSpec wfSpec = new HeraWorkflowSpec(wf.getSpec());
                wfSpec.setBuild(build);
                wf.setSpec(wfSpec.toMap());
                workflowService.updateWorkflow(wId, wf, true);
            }
        }

        // Retrieve status when node workflow is completed
        if (
            HeraPipelineTaskSpec.KIND.equals(runAccessor.getTask()) &&
            runnable instanceof K8sArgoWorkflowRunnable k8sArgoWorkflowRunnable
        ) {
            if (
                k8sArgoWorkflowRunnable.getResults() != null &&
                k8sArgoWorkflowRunnable.getResults().get("workflow") != null
            ) {
                HeraRunStatus heraRunStatus = new HeraRunStatus();
                heraRunStatus.configure(run.getStatus());

                try {
                    //deserialize workflow
                    K8sWorkflowObject workflowObject = KubernetesMapper.OBJECT_MAPPER.convertValue(
                        k8sArgoWorkflowRunnable.getResults().get("workflow"),
                        K8sWorkflowObject.class
                    );

                    //extract nodes
                    List<NodeStatusDTO> nodes = nodeStatusMapper.extractNodesFromWorkflow(workflowObject);
                    heraRunStatus.setNodes(nodes);
                } catch (IllegalArgumentException e) {
                    log.error("Error reading Workflow specification", e);
                }

                return heraRunStatus;
            }
        }

        return null;
    }

    @Override
    public HeraRunStatus onRunning(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        if (
            HeraPipelineTaskSpec.KIND.equals(runAccessor.getTask()) &&
            runnable instanceof K8sArgoWorkflowRunnable k8sArgoWorkflowRunnable
        ) {
            if (
                k8sArgoWorkflowRunnable.getResults() != null &&
                k8sArgoWorkflowRunnable.getResults().get("workflow") != null
            ) {
                HeraRunStatus heraRunStatus = new HeraRunStatus();
                heraRunStatus.configure(run.getStatus());

                try {
                    //deserialize workflow
                    K8sWorkflowObject workflowObject = KubernetesMapper.OBJECT_MAPPER.convertValue(
                        k8sArgoWorkflowRunnable.getResults().get("workflow"),
                        K8sWorkflowObject.class
                    );

                    //extract nodes
                    List<NodeStatusDTO> nodes = nodeStatusMapper.extractNodesFromWorkflow(workflowObject);
                    heraRunStatus.setNodes(nodes);
                } catch (IllegalArgumentException e) {
                    log.error("Error reading Workflow specification", e);
                }

                return heraRunStatus;
            }
        }
        return null;
    }

    @Override
    public boolean isSupported(@NotNull Run run) {
        return Arrays.asList(KINDS).contains(run.getKind());
    }
}
