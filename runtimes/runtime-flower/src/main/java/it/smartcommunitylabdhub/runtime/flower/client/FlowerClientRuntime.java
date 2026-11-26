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

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.functions.Function;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientBuildRunSpec;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientDeployRunSpec;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientDeployTaskSpec;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientFunctionSpec;
import it.smartcommunitylabdhub.runtime.flower.client.specs.FlowerClientRunSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerRunStatus;
import it.smartcommunitylabdhub.tasks.Task;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.BindException;

@Slf4j
@RuntimeComponent(runtime = FlowerClientRuntime.RUNTIME)
public class FlowerClientRuntime
    extends K8sFunctionBaseRuntime<FlowerClientFunctionSpec, FlowerClientRunSpec, FlowerRunStatus, K8sRunnable>
{

    public static final String RUNTIME = "flower-client";
    public static final String[] KINDS = { FlowerClientBuildRunSpec.KIND, FlowerClientDeployRunSpec.KIND };

    @Autowired
    private SecretService secretService;

    @Autowired
    private FunctionManager functionService;



    @Autowired
    @Qualifier("flowerImages")
    private Map<String, String> images;

    @Value("${runtime.flower.user-id}")
    private Integer userId;

    @Value("${runtime.flower.group-id}")
    private Integer groupId;

    @Override
    public FlowerClientRunSpec build(@NotNull Function function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        FlowerClientFunctionSpec funSpec = new FlowerClientFunctionSpec(function.getSpec());

        //build run spec as defined
        FlowerClientRunSpec runSpec = switch (run.getKind()) {
            case FlowerClientDeployRunSpec.KIND -> {
                yield new FlowerClientDeployRunSpec(run.getSpec());
            }
            case FlowerClientBuildRunSpec.KIND -> {
                yield new FlowerClientBuildRunSpec(run.getSpec());
            }
            default -> throw new IllegalArgumentException(
                "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
            );
        };

        //build task spec as defined
        Map<String, Serializable> taskSpec = switch (task.getKind()) {
            case FlowerClientDeployTaskSpec.KIND -> {
                yield new FlowerClientDeployTaskSpec(task.getSpec()).toMap();
            }
            case FlowerClientBuildTaskSpec.KIND -> {
                yield new FlowerClientBuildTaskSpec(task.getSpec()).toMap();
            }
            default -> throw new IllegalArgumentException(
                "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
            );
        };

        //build run merging task spec overrides
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.forEach(map::putIfAbsent);
        //ensure function is not modified
        map.putAll(funSpec.toMap());

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

        K8sRunnable runnable = switch (runAccessor.getTask()) {
            case FlowerClientDeployTaskSpec.KIND -> new FlowerClientDeployRunner(
                images.get("client"),
                userId,
                groupId,
                secretService,
                k8sBuilderHelper,
                k8sLabelHelper
            ).produce(run, secrets);
            case FlowerClientBuildTaskSpec.KIND -> new FlowerClientBuildRunner(
                images.get("client"),
                "flower-supernode",
                k8sBuilderHelper,
                k8sLabelHelper
            ).produce(run, secrets);
            default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
        };

        return runnable;
    }

    @Override
    public FlowerRunStatus onComplete(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        //update image name after build
        if (runnable instanceof K8sContainerBuilderRunnable) {
            try {
                String image = ((K8sContainerBuilderRunnable) runnable).getImage();

                String functionId = runAccessor.getFunctionId();
                Function function = functionService.get(functionId);

                log.debug("update function {} spec to use built image: {}", functionId, image);

                FlowerClientFunctionSpec funSpec = new FlowerClientFunctionSpec(function.getSpec());
                if (!image.equals(funSpec.getImage())) {
                    funSpec.setImage(image);
                    function.setSpec(funSpec.toMap());
                    functionService.update(functionId, function, true);
                }
            } catch (StoreException | IllegalArgumentException | NoSuchEntityException | BindException ex) {
                log.error("Error updating workflow: {}", ex.getMessage());
            }
        }

        return null;
    }

    @Override
    public boolean isSupported(@NotNull Run run) {
        return Arrays.asList(KINDS).contains(run.getKind());
    }
}
