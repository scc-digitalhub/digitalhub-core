/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.serve;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmBaseRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmRunnerHelper;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeTaskSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Builds the K8s serving deployment for tvm+serve, exposing a compiled tvm-so
// Model behind the native tvm-serve server. This is model-centric rather than a
// baked per-model image: an init container downloads the tvm-so Model's S3
// folder (model.so + metadata.json) into TVM_MODEL_DIR, and a generic, swappable
// base serve image (default: the Rust tvm-runtime-rust) serves it. Ports are
// fixed and the Service is left to the framework, so no custom Service is built.
@Slf4j
@Component
public class TvmServeRunner extends TvmBaseRunner {

    private static final int HTTP_PORT = 8080;
    private static final int GRPC_PORT = 9000;

    private final ModelManager modelService;
    private final FunctionManager functionService;

    public TvmServeRunner(
            TvmProperties properties,
            K8sBuilderHelper k8sBuilderHelper,
            ModelManager modelService,
            FunctionManager functionService) {
        super(properties, k8sBuilderHelper);
        this.modelService = modelService;
        this.functionService = functionService;
    }

    public K8sRunnable produce(Run run, Map<String, String> secretData) {
        TvmServeRunSpec runSpec = TvmServeRunSpec.with(run.getSpec());
        TvmFunctionSpec functionSpec = runSpec.getFunctionSpec();
        TvmServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        String funcName = taskAccessor.getFunction();

        String servedName = StringUtils.hasText(taskSpec.getServedName())
                ? taskSpec.getServedName()
                : TvmRunnerHelper.cleanName(funcName);

        // Pick the .so model to serve: an explicit task.model_path wins,
        // otherwise use the so_model a prior tvm+compile recorded on the function.
        String modelKey = StringUtils.hasText(taskSpec.getModelPath())
                ? taskSpec.getModelPath()
                : (functionSpec != null ? functionSpec.getSoModel() : null);
        if (!StringUtils.hasText(modelKey)) {
            throw new IllegalArgumentException(
                    "tvm+serve needs a compiled .so model: set task.model_path or run tvm+compile first " +
                            "(function.spec.so_model is empty)");
        }
        // Resolve store:// to the .so folder's S3 location (trailing slash so the init
        // container pulls the whole directory: model.so + metadata.json + optional params).
        String s3SoPath = TvmRunnerHelper.resolveModelDir(modelKey, modelService);

        // The init container drops the model under <homeDir>/model; tvm-serve is
        // pointed at it via TVM_MODEL_DIR and loads model.so + metadata.json there.
        String modelDir = homeDir + "/model";

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmServeTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_MODEL_DIR", modelDir));
        envs.add(new CoreEnv("TVM_MODEL_NAME", servedName));
        // Optional per-pod worker count. Both serve backends read TVM_SERVE_WORKERS
        // with the same meaning (rust = pool of N threads, each its own model copy;
        // go = nuclio numWorkers). Only set when specified, so each image keeps its
        // own default of 1.
        if (taskSpec.getWorkers() != null) {
            envs.add(new CoreEnv("TVM_SERVE_WORKERS", String.valueOf(taskSpec.getWorkers())));
        }
        // tvm-serve listens on REST 8080 / gRPC 9000 by default; these ports are
        // declared on the runnable below.

        List<ContextRef> contextRefs = Collections.singletonList(
                TvmRunnerHelper.inputContextRef(s3SoPath, "model/"));

        // Serve image: task override, else the configured (swappable) base image.
        String image = resolveImage(
                taskSpec.getImage(),
                properties.getServe(),
                "no serve image configured: set task.image or runtime.tvm.serve");

        List<CoreEnv> coreSecrets = createSecrets(secretData);
        List<CoreVolume> volumes = createVolumes(taskSpec);

        List<CorePort> servicePorts = List.of(
                new CorePort(HTTP_PORT, HTTP_PORT),
                new CorePort(GRPC_PORT, GRPC_PORT));

        List<String> serviceNames = new ArrayList<>();
        if (StringUtils.hasText(taskSpec.getServiceName())) {
            serviceNames.add(funcName + "-" + taskSpec.getServiceName());
        }
        // Add a `<funcName>-latest` service alias only when this run belongs to the
        // function's current latest version. Best-effort: the latest lookup can
        // throw (e.g. NoSuchEntityException if a force-update left the index
        // inconsistent), and a missing alias must not fail the serve.
        if (functionService != null) {
            try {
                Function latest = functionService.getLatestFunction(run.getProject(), funcName);
                if (latest != null && latest.getId().equals(taskAccessor.getFunctionId())) {
                    serviceNames.add(funcName + "-latest");
                }
            } catch (Exception e) {
                log.warn("skip '-latest' alias for {}: {}", funcName, e.getMessage());
            }
        }

        // No command/args on the builder: the serve image's ENTRYPOINT launches tvm-serve.
        // Only the serve-specific fields are set here; applyCommon fills in the rest.
        return applyCommon(
                K8sServeRunnable.builder()
                        .replicas(taskSpec.getReplicas())
                        .servicePorts(servicePorts)
                        .serviceType(taskSpec.getServiceType())
                        .serviceNames(serviceNames.isEmpty() ? null : serviceNames)
                        .build(),
                run,
                TvmServeTaskSpec.KIND,
                funcName,
                image,
                envs,
                coreSecrets,
                volumes,
                contextRefs,
                taskSpec);
    }
}
