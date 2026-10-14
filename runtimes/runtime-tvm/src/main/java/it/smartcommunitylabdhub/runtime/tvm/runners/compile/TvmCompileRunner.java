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
            runSpec.getTargetArchitecture(),
            TvmTargetArchitecture.auto
        );

        String irModelKey = StringUtils.hasText(runSpec.getModelPath())
            ? runSpec.getModelPath()
            : (functionSpec != null ? functionSpec.getIrModel() : null);
        if (!StringUtils.hasText(irModelKey)) {
            throw new IllegalArgumentException(
                "tvm+compile needs an IR model: set run.model_path or run tvm+build first " +
                    "(function.spec.ir_model is empty)"
            );
        }
        String irFolder = TvmRunnerHelper.resolveModelDir(irModelKey, modelManager);

        Integer cpuCores = requestedCpuCores(taskSpec);
        Integer targetNumCores = runSpec.getTargetNumCores() != null ? runSpec.getTargetNumCores() : cpuCores;
        String crossCc = StringUtils.hasText(runSpec.getCrossCc())
            ? runSpec.getCrossCc()
            : defaultCrossCc(architecture);
        validateTuning(runSpec, crossCc, targetNumCores, cpuCores);

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmCompileTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_FUNCTION_NAME", TvmRunnerHelper.cleanName(funcName)));
        envs.add(new CoreEnv("TVM_TARGET", architecture.getValue()));
        addEnv(envs, "TVM_TARGET_NUM_CORES", targetNumCores);
        // Tuning must measure candidates with the thread count the schedules are built for.
        addEnv(envs, "TVM_NUM_THREADS", targetNumCores);
        addEnv(envs, "TVM_OPT_LEVEL", runSpec.getOptLevel());
        addEnv(envs, "TVM_EXEC_MODE", runSpec.getExecMode());
        addEnv(envs, "TVM_RELAX_PIPELINE", runSpec.getRelaxPipeline());
        addEnv(envs, "TVM_TIR_PIPELINE", runSpec.getTirPipeline());
        addEnv(envs, "TVM_CROSS_CC", crossCc);
        addEnv(envs, "TVM_SYSTEM_LIB", runSpec.getSystemLib());
        // In-pod path only: compile_model.py reads it as it is.
        addEnv(envs, "TVM_PARAMS_FILE", runSpec.getParamsPath());
        addEnv(envs, "TVM_TAG", runSpec.getTag());
        addEnv(envs, "TVM_BENCHMARK_RUNS", runSpec.getBenchmarkRuns());
        addTuningEnvironment(envs, runSpec, cpuCores);
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
            runSpec.getTuningMode() != null &&
            runSpec.getTuningMode() != TvmTuningMode.off &&
            StringUtils.hasText(runSpec.getTuningModelPath())
        ) {
            String tuningFolder = TvmRunnerHelper.resolveModelDir(runSpec.getTuningModelPath(), modelManager);
            contextRefs.add(TvmRunnerHelper.inputContextRef(tuningFolder, "tuning-cache/"));
            envs.add(new CoreEnv("TVM_TUNING_DATABASE", homeDir + "/tuning-cache"));
        }

        String image = resolveImage(
            runSpec.getImage(),
            properties.getCompiler(),
            "no compiler image configured: set run.image or runtime.tvm.compiler"
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
            secretData,
            contextRefs,
            taskSpec
        );
    }

    // AMD64 targets use the native toolchain; ARM targets use cross-compilers.
    static String jobArchitecture(TvmTargetArchitecture architecture) {
        return switch (architecture) {
            case amd64, amd64_v3, amd64_native -> "amd64";
            default -> null;
        };
    }

    static String defaultCrossCc(TvmTargetArchitecture architecture) {
        return switch (architecture) {
            case arm64, arm64_pi5 -> "aarch64-linux-gnu-g++";
            case arm32 -> "arm-linux-gnueabihf-g++";
            default -> null;
        };
    }

    static void validateTuning(
        TvmCompileRunSpec runSpec,
        String crossCc,
        Integer targetNumCores,
        Integer resourceCpuCores
    ) {
        TvmTuningMode mode = Objects.requireNonNullElse(runSpec.getTuningMode(), TvmTuningMode.off);
        if (mode == TvmTuningMode.off) return;
        if (mode == TvmTuningMode.apply) {
            if (!StringUtils.hasText(runSpec.getTuningModelPath())) {
                throw new IllegalArgumentException("tuning_mode=apply requires tuning_model_path");
            }
            return;
        }
        if (runSpec.getTuningTrials() == null) {
            throw new IllegalArgumentException("tuning_mode=tune requires tuning_trials");
        }
        TvmTuningRunner runner = Objects.requireNonNullElse(runSpec.getTuningRunner(), TvmTuningRunner.local);
        // Cross-compiled candidates must be measured on the remote target.
        if (runner == TvmTuningRunner.local && StringUtils.hasText(crossCc)) {
            throw new IllegalArgumentException("cross-compiled targets require tuning_runner=rpc or tuning_mode=apply");
        }
        if (
            runner == TvmTuningRunner.rpc &&
            (!StringUtils.hasText(runSpec.getRpcTrackerHost()) ||
                runSpec.getRpcTrackerPort() == null ||
                !StringUtils.hasText(runSpec.getRpcTrackerKey()))
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
            runSpec.getTuningWorkers() != null &&
            resourceCpuCores != null &&
            runSpec.getTuningWorkers() > resourceCpuCores
        ) {
            throw new IllegalArgumentException("tuning_workers exceeds the compile Job CPU resources");
        }
    }

    static void addTuningEnvironment(List<CoreEnv> envs, TvmCompileRunSpec runSpec, Integer resourceCpuCores) {
        TvmTuningMode mode = Objects.requireNonNullElse(runSpec.getTuningMode(), TvmTuningMode.off);
        addEnv(envs, "TVM_TUNING_MODE", mode);
        if (mode == TvmTuningMode.off) return;
        addEnv(envs, "TVM_ALLOW_PARTIAL_TUNING", runSpec.getAllowPartialTuning());
        if (runSpec.getTuningOps() != null && !runSpec.getTuningOps().isEmpty()) {
            envs.add(new CoreEnv("TVM_TUNING_OPS", String.join(",", runSpec.getTuningOps())));
        }
        if (mode == TvmTuningMode.apply) return;
        addEnv(envs, "TVM_TUNING_TRIALS", runSpec.getTuningTrials());
        addEnv(envs, "TVM_MAX_TRIALS_PER_TASK", runSpec.getMaxTrialsPerTask());
        addEnv(envs, "TVM_TUNING_TRIALS_PER_ITER", runSpec.getTuningTrialsPerIter());
        addEnv(envs, "TVM_TUNING_RUNNER", runSpec.getTuningRunner());
        addEnv(
            envs,
            "TVM_TUNING_WORKERS",
            runSpec.getTuningWorkers() != null ? runSpec.getTuningWorkers() : resourceCpuCores
        );
        addEnv(envs, "TVM_TUNING_SEED", runSpec.getTuningSeed());
        addEnv(envs, "TVM_TUNING_NUMBER", runSpec.getTuningNumber());
        addEnv(envs, "TVM_TUNING_REPEAT", runSpec.getTuningRepeat());
        addEnv(envs, "TVM_TUNING_MIN_REPEAT_MS", runSpec.getTuningMinRepeatMs());
        addEnv(envs, "TVM_TUNING_ALLOC_REPEAT", runSpec.getTuningAllocRepeat());
        addEnv(envs, "TVM_TUNING_BUILDER_TIMEOUT_SEC", runSpec.getTuningBuilderTimeoutSec());
        if (runSpec.getTuningRunner() == TvmTuningRunner.rpc) {
            addEnv(envs, "TVM_RPC_TRACKER_HOST", runSpec.getRpcTrackerHost());
            addEnv(envs, "TVM_RPC_TRACKER_PORT", runSpec.getRpcTrackerPort());
            addEnv(envs, "TVM_RPC_TRACKER_KEY", runSpec.getRpcTrackerKey());
            addEnv(envs, "TVM_RPC_SESSION_TIMEOUT_SEC", runSpec.getRpcSessionTimeoutSec());
        } else {
            addEnv(envs, "TVM_TUNING_RUNNER_TIMEOUT_SEC", runSpec.getTuningRunnerTimeoutSec());
        }
        addEnv(envs, "TVM_TUNING_ENABLE_CPU_CACHE_FLUSH", runSpec.getTuningEnableCpuCacheFlush());
    }
}
