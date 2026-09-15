/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.compile;

import io.fabric8.kubernetes.api.model.Quantity;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmBaseBuildRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmRunnerHelper;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmTuningMode;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmTuningRunner;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmTargetArchitecture;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.StringUtils;

// K8s Job for tvm+compile: Relax IR (store:// key) -> model.so via compiler.py, published as a tvm-so Model.
public class TvmCompileRunner extends TvmBaseBuildRunner {

    private static final String COMPILER_SCRIPT_CLASSPATH = "classpath:/runtime-tvm/docker/compiler.py";

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
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        String funcName = taskAccessor.getFunction();

        // Architecture is optional in the form; default to cpu (llvm).
        TvmTargetArchitecture architecture =
            taskSpec.getTargetArchitecture() != null ? taskSpec.getTargetArchitecture() : TvmTargetArchitecture.cpu;

        // IR model to compile: explicit task.model_path wins, else the function's
        // ir_model.
        String modelKey = StringUtils.hasText(taskSpec.getModelPath())
            ? taskSpec.getModelPath()
            : (functionSpec != null ? functionSpec.getIrModel() : null);
        if (!StringUtils.hasText(modelKey)) {
            throw new IllegalArgumentException(
                "tvm+compile needs an IR model: set task.model_path or run tvm+build first " +
                    "(function.spec.ir_model is empty)"
            );
        }
        modelKey = Objects.requireNonNull(modelKey);
        // Resolve store:// to the IR folder's S3 location (whole dir: model.relax.json
        // + metadata + params).
        String s3IrPath = TvmRunnerHelper.resolveModelDir(modelKey, modelManager);

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmCompileTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_FUNCTION_NAME", TvmRunnerHelper.cleanName(funcName)));
        envs.add(new CoreEnv("TVM_TARGET", architecture.getValue()));
        Integer resourceCpuCores = null;
        if (taskSpec.getResources() != null && StringUtils.hasText(taskSpec.getResources().getCpu())) {
            resourceCpuCores = parseCpuCores(taskSpec.getResources().getCpu());
        }
        Integer targetNumCores = taskSpec.getTargetNumCores() != null
                ? taskSpec.getTargetNumCores()
                : resourceCpuCores;
        if (targetNumCores != null) {
            envs.add(new CoreEnv("TVM_TARGET_NUM_CORES", String.valueOf(targetNumCores)));
            // MetaSchedule must measure candidates with the same TVM runtime
            // parallelism assumed by the generated target schedules.
            envs.add(new CoreEnv("TVM_NUM_THREADS", String.valueOf(targetNumCores)));
        }
        if (taskSpec.getOptLevel() != null) {
            envs.add(new CoreEnv("TVM_OPT_LEVEL", String.valueOf(taskSpec.getOptLevel())));
        }
        if (StringUtils.hasText(taskSpec.getExecMode())) {
            envs.add(new CoreEnv("TVM_EXEC_MODE", taskSpec.getExecMode()));
        }
        if (StringUtils.hasText(taskSpec.getRelaxPipeline())) {
            envs.add(new CoreEnv("TVM_RELAX_PIPELINE", taskSpec.getRelaxPipeline()));
        }
        if (StringUtils.hasText(taskSpec.getTirPipeline())) {
            envs.add(new CoreEnv("TVM_TIR_PIPELINE", taskSpec.getTirPipeline()));
        }
        if (taskSpec.getTuningMode() != null) {
            envs.add(new CoreEnv("TVM_TUNING_MODE", taskSpec.getTuningMode().name()));
        }
        if (taskSpec.getTuningTrials() != null) {
            envs.add(new CoreEnv("TVM_TUNING_TRIALS", String.valueOf(taskSpec.getTuningTrials())));
        }
        if (taskSpec.getMaxTrialsPerTask() != null) {
            envs.add(new CoreEnv("TVM_MAX_TRIALS_PER_TASK", String.valueOf(taskSpec.getMaxTrialsPerTask())));
        }
        if (taskSpec.getTuningOps() != null && !taskSpec.getTuningOps().isEmpty()) {
            envs.add(new CoreEnv("TVM_TUNING_OPS", String.join(",", taskSpec.getTuningOps())));
        }
        // Cross targets NEED a cross-cc to link the .so; default per arch (explicit
        // task.cross_cc wins).
        String crossCc = taskSpec.getCrossCc();
        if (!StringUtils.hasText(crossCc)) {
            if (architecture == TvmTargetArchitecture.arm64 ||
                    architecture == TvmTargetArchitecture.arm64_pi5) {
                crossCc = "aarch64-linux-gnu-g++";
            } else if (architecture == TvmTargetArchitecture.armv7l) {
                crossCc = "arm-linux-gnueabihf-g++";
            }
        }
        validateTuning(taskSpec, crossCc, targetNumCores, resourceCpuCores);
        addTuningEnvironment(envs, taskSpec, resourceCpuCores);
        if (StringUtils.hasText(crossCc)) {
            envs.add(new CoreEnv("TVM_CROSS_CC", crossCc));
        }
        if (StringUtils.hasText(taskSpec.getParamsPath())) {
            // env name must stay TVM_PARAMS_FILE — entrypoint.sh maps it to --params-file.
            envs.add(new CoreEnv("TVM_PARAMS_FILE", taskSpec.getParamsPath()));
        }
        if (Boolean.TRUE.equals(taskSpec.getSystemLib())) {
            envs.add(new CoreEnv("TVM_SYSTEM_LIB", "true"));
        }
        if (StringUtils.hasText(taskSpec.getTag())) {
            envs.add(new CoreEnv("TVM_TAG", taskSpec.getTag()));
        }
        // Lineage: link the .so model as CONSUMES the source IR (only store:// keys are
        // tracked entities).
        if (modelKey.startsWith("store://")) {
            envs.add(new CoreEnv("TVM_SOURCE_IR_KEY", modelKey));
        }

