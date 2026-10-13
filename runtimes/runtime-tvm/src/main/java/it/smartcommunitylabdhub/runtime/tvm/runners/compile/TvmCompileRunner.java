/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.compile;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmBaseRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmRunnerHelper;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmTuningMode;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmTuningRunner;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmTargetArchitecture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

public class TvmCompileRunner extends TvmBaseRunner {

    private static final String COMPILE_SCRIPT = "compile_model.py";

    private final ModelManager modelManager;

    public TvmCompileRunner(
        TvmProperties properties,
        K8sBuilderHelper k8sBuilderHelper,
        K8sLabelHelper k8sLabelHelper,
        ModelManager modelManager
    ) {
        super(properties, k8sBuilderHelper, k8sLabelHelper);
        this.modelManager = modelManager;
    }

    public K8sJobRunnable produce(Run run, Map<String, String> secretData) {
        TvmCompileRunSpec runSpec = TvmCompileRunSpec.with(run.getSpec());
        TvmFunctionSpec functionSpec = runSpec.getFunctionSpec();
        TvmCompileTaskSpec taskSpec = runSpec.getTaskCompileSpec();
        String funcName = TaskSpecAccessor.with(taskSpec.toMap()).getFunction();

        TvmTargetArchitecture architecture = Objects.requireNonNullElse(
            taskSpec.getTargetArchitecture(),
            TvmTargetArchitecture.cpu
        );

        String irModelKey = StringUtils.hasText(taskSpec.getModelPath())
            ? taskSpec.getModelPath()
            : (functionSpec != null ? functionSpec.getIrModel() : null);
        if (!StringUtils.hasText(irModelKey)) {
            throw new IllegalArgumentException(
                "tvm+compile needs an IR model: set task.model_path or run tvm+build first " +
                    "(function.spec.ir_model is empty)"
            );
        }
        String irFolder = TvmRunnerHelper.resolveModelDir(irModelKey, modelManager);

        Integer cpuCores = requestedCpuCores(taskSpec);
        Integer targetNumCores = taskSpec.getTargetNumCores() != null ? taskSpec.getTargetNumCores() : cpuCores;
        String crossCc = StringUtils.hasText(taskSpec.getCrossCc())
            ? taskSpec.getCrossCc()
            : defaultCrossCc(architecture);
        validateTuning(taskSpec, crossCc, targetNumCores, cpuCores);

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmCompileTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_FUNCTION_NAME", TvmRunnerHelper.cleanName(funcName)));
        envs.add(new CoreEnv("TVM_TARGET", architecture.getValue()));
        addEnv(envs, "TVM_TARGET_NUM_CORES", targetNumCores);
        // Tuning must measure candidates with the thread count the schedules are built for.
        addEnv(envs, "TVM_NUM_THREADS", targetNumCores);
        addEnv(envs, "TVM_OPT_LEVEL", taskSpec.getOptLevel());
        addEnv(envs, "TVM_EXEC_MODE", taskSpec.getExecMode());
        addEnv(envs, "TVM_RELAX_PIPELINE", taskSpec.getRelaxPipeline());
        addEnv(envs, "TVM_TIR_PIPELINE", taskSpec.getTirPipeline());
        addEnv(envs, "TVM_CROSS_CC", crossCc);
        addEnv(envs, "TVM_SYSTEM_LIB", taskSpec.getSystemLib());
        // In-pod path only: compile_model.py reads it as it is.
        addEnv(envs, "TVM_PARAMS_FILE", taskSpec.getParamsPath());
        addEnv(envs, "TVM_TAG", taskSpec.getTag());
        addEnv(envs, "TVM_BENCHMARK_RUNS", taskSpec.getBenchmarkRuns());
        addTuningEnvironment(envs, taskSpec, cpuCores);
        // Only store:// references can participate in entity lineage.
        if (irModelKey.startsWith(Keys.STORE_PREFIX)) {
            envs.add(new CoreEnv("TVM_SOURCE_IR_KEY", irModelKey));
        }

        envs.add(new CoreEnv("TVM_TASK_SCRIPT", COMPILE_SCRIPT));
        List<ContextSource> contextSources = TvmRunnerHelper.createContextSources(
            entrypoint,
            COMPILE_SCRIPT,
            TvmRunnerHelper.loadClasspath(TvmRunnerHelper.SCRIPTS_CLASSPATH + COMPILE_SCRIPT)
        );

        List<ContextRef> contextRefs = new ArrayList<>();
        contextRefs.add(TvmRunnerHelper.inputContextRef(irFolder, "input/"));
        if (
            taskSpec.getTuningMode() != null &&
            taskSpec.getTuningMode() != TvmTuningMode.off &&
            StringUtils.hasText(taskSpec.getTuningModelPath())
        ) {
            String tuningFolder = TvmRunnerHelper.resolveModelDir(taskSpec.getTuningModelPath(), modelManager);
            contextRefs.add(TvmRunnerHelper.inputContextRef(tuningFolder, "tuning-cache/"));
            envs.add(new CoreEnv("TVM_TUNING_DATABASE", homeDir + "/tuning-cache"));
        }

        String image = resolveImage(
            taskSpec.getImage(),
            properties.getCompiler(),
            "no compiler image configured: set task.image or runtime.tvm.compiler"
        );

        return applyCommon(
            K8sJobRunnable.builder()
                .nodeSelector(architectureSelector(jobArchitecture(architecture)))
                .command("/bin/bash")
                .args(new String[] { homeDir + "/" + TvmRunnerHelper.ENTRYPOINT_NAME })
                .contextSources(contextSources)
                .build(),
            run,
            TvmCompileTaskSpec.KIND,
            funcName,
            image,
            envs,
            createSecrets(secretData),
            createVolumes(taskSpec),
            contextRefs,
            taskSpec
        );
    }

