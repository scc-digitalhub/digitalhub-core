/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.compile;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmBaseRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmRunnerHelper;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmTargetArchitecture;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// K8s Job for tvm+compile: Relax IR (store:// key) -> model.so via compiler.py, published as a tvm-so Model.
@Component
public class TvmCompileRunner extends TvmBaseRunner {

    private static final String COMPILER_SCRIPT_CLASSPATH = "classpath:/runtime-tvm/docker/compiler.py";

    private final ModelManager modelService;

    public TvmCompileRunner(TvmProperties properties, K8sBuilderHelper k8sBuilderHelper, ModelManager modelService) {
        super(properties, k8sBuilderHelper);
        this.modelService = modelService;
    }

    public K8sJobRunnable produce(Run run, Map<String, String> secretData) {
        TvmCompileRunSpec runSpec = TvmCompileRunSpec.with(run.getSpec());
        TvmFunctionSpec functionSpec = runSpec.getFunctionSpec();
        TvmCompileTaskSpec taskSpec = runSpec.getTaskCompileSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        String funcName = taskAccessor.getFunction();

        // Architecture is optional in the form; default to cpu (llvm).
        TvmTargetArchitecture architecture = taskSpec.getTargetArchitecture() != null
                ? taskSpec.getTargetArchitecture()
                : TvmTargetArchitecture.cpu;

        // IR model to compile: explicit task.model_path wins, else the function's ir_model.
        String modelKey = StringUtils.hasText(taskSpec.getModelPath())
                ? taskSpec.getModelPath()
                : (functionSpec != null ? functionSpec.getIrModel() : null);
        if (!StringUtils.hasText(modelKey)) {
            throw new IllegalArgumentException(
                    "tvm+compile needs an IR model: set task.model_path or run tvm+build first " +
                            "(function.spec.ir_model is empty)");
        }
        // Resolve store:// to the IR folder's S3 location (whole dir: model.relax.json + metadata + params).
        String s3IrPath = TvmRunnerHelper.resolveModelDir(modelKey, modelService);

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmCompileTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_FUNCTION_NAME", TvmRunnerHelper.cleanName(funcName)));
        envs.add(new CoreEnv("TVM_TARGET", architecture.getValue()));
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
        // Cross targets NEED a cross-cc to link the .so; default per arch (explicit task.cross_cc wins).
        String crossCc = taskSpec.getCrossCc();
        if (!StringUtils.hasText(crossCc)) {
            if (architecture == TvmTargetArchitecture.arm64) {
                crossCc = "aarch64-linux-gnu-g++";
            } else if (architecture == TvmTargetArchitecture.armv7l) {
                crossCc = "arm-linux-gnueabihf-g++";
            }
        }
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
        // Lineage: link the .so model as CONSUMES the source IR (only store:// keys are tracked entities).
        if (modelKey.startsWith("store://")) {
            envs.add(new CoreEnv("TVM_SOURCE_IR_KEY", modelKey));
        }

        String compilerScript = loadClasspathScript(COMPILER_SCRIPT_CLASSPATH);
        List<ContextSource> contextSources = TvmRunnerHelper.createContextSources(entrypoint, compilerScript);
        List<ContextRef> contextRefs = Collections.singletonList(
                TvmRunnerHelper.inputContextRef(s3IrPath, "input/"));

        String image = resolveImage(
                taskSpec.getImage(),
                properties.getCompiler(),
                "no compiler image configured: set task.image or runtime.tvm.compiler");

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
                taskSpec);
    }
}
