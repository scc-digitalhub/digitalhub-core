/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.serve;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.objects.AppProtocol;
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
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeTaskSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

// K8s Deployment for tvm+serve: an init container drops the tvm-so Model into
// TVM_MODEL_DIR and a generic serve image (Go by default, Rust as an alternative) serves it
// over Open Inference v2. The pod runs on a node of the architecture model.so was compiled
// for, where the multi-arch serve image starts in its matching variant.
@Slf4j
public class TvmServeRunner extends TvmBaseRunner {

    private static final int HTTP_PORT = 8080;
    private static final int GRPC_PORT = 9000;

    private final ModelManager modelManager;
    private final FunctionManager functionService;

    public TvmServeRunner(
            TvmProperties properties,
            K8sBuilderHelper k8sBuilderHelper,
            K8sLabelHelper k8sLabelHelper,
            ModelManager modelManager,
            FunctionManager functionService) {
        super(properties, k8sBuilderHelper, k8sLabelHelper);
        this.modelManager = modelManager;
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

        // The compiled model: task.model_path wins over the function's so_model.
        String modelKey = StringUtils.hasText(taskSpec.getModelPath())
                ? taskSpec.getModelPath()
                : (functionSpec != null ? functionSpec.getSoModel() : null);
        if (!StringUtils.hasText(modelKey)) {
            throw new IllegalArgumentException(
                    "tvm+serve needs a compiled .so model: set task.model_path or run tvm+compile first " +
                            "(function.spec.so_model is empty)");
        }
        String modelFolder = TvmRunnerHelper.resolveModelDir(modelKey, modelManager);
        String modelDir = homeDir + "/model";
        String architecture = modelArchitecture(modelKey);

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmServeTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_MODEL_DIR", modelDir));
        envs.add(new CoreEnv("TVM_MODEL_NAME", servedName));
        // Only set when given, so each serve image keeps its own default of one worker.
        addEnv(envs, "TVM_SERVE_WORKERS", taskSpec.getWorkers());
        // Size the TVM thread pools to the pod CPUs unless the task sets them itself.
        Integer threads = threadsPerWorker(requestedCpuCores(taskSpec), taskSpec.getWorkers());
        if (!hasTaskEnv(taskSpec, "TVM_NUM_THREADS")) {
            addEnv(envs, "TVM_NUM_THREADS", threads);
        }

        List<ContextRef> contextRefs = Collections.singletonList(
                TvmRunnerHelper.inputContextRef(modelFolder, "model/"));

        String image = serveImage(taskSpec, properties.getServeImages());

        List<CoreEnv> coreSecrets = createSecrets(secretData);
        List<CoreVolume> volumes = createVolumes(taskSpec);

        List<CorePort> servicePorts = List.of(
                new CorePort(HTTP_PORT, HTTP_PORT, AppProtocol.openinference_v2),
                new CorePort(GRPC_PORT, GRPC_PORT, AppProtocol.openinference_v2));

        List<String> serviceNames = new ArrayList<>();
        if (StringUtils.hasText(taskSpec.getServiceName())) {
            serviceNames.add(funcName + "-" + taskSpec.getServiceName());
        }
        // Add a `<funcName>-latest` alias only when this run is the latest version;
        // best-effort, must not fail serve.
        if (functionService != null) {
            try {
                Function latest = functionService.getLatestFunctionByName(run.getProject(), funcName);
                if (latest != null && latest.getId().equals(taskAccessor.getFunctionId())) {
                    serviceNames.add(funcName + "-latest");
                }
            } catch (Exception e) {
                log.warn("skip '-latest' alias for {}: {}", funcName, e.getMessage());
            }
        }

        // No command/args: the serve image's ENTRYPOINT launches tvm-serve; applyCommon
        // fills in the rest.
        return applyCommon(
                K8sServeRunnable.builder()
                        .nodeSelector(architectureSelector(architecture))
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
                createSecrets(secretData),
                createVolumes(taskSpec),
                contextRefs,
                taskSpec);
    }

    // Node architecture of the compiled Model (see
    // TvmRunnerHelper.modelArchitecture). When
    // it is unknown the pod may land on any node, and the serve image refuses a
    // model.so
    // built for another architecture.
    private String modelArchitecture(String modelKey) {
        if (!modelKey.startsWith(Keys.STORE_PREFIX)) {
            return null;
        }
        String architecture = TvmRunnerHelper.modelArchitecture(
                TvmRunnerHelper.resolveModel(modelKey, modelManager).getSpec());
        if (architecture == null) {
            log.warn("no target architecture in {}: the serve pod can run on any node", modelKey);
        }
        return architecture;
    }

    // resolve the image
    static String serveImage(TvmServeTaskSpec taskSpec, Map<String, String> serveImages) {
        if (StringUtils.hasText(taskSpec.getImage())) {
            return taskSpec.getImage();
        }
        TvmServeRuntime runtime = taskSpec.getServeRuntime() != null ? taskSpec.getServeRuntime() : TvmServeRuntime.go;
        String image = serveImages != null ? serveImages.get(runtime.name()) : null;
        if (!StringUtils.hasText(image)) {
            throw new IllegalArgumentException(
                    "no image configured for serve runtime " + runtime + ": set runtime.tvm.serve-images." + runtime +
                            " or task.image");
        }
        return image;
    }

    // TVM threads for each inference worker. Every worker owns a model copy and TVM
    // gives
    // each of them its own thread pool, so the requested cores are split among the
    // workers. Null when the task requests no CPU: TVM then picks its own default.
    static Integer threadsPerWorker(Integer cpuCores, Integer workers) {
        if (cpuCores == null) {
            return null;
        }
        int workerCount = workers != null && workers > 0 ? workers : 1;
        return Math.max(1, cpuCores / workerCount);
    }
}
