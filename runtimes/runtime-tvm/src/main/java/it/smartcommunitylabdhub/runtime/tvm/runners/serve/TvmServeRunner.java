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
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.models.Model;
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
import org.springframework.util.StringUtils;

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
        FunctionManager functionService
    ) {
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

        String modelKey = resolveModelKey(
            taskSpec.getModelPath(),
            functionSpec != null ? functionSpec.getSoModel() : null
        );
        Model model = modelKey.startsWith(Keys.STORE_PREFIX)
            ? TvmRunnerHelper.resolveModel(modelKey, modelManager)
            : null;
        String modelFolder = TvmRunnerHelper.modelDir(
            model != null ? TvmRunnerHelper.modelPath(model, modelKey) : modelKey
        );
        String modelDir = homeDir + "/model";
        String architecture = modelArchitecture(modelKey, model);

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmServeTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_MODEL_DIR", modelDir));
        envs.add(new CoreEnv("TVM_MODEL_NAME", servedName));
        addEnv(envs, "TVM_SERVE_WORKERS", taskSpec.getWorkers());
        Integer threads = threadsPerWorker(requestedCpuCores(taskSpec), taskSpec.getWorkers());
        if (!hasTaskEnv(taskSpec, "TVM_NUM_THREADS")) {
            addEnv(envs, "TVM_NUM_THREADS", threads);
        }

        List<ContextRef> contextRefs = Collections.singletonList(
            TvmRunnerHelper.inputContextRef(modelFolder, "model/")
        );

        String image = resolveImage(
            taskSpec.getImage(),
            properties.getServe(),
            "no serve image configured: set task.image or runtime.tvm.serve"
        );

        List<CorePort> servicePorts = List.of(
            new CorePort(HTTP_PORT, HTTP_PORT, AppProtocol.openinference_v2),
            new CorePort(GRPC_PORT, GRPC_PORT, AppProtocol.openinference_v2)
        );

        List<String> serviceNames = new ArrayList<>();
        if (StringUtils.hasText(taskSpec.getServiceName())) {
            serviceNames.add(funcName + "-" + taskSpec.getServiceName());
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
            secretData,
            contextRefs,
            taskSpec
        );
    }

    // The function reference is the default; never infer a model from run history.
    public static String resolveModelKey(String modelPath, String soModel) {
        String modelKey = StringUtils.hasText(modelPath) ? modelPath : soModel;
        if (!StringUtils.hasText(modelKey)) {
            throw new IllegalArgumentException(
                "tvm+serve needs a compiled .so model: set model_path or run tvm+compile first " +
                    "(function.spec.so_model is empty)"
            );
        }
        return modelKey;
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

    // Each worker owns a thread pool: split the pod's CPUs to avoid oversubscription.
    static Integer threadsPerWorker(Integer cpuCores, Integer workers) {
        if (cpuCores == null) {
            return null;
        }
        int workerCount = workers != null && workers > 0 ? workers : 1;
        return Math.max(1, cpuCores / workerCount);
    }
}
