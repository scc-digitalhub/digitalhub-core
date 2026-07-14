/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.build;

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
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmFormat;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Builds the K8s Job that runs tvm+build: it takes a source ONNX model (resolved
// from a store:// key), runs builder_onnx.py on the tvm-toolkit image to convert
// it to Relax IR (model.relax.json + metadata.json [+ params.bin]), and publishes
// the result as a tvm-ir Model. All from_onnx params and ONNX preprocessing flags
// are forwarded as env vars to builder_onnx.py. ONNX is the only supported source
// format (spec.format is {auto, onnx}).
@Component
public class TvmBuildRunner extends TvmBaseRunner {

    private static final String FORMAT = "onnx";
    private static final String DEFAULT_INPUT_FILE = "model.onnx";
    private static final String BUILDER_SCRIPT_CLASSPATH = "classpath:/runtime-tvm/docker/builder_onnx.py";

    private final ModelManager modelService;

    public TvmBuildRunner(TvmProperties properties, K8sBuilderHelper k8sBuilderHelper, ModelManager modelService) {
        super(properties, k8sBuilderHelper);
        this.modelService = modelService;
    }

    public K8sJobRunnable produce(Run run, Map<String, String> secretData) {
        TvmBuildRunSpec runSpec = TvmBuildRunSpec.with(run.getSpec());
        TvmFunctionSpec functionSpec = runSpec.getFunctionSpec();
        TvmBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        String funcName = taskAccessor.getFunction();

        // Resolve a store:// model key to the Model's concrete spec.path (s3://) up
        // front: the builder init container has no 'store' protocol, and format
        // auto-detect needs the source file extension. Direct s3:// / http(s)://
        // paths pass through unchanged.
        String inputUri = TvmRunnerHelper.resolveModelPath(functionSpec.getModel(), modelService);

        // ONNX is the only supported source format: an explicit spec.format=onnx always
        // proceeds, format auto (or unset) needs a .onnx extension to auto-detect.
        TvmFormat format = functionSpec.getFormat();
        boolean onnx = format == TvmFormat.onnx || (inputUri != null && inputUri.toLowerCase().endsWith(".onnx"));
        if (!onnx) {
            throw new IllegalStateException(
                    "cannot auto-detect TVM source format for path: " +
                    inputUri +
                    " — set spec.format explicitly (onnx) when the source has " +
                    "no recognizable extension (e.g. a store:// or folder path)");
        }

        // A folder source (trailing slash) has no filename: the init container downloads
        // its CONTENTS into input/, so fall back to the conventional entrypoint name
        // (model.onnx). Note extractFileName would return the last path segment (the
        // folder name) here, which is not a file in input/ — hence the explicit check.
        String inputFile = inputUri != null && inputUri.endsWith("/")
                ? DEFAULT_INPUT_FILE
                : TvmRunnerHelper.extractFileName(inputUri);
        if (!StringUtils.hasText(inputFile)) {
            inputFile = DEFAULT_INPUT_FILE;
        }

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmBuildTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_FUNCTION_NAME", funcName));
        envs.add(new CoreEnv("TVM_INPUT_FILE", inputFile));

        // from_onnx() conversion flags
        if (taskSpec.getKeepParamsInInput() != null) {
            envs.add(new CoreEnv("TVM_KEEP_PARAMS_IN_INPUT", String.valueOf(taskSpec.getKeepParamsInInput())));
        }
        if (taskSpec.getSanitizeInputNames() != null) {
            envs.add(new CoreEnv("TVM_SANITIZE_INPUT_NAMES", String.valueOf(taskSpec.getSanitizeInputNames())));
        }

        // ONNX preprocessing applied before conversion (simplify + shape inference)
        if (Boolean.TRUE.equals(taskSpec.getSimplify())) {
            envs.add(new CoreEnv("TVM_SIMPLIFY", "true"));
        }
        if (taskSpec.getTargetOpset() != null) {
            envs.add(new CoreEnv("TVM_TARGET_OPSET", String.valueOf(taskSpec.getTargetOpset())));
        }
        if (taskSpec.getOpsetOverride() != null) {
            envs.add(new CoreEnv("TVM_OPSET_OVERRIDE", String.valueOf(taskSpec.getOpsetOverride())));
        }
        if (Boolean.TRUE.equals(taskSpec.getStrictShapeInference())) {
            envs.add(new CoreEnv("TVM_STRICT_SHAPE_INFER", "true"));
        }
        if (Boolean.TRUE.equals(taskSpec.getDataProp())) {
            envs.add(new CoreEnv("TVM_DATA_PROP", "true"));
        }

        // runtime-tvm.yml pins builder-scripts.onnx to this same classpath default, so
        // the fallback only applies when the key is omitted from the config entirely.
        String script = loadClasspathScript(
                properties.getBuilderScripts() != null
                        ? properties.getBuilderScripts().getOrDefault(FORMAT, BUILDER_SCRIPT_CLASSPATH)
                        : BUILDER_SCRIPT_CLASSPATH);
        List<ContextSource> contextSources = TvmRunnerHelper.createContextSources(entrypoint, script);

        // http(s) single-file sources need a full FILE destination: the init
        // container's http downloader cannot write to a bare directory (s3 handles
        // both folder and single-file downloads into "input/" fine).
        String destination = inputUri != null && (inputUri.startsWith("http://") || inputUri.startsWith("https://"))
                ? "input/" + inputFile
                : "input/";
        List<ContextRef> contextRefs = Collections.singletonList(
                TvmRunnerHelper.inputContextRef(inputUri, destination));

        // An image set on the task overrides the builder image from runtime-tvm.yml.
        String image = resolveImage(
                taskSpec.getImage(),
                properties.getBuilders() != null ? properties.getBuilders().get(FORMAT) : null,
                "no builder image configured for format: " + FORMAT);

        List<CoreVolume> volumes = createVolumes(taskSpec);
        List<CoreEnv> coreSecrets = createSecrets(secretData);

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
                coreSecrets,
                volumes,
                contextRefs,
                taskSpec);
    }
}
