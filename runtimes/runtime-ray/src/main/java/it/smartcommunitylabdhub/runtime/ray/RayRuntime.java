/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.services.ConfigurationService;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionBaseRuntime;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.ray.build.RayBuildRunSpec;
import it.smartcommunitylabdhub.runtime.ray.build.RayBuildRunner;
import it.smartcommunitylabdhub.runtime.ray.build.RayBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.ray.config.RayProperties;
import it.smartcommunitylabdhub.runtime.ray.job.RayJobRunSpec;
import it.smartcommunitylabdhub.runtime.ray.job.RayJobRunner;
import it.smartcommunitylabdhub.runtime.ray.job.RayJobTaskSpec;
import it.smartcommunitylabdhub.runtime.ray.specs.RayFunctionSpec;
import it.smartcommunitylabdhub.runtime.ray.specs.RayRunSpec;
import it.smartcommunitylabdhub.runtime.ray.specs.RayRunStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.Assert;

/**
 * Runtime that executes a Ray python program as a {@code RayJob} on Kubernetes,
 * delegating to the {@link it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayJobFramework}
 * via {@link it.smartcommunitylabdhub.framework.ray.runnables.K8sRayJobRunnable}.
 *
 * <p>Supported task kinds:</p>
 * <ul>
 *   <li>{@link RayJobTaskSpec#KIND ray+job} — execute a Ray job;</li>
 *   <li>{@link RayBuildTaskSpec#KIND ray+build} — bake a custom Ray image
 *       (delegated to the Kaniko framework) and update the function spec
 *       with the produced image.</li>
 * </ul>
 */
@Slf4j
@RuntimeComponent(runtime = RayRuntime.RUNTIME)
public class RayRuntime
    extends K8sFunctionBaseRuntime<RayFunctionSpec, RayRunSpec, RayRunStatus, K8sRunnable>
    implements InitializingBean {

    public static final String RUNTIME = "ray";
    public static final String[] KINDS = { RayJobRunSpec.KIND, RayBuildRunSpec.KIND };

    public static final int UID = 8877;
    public static final int GID = 999;
    public static final String HOME_DIR = "/shared";



    private final RayProperties properties;

    private RayJobRunner jobRunner;
    private RayBuildRunner buildRunner;

    @Autowired
    private SecretService secretService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    public RayRuntime(@Qualifier("rayProperties") RayProperties properties) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.jobRunner = new RayJobRunner(properties, k8sBuilderHelper);
        this.buildRunner = new RayBuildRunner(properties, k8sBuilderHelper);
    }

    @Override
    public RayRunSpec build(@NotNull Function function, @NotNull Task task, @NotNull Run run) {
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        RayFunctionSpec funSpec = new RayFunctionSpec(function.getSpec());
        RayRunSpec runSpec =
            switch (run.getKind()) {
                case RayJobRunSpec.KIND -> new RayJobRunSpec(run.getSpec());
                case RayBuildRunSpec.KIND -> new RayBuildRunSpec(run.getSpec());
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        Map<String, Serializable> taskSpec =
            switch (task.getKind()) {
                case RayJobTaskSpec.KIND -> new RayJobTaskSpec(task.getSpec()).toMap();
                case RayBuildTaskSpec.KIND -> new RayBuildTaskSpec(task.getSpec()).toMap();
                default -> throw new IllegalArgumentException(
                    "Kind not recognized. Cannot retrieve the right builder or specialize Spec for Run and Task."
                );
            };

        //merge: run wins over task; function wins over both
        Map<String, Serializable> map = new HashMap<>();
        map.putAll(runSpec.toMap());
        taskSpec.forEach(map::putIfAbsent);
        map.putAll(funSpec.toMap());

        runSpec.configure(map);
        return runSpec;
    }

    @Override
    public Spec onBuilt(@NotNull Run run) {
        RayRunSpec runSpec = new RayRunSpec(run.getSpec());
        if (runSpec.getInputs() != null && !runSpec.getInputs().isEmpty()) {
            RelationshipsMetadata lineage = RelationshipsMetadata.from(run.getMetadata());
            List<RelationshipDetail> rels = lineage.getRelationships() != null
                ? new ArrayList<>(lineage.getRelationships())
                : new ArrayList<>();

            runSpec
                .getInputs()
                .forEach((name, input) -> {
                    if (
                        rels
                            .stream()
                            .noneMatch(r -> r.getType() == RelationshipName.CONSUMES && r.getDest().equals(input))
                    ) {
                        rels.add(new RelationshipDetail(RelationshipName.CONSUMES, run.getKey(), input));
                    }
                });

            lineage.setRelationships(rels);
            return lineage;
        }

        return null;
    }

    @Override
    public K8sRunnable run(@NotNull Run run) {
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind {} unsupported".formatted(String.valueOf(run.getKind())));
        }

        //read base task spec to extract secrets
        K8sFunctionTaskBaseSpec taskSpec = new K8sFunctionTaskBaseSpec();
        taskSpec.configure(run.getSpec());
        Map<String, String> secrets = secretService.getSecretData(run.getProject(), taskSpec.getSecrets());

        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable =
            switch (runAccessor.getTask()) {
                case RayJobTaskSpec.KIND -> jobRunner.produce(run, secrets);
                case RayBuildTaskSpec.KIND -> buildRunner.produce(run, secrets);
                default -> throw new IllegalArgumentException("Kind not recognized. Cannot retrieve the right Runner");
            };

        //inject credentials from authenticated user, if any
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        if (auth != null) {
            List<Credentials> credentials = credentialsService.getCredentials(auth);
            runnable.setCredentials(credentials);
        }

        //inject configuration providers
        List<Configuration> configurations = configurationService.getConfigurations();
        runnable.setConfigurations(configurations);

        return runnable;
    }

    @Override
    public RayRunStatus onComplete(Run run, RunRunnable runnable) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        //after a build, propagate the produced image into the function spec so
        //subsequent ray+job runs pick it up automatically
        if (runnable instanceof K8sContainerBuilderRunnable builder) {
            String image = builder.getImage();
            String functionId = runAccessor.getFunctionId();
            Function function = functionService.getFunction(functionId);

            log.debug("update function {} spec to use built image: {}", functionId, image);

            RayFunctionSpec funSpec = new RayFunctionSpec(function.getSpec());
            if (image != null && !image.equals(funSpec.getImage())) {
                funSpec.setImage(image);
                function.setSpec(funSpec.toMap());
                functionService.updateFunction(functionId, function, true);
            }
        }

        return null;
    }

    @Override
    public boolean isSupported(@NotNull Run run) {
        return Arrays.asList(KINDS).contains(run.getKind());
    }
}
