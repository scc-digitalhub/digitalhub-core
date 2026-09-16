/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.build;

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
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

// K8s Job for tvm+build: converts the source model (ONNX or TFLite) into Relax IR with the
// builder script of its format, and publishes the result as a tvm-ir Model.
public class TvmBuildRunner extends TvmBaseRunner {

    private final ModelManager modelManager;

    public TvmBuildRunner(
        TvmProperties properties,
        K8sBuilderHelper k8sBuilderHelper,
        K8sLabelHelper k8sLabelHelper,
        ModelManager modelManager
    ) {
        super(properties, k8sBuilderHelper, k8sLabelHelper);
        this.modelManager = modelManager;
    }

    public K8sJobRunnable produce(Run run, Map<String, String> secretData) {
        TvmBuildRunSpec runSpec = TvmBuildRunSpec.with(run.getSpec());
        TvmFunctionSpec functionSpec = runSpec.getFunctionSpec();
        TvmBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();
        String funcName = TaskSpecAccessor.with(taskSpec.toMap()).getFunction();

        String sourceUri = TvmRunnerHelper.resolveModelPath(functionSpec.getModel(), modelManager);
        TvmFormat format = resolveFormat(functionSpec.getFormat(), functionSpec.getModel(), sourceUri);
        String inputFile = inputFileName(sourceUri, format);

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmBuildTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_FUNCTION_NAME", funcName));
        envs.add(new CoreEnv("TVM_INPUT_FILE", inputFile));
        // Conversion options, read by build_onnx.py; build_tflite.py has none of its own.
        addEnv(envs, "TVM_KEEP_PARAMS_IN_INPUT", taskSpec.getKeepParamsInInput());
        addEnv(envs, "TVM_SANITIZE_INPUT_NAMES", taskSpec.getSanitizeInputNames());
        addEnv(envs, "TVM_SIMPLIFY", taskSpec.getSimplify());
        addEnv(envs, "TVM_TARGET_OPSET", taskSpec.getTargetOpset());
        addEnv(envs, "TVM_OPSET_OVERRIDE", taskSpec.getOpsetOverride());
        addEnv(envs, "TVM_STRICT_SHAPE_INFER", taskSpec.getStrictShapeInference());
        addEnv(envs, "TVM_DATA_PROP", taskSpec.getDataProp());

        String scriptLocation = builderScriptLocation(format);
        String scriptName = TvmRunnerHelper.scriptName(scriptLocation);
        envs.add(new CoreEnv("TVM_TASK_SCRIPT", scriptName));
        List<ContextSource> contextSources = TvmRunnerHelper.createContextSources(
            entrypoint,
            scriptName,
            TvmRunnerHelper.loadClasspath(scriptLocation)
        );

        // The init container downloads the source into input/. The http downloader needs a
        // file name as destination, while s3 can write straight into the folder.
        boolean http = sourceUri.startsWith("http://") || sourceUri.startsWith("https://");
        List<ContextRef> contextRefs = Collections.singletonList(
            TvmRunnerHelper.inputContextRef(sourceUri, http ? "input/" + inputFile : "input/")
        );

        String image = resolveImage(
            taskSpec.getImage(),
            properties.getBuilders() != null ? properties.getBuilders().get(format.name()) : null,
            "no builder image configured for format: " + format.name()
        );

        return applyCommon(
            K8sJobRunnable.builder()
                .command("/bin/bash")
                .args(new String[] { homeDir + "/" + TvmRunnerHelper.ENTRYPOINT_NAME })
                .contextSources(contextSources)
                .build(),
            run,
            TvmBuildTaskSpec.KIND,
            funcName,
            image,
            envs,
            createSecrets(secretData),
            createVolumes(taskSpec),
            contextRefs,
            taskSpec
        );
    }

    // Source format, in order of precedence: spec.format when set explicitly, then the kind
    // of the referenced Model (onnx, tflite), then the file extension.
    static TvmFormat resolveFormat(TvmFormat declared, String modelReference, String sourceUri) {
        if (declared != null && declared != TvmFormat.auto) {
            return declared;
        }
        TvmFormat fromKind = TvmFormat.fromModelKind(TvmRunnerHelper.modelKindOf(modelReference));
        return fromKind != null ? fromKind : TvmFormat.fromPath(sourceUri);
    }

    // File the builder reads from input/. A folder has no file name of its own: the pod
    // build script then looks for the single model file with the format's extension.
    static String inputFileName(String sourceUri, TvmFormat format) {
        String name = sourceUri.endsWith("/") ? "" : TvmRunnerHelper.extractFileName(sourceUri);
        return StringUtils.hasText(name) ? name : "model." + format.name();
    }

    // Build script of a format: runtime.tvm.builder-scripts.<format>, else the bundled
    // build_<format>.py.
    private String builderScriptLocation(TvmFormat format) {
        String bundled = TvmRunnerHelper.SCRIPTS_CLASSPATH + "build_" + format.name() + ".py";
        Map<String, String> configured = properties.getBuilderScripts();
        return configured != null ? configured.getOrDefault(format.name(), bundled) : bundled;
    }
}
