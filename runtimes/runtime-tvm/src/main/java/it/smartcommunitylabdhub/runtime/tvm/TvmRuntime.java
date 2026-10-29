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
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
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
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceDetails;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.objects.AppProtocol;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import it.smartcommunitylabdhub.relationships.RelationshipsMetadata;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.build.TvmBuildRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.compile.TvmCompileRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.serve.TvmServeRunner;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileOptions;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.status.InferenceV2Service;
import it.smartcommunitylabdhub.runtime.tvm.status.TvmRunStatus;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

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

    private TvmBuildRunner buildRunner;
    private TvmCompileRunner compileRunner;
    private TvmServeRunner serveRunner;
    private TvmProperties properties;

    // Serialize TVM callbacks in this JVM; external edits are not covered.
    private final ReentrantLock functionUpdateLock = new ReentrantLock();
    private static final int MODEL_UPDATE_ATTEMPTS = 3;

    @Autowired
    private SecretService secretService;

    @Autowired
    private CredentialsService credentialsService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private FunctionManager functionService;

    @Autowired
    private ModelManager modelManager;

    public TvmRuntime(@Qualifier("tvmProperties") TvmProperties properties) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        this.buildRunner = new TvmBuildRunner(properties, k8sBuilderHelper, k8sLabelHelper, modelManager);
        this.compileRunner = new TvmCompileRunner(properties, k8sBuilderHelper, k8sLabelHelper, modelManager);
        this.serveRunner = new TvmServeRunner(
                properties,
                k8sBuilderHelper,
                k8sLabelHelper,
                modelManager,
                functionService);
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

        // The task supplies common settings; TVM options belong to the run.
        boolean compile = runSpec instanceof TvmCompileRunSpec;
        Map<String, Serializable> map = new LinkedHashMap<>(runSpec.toMap());
        if (compile) {
            map = TvmCompileOptions.flatten(map);
        }
        taskMap.forEach(map::putIfAbsent);
        map.putAll(funSpec.toMap());

        if (compile) {
            map.putIfAbsent("tuning_mode", "off");
        }

        runSpec.configure(map);
        if (runSpec instanceof TvmServeRunSpec serveSpec) {
            // Pin the model in this run, not in the reusable task.
            String model = TvmServeRunner.resolveModelKey(
                    serveSpec.getModelPath(),
                    funSpec.getSoModel());
            serveSpec.setModelPath(model);
        }
        return runSpec;
    }

    @Override
    public Spec onBuilt(@NotNull Run run) {
        TvmRunSpec runSpec = new TvmRunSpec(run.getSpec());
        if (runSpec.getInputs() != null && !runSpec.getInputs().isEmpty()) {
            RelationshipsMetadata lineage = RelationshipsMetadata.from(run.getMetadata());
            List<RelationshipDetail> rels = lineage.getRelationships() != null
                    ? new ArrayList<>(lineage.getRelationships())
                    : new ArrayList<>();

            runSpec
                    .getInputs()
                    .forEach((name, input) -> {
                        if (rels
                                .stream()
                                .noneMatch(
                                        r -> r.getType() == RelationshipName.CONSUMES && r.getDest().equals(input))) {
                            RelationshipDetail dr = new RelationshipDetail(RelationshipName.CONSUMES, run.getKey(),
                                    input);
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
            default -> throw new IllegalArgumentException("Unknown task kind in run spec: " + runAccessor.getTask());
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
    public TvmRunStatus onRunning(@NotNull Run run, RunRunnable runnable) {
        TvmRunStatus status = TvmRunStatus.with(run.getStatus());
        if (runnable instanceof K8sRunnable k8sRunnable) {
            status.setState(k8sRunnable.getState());
            status.setMessage(k8sRunnable.getError());
        }
        if (!TvmServeRunSpec.KIND.equals(run.getKind()) || status.getService() == null) {
            return status;
        }

        K8sServiceInfo service = status.getService();
        String baseUrl = service.getUrl();
        TvmServeRunSpec spec = TvmServeRunSpec.with(run.getSpec());
        String model = TvmServeRunner.resolveServedName(
            spec.getServedName(), RunSpecAccessor.with(run.getSpec()).getFunction()
        );
        if (!StringUtils.hasText(baseUrl) || !StringUtils.hasText(model)) {
            return status;
        }

        String v2Url = baseUrl + "/v2";
        List<K8sServiceDetails> urls = new ArrayList<>(service.getUrls() != null ? service.getUrls() : List.of());
        if (urls.stream().noneMatch(endpoint -> v2Url.equals(endpoint.getUrl()))) {
            urls.add(new K8sServiceDetails(v2Url, TvmServeRunner.HTTP_PORT, AppProtocol.openinference_v2.name()));
        }
        service.setUrls(urls);

        status.setInferenceV2(InferenceV2Service.from(baseUrl, model));
        return status;
    }

    @Override
    public TvmRunStatus onComplete(@NotNull Run run, RunRunnable runnable) {
        // The lifecycle marks the run ERROR if saving its model reference fails.
        return switch (run.getKind()) {
            case TvmBuildRunSpec.KIND -> writeModelKeyBack(run, "ir_module", "ir_model");
            case TvmCompileRunSpec.KIND -> writeModelKeyBack(run, "compiled_so", "so_model");
            default -> null;
        };
    }

    private TvmRunStatus writeModelKeyBack(Run run, String outputKey, String specField) {
        String functionId = RunSpecAccessor.with(run.getSpec()).getFunctionId();
        String modelKey = readOutput(run, outputKey);
        if (!StringUtils.hasText(modelKey)) {
            throw new CoreRuntimeException(
                    "TVM run %s has no status.outputs.%s; function.spec.%s was not updated".formatted(
                            run.getId(),
                            outputKey,
                            specField));
        }
        String failureMessage = "TVM run %s produced model %s but could not update function %s spec.%s".formatted(
                run.getId(),
                modelKey,
                functionId,
                specField);
        if (functionService == null || !StringUtils.hasText(functionId)) {
            throw new CoreRuntimeException(failureMessage + ": function service or exact version is missing");
        }

        try {
            if (!functionUpdateLock.tryLock(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for another TVM update");
            }
            try {
                for (int attempt = 1; attempt <= MODEL_UPDATE_ATTEMPTS; attempt++) {
                    try {
                        saveModelReference(run, functionId, specField, modelKey);
                        break;
                    } catch (RuntimeException e) {
                        if (e instanceof NoSuchEntityException ||
                                e instanceof IllegalArgumentException ||
                                attempt == MODEL_UPDATE_ATTEMPTS) {
                            throw e;
                        }
                        log.warn(
                                "retry TVM function update for run {} (attempt {} failed): {}",
                                run.getId(),
                                attempt,
                                e.getMessage());
                        Thread.sleep(200L * attempt);
                    }
                }
            } finally {
                functionUpdateLock.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CoreRuntimeException(failureMessage + ": interrupted", e);
        } catch (RuntimeException e) {
            throw new CoreRuntimeException(failureMessage + ": " + e.getMessage(), e);
        }

        return TvmRunStatus.builder().modelKey(modelKey).build();
    }

    private void saveModelReference(Run run, String functionId, String field, String modelKey) {
        // Re-read the exact version on every attempt, never the run's old snapshot.
        Function current = functionService.getFunction(functionId);
        if (current == null ||
                !functionId.equals(current.getId()) ||
                !RUNTIME.equals(current.getKind()) ||
                !Objects.equals(run.getProject(), current.getProject()) ||
                !Objects.equals(RunSpecAccessor.with(run.getSpec()).getFunction(), current.getName())) {
            throw new IllegalArgumentException("the function does not match the run reference");
        }
        if (modelKey.equals(current.getSpec().get(field)))
            return;

        Function saved = functionService.updateFunction(functionId, withModelReference(current, field, modelKey), true);
        if (saved == null || saved.getSpec() == null || !modelKey.equals(saved.getSpec().get(field))) {
            throw new IllegalStateException("the saved function did not confirm the model reference");
        }
        log.info("function {} spec updated: {}={}", current.getKey(), field, modelKey);
    }

    private static Function withModelReference(Function function, String field, String modelKey) {
        // Copy only what changes; do not mutate a possibly cached service DTO.
        Map<String, Serializable> spec = new LinkedHashMap<>(function.getSpec());
        spec.put(field, modelKey);
        return Function.builder()
                .id(function.getId())
                .kind(function.getKind())
                .name(function.getName())
                .project(function.getProject())
                .key(function.getKey())
                .user(function.getUser())
                .metadata(function.getMetadata())
                .status(function.getStatus())
                .spec(spec)
                .build();
    }

    private static String readOutput(Run run, String key) {
        if (run.getStatus() == null || !(run.getStatus().get("outputs") instanceof Map<?, ?> outputs))
            return null;
        return outputs.get(key) instanceof String value ? value : null;
    }

    @Override
    public boolean isSupported(@NotNull Run run) {
        return Arrays.asList(KINDS).contains(run.getKind());
    }
}
