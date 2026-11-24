/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.serve;

import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.objects.AppProtocol;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.models.Model;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmBaseRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmRunnerHelper;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeTaskSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

@Slf4j
public class TvmServeRunner extends TvmBaseRunner {

    public static final int HTTP_PORT = 8080;
    public static final int GRPC_PORT = 9000;

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
        TvmServeTaskSpec taskSpec = runSpec.getTaskServeSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(run.getSpec());
        String funcName = taskAccessor.getFunction();

        String servedName = resolveServedName(runSpec.getServedName(), funcName);

        String modelKey = resolveModelKey(runSpec.getSoModelPath());
        Model model = resolveCompiledModel(modelKey);
        String modelFolder = TvmRunnerHelper.modelDir(TvmRunnerHelper.modelPath(model, modelKey));
        String modelDir = homeDir + "/model";
        String architecture = modelArchitecture(modelKey, model);

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmServeTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_MODEL_DIR", modelDir));
        envs.add(new CoreEnv("TVM_MODEL_NAME", servedName));
        addEnv(envs, "TVM_SERVE_WORKERS", runSpec.getWorkers());
        Integer threads = threadsPerWorker(requestedCpuCores(taskSpec), runSpec.getWorkers());
        if (!hasTaskEnv(taskSpec, "TVM_NUM_THREADS")) {
            addEnv(envs, "TVM_NUM_THREADS", threads);
        }

        List<ContextRef> contextRefs = Collections.singletonList(
                TvmRunnerHelper.inputContextRef(modelFolder, "model/"));

        String image = resolveImage(
                runSpec.getImage(),
                properties.getServe(),
                "no serve image configured: set run.image or runtime.tvm.serve");

        List<CorePort> servicePorts = List.of(
                new CorePort(HTTP_PORT, HTTP_PORT, AppProtocol.openinference_v2),
                new CorePort(GRPC_PORT, GRPC_PORT, AppProtocol.openinference_v2));

        List<String> serviceNames = new ArrayList<>();
        if (StringUtils.hasText(runSpec.getServiceName())) {
            serviceNames.add(funcName + "-" + runSpec.getServiceName());
        }
        // Alias registration is best-effort and must not prevent serving.
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

        // Leave command/args unset to use the serving image's ENTRYPOINT.
        return applyCommon(
                K8sServeRunnable.builder()
                        .nodeSelector(architectureSelector(architecture))
                        .replicas(runSpec.getReplicas())
                        .servicePorts(servicePorts)
                        .serviceType(runSpec.getServiceType())
                        .serviceNames(serviceNames.isEmpty() ? null : serviceNames)
                        .build(),
                run,
                TvmServeTaskSpec.KIND,
                funcName,
                image,
                envs,
                secretData,
                contextRefs,
                taskSpec);
    }

    public static String resolveServedName(String servedName, String functionName) {
        return StringUtils.hasText(servedName) ? servedName : functionName;
    }

    private Model resolveCompiledModel(String modelKey) {
        Model model = TvmRunnerHelper.resolveModel(modelKey, modelManager);
        KeyAccessor key = KeyAccessor.with(modelKey);
        if (!Objects.equals(key.getProject(), model.getProject()) ||
                !Objects.equals(key.getKind(), model.getKind()) ||
                !Objects.equals(key.getName(), model.getName()) ||
                !Objects.equals(key.getId(), model.getId())) {
            throw new IllegalArgumentException("so_model_path does not match the resolved tvm-so Model: " + modelKey);
        }
        return model;
    }

    public static String resolveModelKey(String soModelPath) {
        if (!StringUtils.hasText(soModelPath)) {
            throw new IllegalArgumentException(
                    "tvm+serve requires run.so_model_path: use status.outputs.compiled_so from the selected tvm+compile Run");
        }
        if (!soModelPath.matches(TvmServeRunSpec.COMPILED_PATH_REGEX)) {
            throw new IllegalArgumentException(
                    "tvm+serve so_model_path must be a versioned tvm-so Model key from status.outputs.compiled_so");
        }
        return soModelPath;
    }

    private String modelArchitecture(String modelKey, Model model) {
        if (model == null) {
            return null;
        }
        String architecture = TvmRunnerHelper.modelArchitecture(model.getSpec());
        if (architecture == null) {
            log.warn("no target architecture in {}: the serve pod can run on any node", modelKey);
        }
        return architecture;
    }

    // Each worker owns a thread pool: split the pod's CPUs to avoid
    // oversubscription.
    static Integer threadsPerWorker(Integer cpuCores, Integer workers) {
        if (cpuCores == null) {
            return null;
        }
        int workerCount = workers != null && workers > 0 ? workers : 1;
        return Math.max(1, cpuCores / workerCount);
    }
}
