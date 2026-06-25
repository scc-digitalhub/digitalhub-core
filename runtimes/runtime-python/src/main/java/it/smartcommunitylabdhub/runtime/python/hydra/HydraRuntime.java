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

package it.smartcommunitylabdhub.runtime.python.hydra;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.persistence.RunEntity;
import it.smartcommunitylabdhub.runtime.python.config.PythonProperties;
import it.smartcommunitylabdhub.runtime.python.hydra.runners.HydraBuildRunner;
import it.smartcommunitylabdhub.runtime.python.hydra.runners.HydraJobRunner;
import it.smartcommunitylabdhub.runtime.python.hydra.runners.HydraSubtaskRunner;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraBuildRunSpec;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraJobRunSpec;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraJobTaskSpec;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraRunSpec;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraRunStatus;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraSubtaskRunSpec;
import it.smartcommunitylabdhub.runtime.python.hydra.specs.HydraSubtaskTaskSpec;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

@Slf4j
@RuntimeComponent(runtime = HydraRuntime.RUNTIME)
public class HydraRuntime
    extends K8sFunctionBaseRuntime<HydraFunctionSpec, HydraRunSpec, HydraRunStatus, K8sRunnable>
    implements InitializingBean {


    public static final String RUNTIME = "hydra";
    public static final String[] KINDS = { HydraJobRunSpec.KIND, HydraBuildRunSpec.KIND, HydraSubtaskRunSpec.KIND };

    private final PythonProperties properties;

    private HydraBuildRunner buildRunner;
    private HydraJobRunner jobRunner;
    private HydraSubtaskRunner subtaskRunner;

    @Autowired
    private SecretService secretService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private SearchableEntityRepository<RunEntity, Run> entityRepository;

    public HydraRuntime(@Qualifier("hydraProperties") PythonProperties properties) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.buildRunner = new HydraBuildRunner(properties, k8sBuilderHelper);
        this.jobRunner = new HydraJobRunner(properties, k8sBuilderHelper);
        this.subtaskRunner = new HydraSubtaskRunner(properties, k8sBuilderHelper);
    }

    @Override
    public HydraRunSpec build(@NotNull Function function, @NotNull Task task, @NotNull Run run) {
        //check run kind
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        HydraFunctionSpec funSpec = new HydraFunctionSpec(function.getSpec());
        HydraRunSpec runSpec =
            switch (run.getKind()) {
                case HydraJobRunSpec.KIND -> new HydraJobRunSpec(run.getSpec());
                case HydraBuildRunSpec.KIND -> new HydraBuildRunSpec(run.getSpec());
                case HydraSubtaskRunSpec.KIND -> new HydraSubtaskRunSpec(run.getSpec());
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //build task spec as defined
        Map<String, Serializable> taskSpec =
            switch (task.getKind()) {
                case HydraJobTaskSpec.KIND -> {
                    yield new HydraJobTaskSpec(task.getSpec()).toMap();
                }
                case HydraBuildTaskSpec.KIND -> {
                    yield new HydraBuildTaskSpec(task.getSpec()).toMap();
                }
                case HydraSubtaskTaskSpec.KIND -> {
                    yield new HydraSubtaskTaskSpec(task.getSpec()).toMap();
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

        //reconfigure run spec
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
                case HydraJobTaskSpec.KIND -> jobRunner.produce(run, secrets);
                case HydraBuildTaskSpec.KIND -> buildRunner.produce(run, secrets);
                case HydraSubtaskTaskSpec.KIND -> subtaskRunner.produce(run, secrets);
                default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
            };

        //extract auth from security context to inflate secured credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        if (auth != null) {
            //get credentials from providers
            List<Credentials> credentials = credentialsService.getCredentials(auth);
            runnable.setCredentials(credentials);
        }

        //inject configuration
        List<Configuration> configurations = configurationService.getConfigurations();
        runnable.setConfigurations(configurations);

        return runnable;
    }

    @Override
    public HydraRunStatus onComplete(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        //update image name after build
        if (runnable instanceof K8sContainerBuilderRunnable) {
            String image = ((K8sContainerBuilderRunnable) runnable).getImage();

            String functionId = runAccessor.getFunctionId();
            Function function = functionService.getFunction(functionId);

            log.debug("update function {} spec to use built image: {}", functionId, image);

            HydraFunctionSpec funSpec = new HydraFunctionSpec(function.getSpec());
            if (!image.equals(funSpec.getImage())) {
                funSpec.setImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            }
        }

        return null;
    }

    @Override
    @Nullable
    public K8sRunnable delete(@NotNull Run run) {
        K8sRunnable k8sRunnable = super.delete(run);
        if (run.getKind().equals(HydraJobRunSpec.KIND)) {
            RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());
            String functionId = runAccessor.getFunctionId();
            // find subtask task
            Optional<Task> task = functionService.getTasksByFunctionId(functionId).stream().filter(t -> t.getKind().equals(HydraSubtaskTaskSpec.KIND)).findFirst();
            if (task.isPresent()) {
                // find subtask runs and delete them
                try {
                    List<Run> subtaskRuns = findSubtaskRuns(runAccessor.getProject(), task.get(), run.getId());
                    for (Run r : subtaskRuns) {
                        entityRepository.delete(r.getId());
                    }
                } catch (StoreException e) {
                    log.error("Error deleting subtasks run {}", run.getId(), e);
                }
            }

        }
        return k8sRunnable;
    }


    private List<Run> findSubtaskRuns(String project, Task task, String id) throws StoreException {
        //define a spec for runs building task path
        String path = (task.getKind() + "://" +project + "/" + task.getId());
        Specification<RunEntity> where = Specification.allOf(
            CommonSpecification.projectEquals(task.getProject()),
            createTaskSpecification(path)
        );
        //fetch all runs ordered by created DESC
        Specification<RunEntity> specification = (root, query, builder) -> {
            return where.toPredicate(root, query, builder);
        };

        List<Run> runs = entityRepository.searchAll(specification).stream().filter(r -> {
            HydraSubtaskRunSpec runSpec = new HydraSubtaskRunSpec(r.getSpec());
            return id.equals(runSpec.getJobRef());
        }).toList();

        return runs;
    }

    private Specification<RunEntity> createTaskSpecification(String task) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("task"), task);
    }


    @Override
    public boolean isSupported(@NotNull Run run) {
        return Arrays.asList(KINDS).contains(run.getKind());
    }

}
