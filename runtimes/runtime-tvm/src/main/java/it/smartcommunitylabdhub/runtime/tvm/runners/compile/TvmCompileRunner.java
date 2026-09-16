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

// K8s Job for tvm+compile: compile_model.py turns the Relax IR into model.so for the chosen
// target (optionally tuned with MetaSchedule) and publishes it as a tvm-so Model.
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

        // The IR to compile: task.model_path wins over the function's ir_model.
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

        // Cores the generated code assumes: target_num_cores, else the Job CPU request.
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
        // Lineage: the compiled Model CONSUMES the IR Model (only store:// keys are entities).
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
        // A previous tvm-so Model carries the tuning database to resume (tune) or apply.
        if (StringUtils.hasText(taskSpec.getTuningModelPath())) {
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

    // Node architecture the compile Job needs. The x86 targets are generated and linked by
    // the native toolchain of the node, so they need an amd64 node; the ARM targets are
    // cross-compiled on any node, and cpu builds for the node the Job runs on.
    static String jobArchitecture(TvmTargetArchitecture architecture) {
        return switch (architecture) {
            case x86, x86_v3, x86_native -> "amd64";
            default -> null;
        };
    }

    // Cross C++ compiler that links model.so for an ARM target; null for the targets the
    // compile Job links with its native compiler.
    static String defaultCrossCc(TvmTargetArchitecture architecture) {
        return switch (architecture) {
            case arm64, arm64_pi5 -> "aarch64-linux-gnu-g++";
            case armv7l -> "arm-linux-gnueabihf-g++";
            default -> null;
        };
    }

    // Rejects tuning settings that can only fail inside the Job, so the user gets the
    // error when submitting the run instead of after the pod starts.
    static void validateTuning(
        TvmCompileTaskSpec taskSpec,
        String crossCc,
        Integer targetNumCores,
        Integer resourceCpuCores
    ) {
        TvmTuningMode mode = Objects.requireNonNullElse(taskSpec.getTuningMode(), TvmTuningMode.off);
        TvmTuningRunner runner = Objects.requireNonNullElse(taskSpec.getTuningRunner(), TvmTuningRunner.local);
        boolean tune = mode == TvmTuningMode.tune;

        if (tune && taskSpec.getTuningTrials() == null) {
            throw new IllegalArgumentException("tuning_mode=tune requires tuning_trials");
        }
        if (mode == TvmTuningMode.apply && !StringUtils.hasText(taskSpec.getTuningModelPath())) {
            throw new IllegalArgumentException("tuning_mode=apply requires tuning_model_path");
        }
        // The local runner executes candidates on the Job itself, which cannot run ARM code.
        if (tune && runner == TvmTuningRunner.local && StringUtils.hasText(crossCc)) {
            throw new IllegalArgumentException("cross-compiled targets require tuning_runner=rpc or tuning_mode=apply");
        }
        if (
            tune &&
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
            tune &&
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

    // MetaSchedule settings read by compile_model.py. Unset fields are not exported, so
    // the script applies its own defaults.
    static void addTuningEnvironment(List<CoreEnv> envs, TvmCompileTaskSpec taskSpec, Integer resourceCpuCores) {
        addEnv(envs, "TVM_TUNING_MODE", taskSpec.getTuningMode());
        addEnv(envs, "TVM_TUNING_TRIALS", taskSpec.getTuningTrials());
        addEnv(envs, "TVM_MAX_TRIALS_PER_TASK", taskSpec.getMaxTrialsPerTask());
        addEnv(envs, "TVM_TUNING_TRIALS_PER_ITER", taskSpec.getTuningTrialsPerIter());
        if (taskSpec.getTuningOps() != null && !taskSpec.getTuningOps().isEmpty()) {
            envs.add(new CoreEnv("TVM_TUNING_OPS", String.join(",", taskSpec.getTuningOps())));
        }
        addEnv(envs, "TVM_TUNING_RUNNER", taskSpec.getTuningRunner());
        // One builder worker per requested core unless the task says otherwise.
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
        addEnv(envs, "TVM_TUNING_RUNNER_TIMEOUT_SEC", taskSpec.getTuningRunnerTimeoutSec());
        addEnv(envs, "TVM_RPC_TRACKER_HOST", taskSpec.getRpcTrackerHost());
        addEnv(envs, "TVM_RPC_TRACKER_PORT", taskSpec.getRpcTrackerPort());
        addEnv(envs, "TVM_RPC_TRACKER_KEY", taskSpec.getRpcTrackerKey());
        addEnv(envs, "TVM_RPC_SESSION_TIMEOUT_SEC", taskSpec.getRpcSessionTimeoutSec());
        addEnv(envs, "TVM_TUNING_ENABLE_CPU_CACHE_FLUSH", taskSpec.getTuningEnableCpuCacheFlush());
        addEnv(envs, "TVM_ALLOW_PARTIAL_TUNING", taskSpec.getAllowPartialTuning());
    }
}
