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
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmBaseRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmRunnerHelper;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

// Shared base for the per-format build frontends. Assembles the single K8s Job that
// runs builder_<format>.py on the tvm-toolkit image: wiring the input download, the
// IR output S3 prefix, the common + format-specific env vars and the pod scripts.
// Subclasses only contribute the format name and its extra env vars.
public abstract class TvmBuildFrontendRunner extends TvmBaseRunner {

    protected TvmBuildFrontendRunner(TvmProperties properties, K8sBuilderHelper k8sBuilderHelper) {
        super(properties, k8sBuilderHelper);
    }

    protected K8sJobRunnable buildJobRunnable(
            Run run,
            TvmFunctionSpec functionSpec,
            TvmBuildTaskSpec taskSpec,
            Map<String, String> secretData,
            String format,
            String defaultInputFile,
            List<CoreEnv> formatEnvs) {
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        String funcName = taskAccessor.getFunction();

        // Already resolved to a concrete s3:// / http(s):// URI by TvmBuildRunner before
        // dispatch (the builder init container has no 'store' protocol).
        String inputUri = functionSpec.getModel();
        // A folder source (trailing slash) has no filename: the init container downloads
        // its CONTENTS into input/, so fall back to the format's conventional entrypoint
        // name (e.g. model.onnx). Note extractFileName would return the last path segment
        // (the folder name) here, which is not a file in input/ — hence the explicit check.
        String inputFile = inputUri != null && inputUri.endsWith("/")
                ? defaultInputFile
                : TvmRunnerHelper.extractFileName(inputUri);
        if (!StringUtils.hasText(inputFile)) {
            inputFile = defaultInputFile;
        }

        List<CoreEnv> envs = createEnvList(run, taskSpec);
        envs.add(new CoreEnv("TVM_TASK_KIND", TvmBuildTaskSpec.KIND));
        envs.add(new CoreEnv("TVM_FUNCTION_NAME", funcName));
        envs.add(new CoreEnv("TVM_INPUT_FILE", inputFile));
        if (formatEnvs != null) {
            envs.addAll(formatEnvs);
        }

        String defaultScript = "classpath:/runtime-tvm/docker/builder_" + format + ".py";
        String script = loadClasspathScript(
                properties.getBuilderScripts() != null
                        ? properties.getBuilderScripts().getOrDefault(format, defaultScript)
                        : defaultScript);
        List<ContextSource> contextSources = TvmRunnerHelper.createContextSources(entrypoint, script);

        // http(s) single-file sources need a full FILE destination: the init
        // container's http downloader cannot write to a bare directory (s3 handles
        // both folder and single-file downloads into "input/" fine).
        String destination = inputUri != null && (inputUri.startsWith("http://") || inputUri.startsWith("https://"))
                ? "input/" + inputFile
                : "input/";
        List<ContextRef> contextRefs = Collections.singletonList(
                TvmRunnerHelper.inputContextRef(inputUri, destination));

        // An image set on the task overrides the per-format builder image from runtime-tvm.yml.
        String image = resolveImage(
                taskSpec.getImage(),
                properties.getBuilders() != null ? properties.getBuilders().get(format) : null,
                "no builder image configured for format: " + format);

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