        // One compiler for every source format: the input is Relax IR, which no longer
        // carries any trace of the framework it came from.
        String compilerScript = loadClasspathScript(COMPILER_SCRIPT_CLASSPATH);
        List<ContextSource> contextSources = TvmRunnerHelper.createContextSources(entrypoint, compilerScript);
        List<ContextRef> contextRefs = new ArrayList<>();
        contextRefs.add(TvmRunnerHelper.inputContextRef(s3IrPath, "input/"));
        if (StringUtils.hasText(taskSpec.getTuningModelPath())) {
            String tuningPath = TvmRunnerHelper.resolveModelDir(taskSpec.getTuningModelPath(), modelManager);
            contextRefs.add(TvmRunnerHelper.inputContextRef(tuningPath, "tuning-cache/"));
            envs.add(new CoreEnv("TVM_TUNING_DATABASE", homeDir + "/tuning-cache"));
        } else if (taskSpec.getTuningMode() == TvmTuningMode.apply) {
            throw new IllegalArgumentException(
                    "tuning_mode=apply needs tuning_model_path pointing to a prior compiled Model");
        }

        String image = resolveImage(
            taskSpec.getImage(),
            properties.getCompiler(),
            "no compiler image configured: set task.image or runtime.tvm.compiler"
        );

        List<CoreVolume> volumes = createVolumes(taskSpec);
        List<CoreEnv> coreSecrets = createSecrets(secretData);