    // x86 targets use the native toolchain; ARM targets use cross-compilers.
    static String jobArchitecture(TvmTargetArchitecture architecture) {
        return switch (architecture) {
            case x86, x86_v3, x86_native -> "amd64";
            default -> null;
        };
    }

    static String defaultCrossCc(TvmTargetArchitecture architecture) {
        return switch (architecture) {
            case arm64, arm64_pi5 -> "aarch64-linux-gnu-g++";
            case armv7l -> "arm-linux-gnueabihf-g++";
            default -> null;
        };
    }

    static void validateTuning(
        TvmCompileTaskSpec taskSpec,
        String crossCc,
        Integer targetNumCores,
        Integer resourceCpuCores
    ) {
        TvmTuningMode mode = Objects.requireNonNullElse(taskSpec.getTuningMode(), TvmTuningMode.off);
        if (mode == TvmTuningMode.off) return;
        if (mode == TvmTuningMode.apply) {
            if (!StringUtils.hasText(taskSpec.getTuningModelPath())) {
                throw new IllegalArgumentException("tuning_mode=apply requires tuning_model_path");
            }
            return;
        }
        if (taskSpec.getTuningTrials() == null) {
            throw new IllegalArgumentException("tuning_mode=tune requires tuning_trials");
        }
        TvmTuningRunner runner = Objects.requireNonNullElse(taskSpec.getTuningRunner(), TvmTuningRunner.local);
        // Cross-compiled candidates must be measured on the remote target.
        if (runner == TvmTuningRunner.local && StringUtils.hasText(crossCc)) {
            throw new IllegalArgumentException("cross-compiled targets require tuning_runner=rpc or tuning_mode=apply");
        }
        if (
            runner == TvmTuningRunner.rpc &&
            (!StringUtils.hasText(taskSpec.getRpcTrackerHost()) ||
                taskSpec.getRpcTrackerPort() == null ||
                !StringUtils.hasText(taskSpec.getRpcTrackerKey()))
        ) {
            throw new IllegalArgumentException(
                "tuning_runner=rpc requires rpc_tracker_host, rpc_tracker_port, and rpc_tracker_key"
            );
        }
        if (
            runner == TvmTuningRunner.local &&
            targetNumCores != null &&
            resourceCpuCores != null &&
            targetNumCores > resourceCpuCores
        ) {
            throw new IllegalArgumentException(
                "target_num_cores exceeds the compile Job CPU resources for local tuning"
            );
        }
        if (
            taskSpec.getTuningWorkers() != null &&
            resourceCpuCores != null &&
            taskSpec.getTuningWorkers() > resourceCpuCores
        ) {
            throw new IllegalArgumentException("tuning_workers exceeds the compile Job CPU resources");
        }
    }

