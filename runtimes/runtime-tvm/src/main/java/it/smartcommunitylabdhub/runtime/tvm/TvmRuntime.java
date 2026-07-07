/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm;

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
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.build.TvmBuildRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.build.TvmFrontend;
import it.smartcommunitylabdhub.runtime.tvm.runners.compile.TvmCompileRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.serve.TvmServeRunner;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.status.TvmRunStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

// DigitalHub runtime that integrates Apache TVM as three K8s tasks:
//   tvm+build:   source ONNX -> Relax IR Model (Job)
//   tvm+compile: Relax IR -> model.so Model for a hardware target (Job)
//   tvm+serve:   deploy the .so Model behind native tvm-serve (OpenInference v2)
// serve is model-centric: an init container downloads the tvm-so Model into a
// configurable base serve image. The rust runtime image is the default but is
// selectable; a native Go serverless runtime is available as an alternative.
@Slf4j
@RuntimeComponent(runtime = TvmRuntime.RUNTIME)
public class TvmRuntime
        extends K8sFunctionBaseRuntime<TvmFunctionSpec, TvmRunSpec, TvmRunStatus, K8sRunnable>
        implements InitializingBean {

    public static final String RUNTIME = "tvm";
    public static final String[] KINDS = { TvmBuildRunSpec.KIND, TvmCompileRunSpec.KIND, TvmServeRunSpec.KIND };

    public static final int UID = 1000;
    public static final int GID = 1000;
    public static final String HOME_DIR = "/shared";

    private final TvmProperties properties;

    private TvmBuildRunner buildRunner;

    private final List<TvmFrontend> frontends;

    @Autowired
    private TvmCompileRunner compileRunner;

    @Autowired
    private TvmServeRunner serveRunner;

    @Autowired
    private SecretService secretService;

    @Autowired
    private ModelManager modelService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private FunctionManager functionService;

    public TvmRuntime(
            @Qualifier("tvmProperties") TvmProperties properties,
            List<TvmFrontend> frontends) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties;
        this.frontends = frontends;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.buildRunner = new TvmBuildRunner(frontends, modelService);
    }

    @Override
    public TvmRunSpec build(@NotNull Function function, @NotNull Task task, @NotNull Run run) {
        if (!isSupported(run)) {
            throw new IllegalArgumentException("Run kind " + run.getKind() + " unsupported");
        }

        TvmFunctionSpec funSpec = TvmFunctionSpec.with(function.getSpec());
        TvmRunSpec runSpec = switch (run.getKind()) {
            case TvmBuildRunSpec.KIND -> TvmBuildRunSpec.with(run.getSpec());
            case TvmCompileRunSpec.KIND -> TvmCompileRunSpec.with(run.getSpec());
            case TvmServeRunSpec.KIND -> TvmServeRunSpec.with(run.getSpec());
            default -> throw new IllegalArgumentException("Unknown run kind: " + run.getKind());
        };

        Map<String, Serializable> taskMap = switch (task.getKind()) {
            case TvmBuildTaskSpec.KIND -> TvmBuildTaskSpec.with(task.getSpec()).toMap();
            case TvmCompileTaskSpec.KIND -> TvmCompileTaskSpec.with(task.getSpec()).toMap();
            case TvmServeTaskSpec.KIND -> TvmServeTaskSpec.with(task.getSpec()).toMap();
            default -> throw new IllegalArgumentException("Unknown task kind: " + task.getKind());
        };

        // Merge precedence: run spec first, task fills only what the run left unset,
        // then the function spec overrides everything (function is the source of truth).
        Map<String, Serializable> map = new LinkedHashMap<>();
        map.putAll(runSpec.toMap());
        taskMap.forEach(map::putIfAbsent);
        map.putAll(funSpec.toMap());

        runSpec.configure(map);
        return runSpec;
    }

    @Override
    public Spec onBuilt(@NotNull Run run) {
        // Record CONSUMES lineage: each declared run input becomes a relationship so
        // the platform can trace which artifacts this run consumed.
        TvmRunSpec runSpec = new TvmRunSpec(run.getSpec());
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
                        RelationshipDetail dr = new RelationshipDetail(RelationshipName.CONSUMES, run.getKey(), input);
                        rels.add(dr);
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
            throw new IllegalArgumentException("Run kind " + run.getKind() + " unsupported");
        }

        K8sFunctionTaskBaseSpec taskSpec = new K8sFunctionTaskBaseSpec();
        taskSpec.configure(run.getSpec());
        Map<String, String> secrets = secretService.getSecretData(run.getProject(), taskSpec.getSecrets());

        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());

        K8sRunnable runnable = switch (runAccessor.getTask()) {
            case TvmBuildTaskSpec.KIND -> buildRunner.produce(run, secrets);
            case TvmCompileTaskSpec.KIND -> compileRunner.produce(run, secrets);
            case TvmServeTaskSpec.KIND -> serveRunner.produce(run, secrets);
            default -> throw new IllegalArgumentException(
                    "Unknown task kind in run spec: " + runAccessor.getTask());
        };

        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        if (auth != null) {
            List<Credentials> credentials = credentialsService.getCredentials(auth);
            runnable.setCredentials(credentials);
        }
        List<Configuration> configurations = configurationService.getConfigurations();
        runnable.setConfigurations(configurations);

        return runnable;
    }

    @Override
    public TvmRunStatus onComplete(@NotNull Run run, RunRunnable runnable) {
        try {
            return switch (run.getKind()) {
                case TvmBuildRunSpec.KIND -> writeModelKeyBack(run, "ir_module", TvmFunctionSpec::setIrModel, "ir_model");
                case TvmCompileRunSpec.KIND -> writeModelKeyBack(
                    run,
                    "compiled_so",
                    TvmFunctionSpec::setSoModel,
                    "so_model"
                );
                default -> null;
            };
        } catch (Exception e) {
            log.error("error in onComplete for run {}: {}", run.getId(), e.getMessage(), e);
        }
        return null;
    }

    // Shared "job finished" handler for build and compile. A finished job publishes
    // its output Model and reports the key under status.outputs.<outputKey>; write
    // that key back onto the parent function's spec (via <setter>) so the next task
    // in the chain can resolve it automatically, and surface it on the run status.
    // build -> ir_module -> function.spec.ir_model; compile -> compiled_so -> so_model.
    private TvmRunStatus writeModelKeyBack(
        Run run,
        String outputKey,
        BiConsumer<TvmFunctionSpec, String> setter,
        String specFieldLabel
    ) {
        RunSpecAccessor runAccessor = RunSpecAccessor.with(run.getSpec());
        String funcName = runAccessor.getFunction();
        String funcId = runAccessor.getFunctionId();

        String modelKey = readOutput(run, outputKey);
        if (!StringUtils.hasText(modelKey)) {
            log.warn("run {} completed but no {} key in status.outputs: skip function update", run.getId(), outputKey);
            return null;
        }

        if (functionService != null && StringUtils.hasText(funcId)) {
            try {
                Function fn = functionService.getFunction(funcId);
                TvmFunctionSpec tvmFunctionSpec = TvmFunctionSpec.with(fn.getSpec());
                setter.accept(tvmFunctionSpec, modelKey);
                fn.setSpec(tvmFunctionSpec.toMap());
                functionService.updateFunction(funcId, fn, true);
                log.info("function {} spec updated: {}={}", funcName, specFieldLabel, modelKey);
            } catch (Exception e) {
                log.warn("failed to update function {} spec.{}: {}", funcName, specFieldLabel, e.getMessage());
            }
        }

        TvmRunStatus status = TvmRunStatus.builder().build();
        status.setModelKey(modelKey);
        return status;
    }

    // Null-safe read of a single String value out of the run's status.outputs map.
    private String readOutput(Run run, String key) {
        if (run.getStatus() == null)
            return null;
        Object outs = run.getStatus().get("outputs");
        if (outs instanceof Map<?, ?> m) {
            Object v = m.get(key);
            if (v instanceof String s)
                return s;
        }
        return null;
    }

    @Override
    public boolean isSupported(@NotNull Run run) {
        return Arrays.asList(KINDS).contains(run.getKind());
    }
}