        return applyCommon(
            K8sJobRunnable.builder()
                .command("/bin/bash")
                .args(new String[] { homeDir + "/" + TvmRunnerHelper.ENTRYPOINT_NAME })
                .contextSources(contextSources)
                .build(),
            run,
            TvmCompileTaskSpec.KIND,
            funcName,
            image,
            envs,
            coreSecrets,
            volumes,
            contextRefs,
            taskSpec
        );
    }

    static int parseCpuCores(String value) {
        final BigDecimal cores;
        try {
            cores = Quantity.parse(value).getNumericalAmount();
        } catch (IllegalArgumentException | ArithmeticException e) {
            throw new IllegalArgumentException("invalid Kubernetes CPU quantity: " + value, e);
        }
        if (cores.signum() <= 0) {
            throw new IllegalArgumentException("CPU resources must be greater than zero: " + value);
        }
        try {
            return cores.setScale(0, RoundingMode.CEILING).intValueExact();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("CPU quantity is too large: " + value, e);
        }
    }

    static void validateTuning(
            TvmCompileTaskSpec taskSpec,
            String crossCc,
            Integer targetNumCores,
            Integer resourceCpuCores) {
        TvmTuningMode mode = taskSpec.getTuningMode() != null
                ? taskSpec.getTuningMode()
                : TvmTuningMode.off;
        TvmTuningRunner runner = taskSpec.getTuningRunner() != null
                ? taskSpec.getTuningRunner()
                : TvmTuningRunner.local;
        if (mode == TvmTuningMode.tune && taskSpec.getTuningTrials() == null) {
            throw new IllegalArgumentException("tuning_mode=tune requires tuning_trials");
        }
        if (mode == TvmTuningMode.apply && !StringUtils.hasText(taskSpec.getTuningModelPath())) {
            throw new IllegalArgumentException("tuning_mode=apply requires tuning_model_path");
        }
        if (mode == TvmTuningMode.tune && runner == TvmTuningRunner.local && StringUtils.hasText(crossCc)) {
            throw new IllegalArgumentException(
                    "cross-compiled targets require tuning_runner=rpc or tuning_mode=apply");
        }
        if (mode == TvmTuningMode.tune && runner == TvmTuningRunner.rpc &&
                (!StringUtils.hasText(taskSpec.getRpcTrackerHost()) ||
                        taskSpec.getRpcTrackerPort() == null ||
                        !StringUtils.hasText(taskSpec.getRpcTrackerKey()))) {
            throw new IllegalArgumentException(
                    "tuning_runner=rpc requires rpc_tracker_host, rpc_tracker_port, and rpc_tracker_key");
        }
        if (mode == TvmTuningMode.tune && runner == TvmTuningRunner.local &&
                targetNumCores != null && resourceCpuCores != null && targetNumCores > resourceCpuCores) {
            throw new IllegalArgumentException(
                    "target_num_cores exceeds the compile Job CPU resources for local tuning");
        }
        if (taskSpec.getTuningWorkers() != null && resourceCpuCores != null &&
                taskSpec.getTuningWorkers() > resourceCpuCores) {
            throw new IllegalArgumentException(
                    "tuning_workers exceeds the compile Job CPU resources");
        }
    }

    static void addTuningEnvironment(
            List<CoreEnv> envs,
            TvmCompileTaskSpec taskSpec,
            Integer resourceCpuCores) {
        if (taskSpec.getTuningRunner() != null) {
            envs.add(new CoreEnv("TVM_TUNING_RUNNER", taskSpec.getTuningRunner().name()));
        }
        Integer workers = taskSpec.getTuningWorkers() != null
                ? taskSpec.getTuningWorkers()
                : resourceCpuCores;
        if (workers != null) {
            envs.add(new CoreEnv("TVM_TUNING_WORKERS", String.valueOf(workers)));
        }
        addEnvironment(envs, "TVM_TUNING_SEED", taskSpec.getTuningSeed());
        addEnvironment(envs, "TVM_TUNING_NUMBER", taskSpec.getTuningNumber());
        addEnvironment(envs, "TVM_TUNING_REPEAT", taskSpec.getTuningRepeat());
        addEnvironment(envs, "TVM_TUNING_MIN_REPEAT_MS", taskSpec.getTuningMinRepeatMs());
        addEnvironment(envs, "TVM_TUNING_ALLOC_REPEAT", taskSpec.getTuningAllocRepeat());
        addEnvironment(envs, "TVM_TUNING_BUILDER_TIMEOUT_SEC", taskSpec.getTuningBuilderTimeoutSec());
        addEnvironment(envs, "TVM_TUNING_RUNNER_TIMEOUT_SEC", taskSpec.getTuningRunnerTimeoutSec());
        addEnvironment(envs, "TVM_RPC_TRACKER_HOST", taskSpec.getRpcTrackerHost());
        addEnvironment(envs, "TVM_RPC_TRACKER_PORT", taskSpec.getRpcTrackerPort());
        addEnvironment(envs, "TVM_RPC_TRACKER_KEY", taskSpec.getRpcTrackerKey());
        addEnvironment(envs, "TVM_RPC_SESSION_TIMEOUT_SEC", taskSpec.getRpcSessionTimeoutSec());
        addEnvironment(envs, "TVM_TUNING_ENABLE_CPU_CACHE_FLUSH", taskSpec.getTuningEnableCpuCacheFlush());
        addEnvironment(envs, "TVM_ALLOW_PARTIAL_TUNING", taskSpec.getAllowPartialTuning());
    }

    private static void addEnvironment(List<CoreEnv> envs, String name, Object value) {
        if (value != null && (!(value instanceof String text) || StringUtils.hasText(text))) {
            envs.add(new CoreEnv(name, String.valueOf(value)));
        }
    }
}