    static void addTuningEnvironment(List<CoreEnv> envs, TvmCompileTaskSpec taskSpec, Integer resourceCpuCores) {
        TvmTuningMode mode = Objects.requireNonNullElse(taskSpec.getTuningMode(), TvmTuningMode.off);
        addEnv(envs, "TVM_TUNING_MODE", mode);
        if (mode == TvmTuningMode.off) return;
        addEnv(envs, "TVM_ALLOW_PARTIAL_TUNING", taskSpec.getAllowPartialTuning());
        if (taskSpec.getTuningOps() != null && !taskSpec.getTuningOps().isEmpty()) {
            envs.add(new CoreEnv("TVM_TUNING_OPS", String.join(",", taskSpec.getTuningOps())));
        }
        if (mode == TvmTuningMode.apply) return;
        addEnv(envs, "TVM_TUNING_TRIALS", taskSpec.getTuningTrials());
        addEnv(envs, "TVM_MAX_TRIALS_PER_TASK", taskSpec.getMaxTrialsPerTask());
        addEnv(envs, "TVM_TUNING_TRIALS_PER_ITER", taskSpec.getTuningTrialsPerIter());
        addEnv(envs, "TVM_TUNING_RUNNER", taskSpec.getTuningRunner());
        addEnv(
            envs,
            "TVM_TUNING_WORKERS",
            taskSpec.getTuningWorkers() != null ? taskSpec.getTuningWorkers() : resourceCpuCores
        );
        addEnv(envs, "TVM_TUNING_SEED", taskSpec.getTuningSeed());
        addEnv(envs, "TVM_TUNING_NUMBER", taskSpec.getTuningNumber());
        addEnv(envs, "TVM_TUNING_REPEAT", taskSpec.getTuningRepeat());
        addEnv(envs, "TVM_TUNING_MIN_REPEAT_MS", taskSpec.getTuningMinRepeatMs());
        addEnv(envs, "TVM_TUNING_ALLOC_REPEAT", taskSpec.getTuningAllocRepeat());
        addEnv(envs, "TVM_TUNING_BUILDER_TIMEOUT_SEC", taskSpec.getTuningBuilderTimeoutSec());
        if (taskSpec.getTuningRunner() == TvmTuningRunner.rpc) {
            addEnv(envs, "TVM_RPC_TRACKER_HOST", taskSpec.getRpcTrackerHost());
            addEnv(envs, "TVM_RPC_TRACKER_PORT", taskSpec.getRpcTrackerPort());
            addEnv(envs, "TVM_RPC_TRACKER_KEY", taskSpec.getRpcTrackerKey());
            addEnv(envs, "TVM_RPC_SESSION_TIMEOUT_SEC", taskSpec.getRpcSessionTimeoutSec());
        } else {
            addEnv(envs, "TVM_TUNING_RUNNER_TIMEOUT_SEC", taskSpec.getTuningRunnerTimeoutSec());
        }
        addEnv(envs, "TVM_TUNING_ENABLE_CPU_CACHE_FLUSH", taskSpec.getTuningEnableCpuCacheFlush());
    }
}
