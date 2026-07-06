/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners;

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

// Base for all TVM runners (build/compile Jobs and the serve Deployment). Resolves
// the effective pod identity (uid/gid/home) from TvmProperties, falling back to
// TvmRuntime defaults, loads the pod entrypoint script, and provides the
// env/secret/volume/label/image helpers plus the common-config applier shared by
// every TVM runnable. Model publishing (and its S3 destination) is handled entirely
// by the digitalhub SDK inside the pod, driven by the platform S3 credentials.
public abstract class TvmBaseRunner {

    protected final TvmProperties properties;
    protected final K8sBuilderHelper k8sBuilderHelper;

    protected final int userId;
    protected final int groupId;
    protected final String homeDir;
    protected final String volumeSizeSpec;

    private final DefaultResourceLoader loader = new DefaultResourceLoader();
    protected final String entrypoint;

    protected TvmBaseRunner(TvmProperties properties, K8sBuilderHelper k8sBuilderHelper) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties;
        this.k8sBuilderHelper = k8sBuilderHelper;

        this.userId = properties.getUserId() != null ? properties.getUserId() : TvmRuntime.UID;
        this.groupId = properties.getGroupId() != null ? properties.getGroupId() : TvmRuntime.GID;
        this.homeDir = properties.getHomeDir() != null ? properties.getHomeDir() : TvmRuntime.HOME_DIR;
        this.volumeSizeSpec = properties.getVolumeSize();

        String path = properties.getEntrypoint() != null
            ? properties.getEntrypoint()
            : "classpath:/runtime-tvm/docker/entrypoint.sh";
        this.entrypoint = readResource(loader.getResource(path));
    }

    protected String loadClasspathScript(String classpathLocation) {
        return readResource(loader.getResource(classpathLocation));
    }

    private String readResource(Resource resource) {
        try {
            return new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error reading classpath resource: " + resource);
        }
    }

    // Standard TVM_* env contract read by the pod scripts (entrypoint.sh + builders):
    // project/run identity plus the home/input/output dirs.
    // Task-declared envs are appended last so they can override the defaults.
    protected List<CoreEnv> createEnvList(Run run, K8sFunctionTaskBaseSpec taskSpec) {
        List<CoreEnv> envs = new ArrayList<>();
        envs.add(new CoreEnv("PROJECT_NAME", run.getProject()));
        envs.add(new CoreEnv("RUN_ID", run.getId()));
        envs.add(new CoreEnv("TVM_HOME_DIR", homeDir));
        envs.add(new CoreEnv("TVM_INPUT_DIR", homeDir + "/input"));
        envs.add(new CoreEnv("TVM_OUTPUT_DIR", homeDir + "/output"));
        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(envs::addAll);
        return envs;
    }

    protected List<CoreEnv> createSecrets(Map<String, String> secretData) {
        return secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();
    }

    // Task-declared volumes plus a shared scratch volume the builder uses to stage
    // input and output; sized from the task's disk request or the configured default.
    protected List<CoreVolume> createVolumes(K8sFunctionTaskBaseSpec taskSpec) {
        List<CoreVolume> volumes = new ArrayList<>(
            taskSpec.getVolumes() != null ? taskSpec.getVolumes() : List.of()
        );
        String size = taskSpec.getResources() != null && taskSpec.getResources().getDisk() != null
            ? taskSpec.getResources().getDisk()
            : volumeSizeSpec;
        CoreResource diskResource = new CoreResource();
        diskResource.setDisk(size);
        if (k8sBuilderHelper != null) {
            CoreVolume shared = k8sBuilderHelper.buildSharedVolume(diskResource);
            if (shared != null) {
                volumes.add(shared);
            }
        }
        return volumes;
    }

    // The single `function=<name>` label placed on every TVM runnable (null when no
    // K8sBuilderHelper is available, e.g. in unit tests).
    protected List<CoreLabel> functionLabels(String funcName) {
        return k8sBuilderHelper != null
            ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), funcName))
            : null;
    }

    // Picks the effective image: a task-level override wins, otherwise the configured
    // default; throws with the given message when neither is set.
    protected String resolveImage(String taskImage, String defaultImage, String missingMessage) {
        String image = StringUtils.hasText(taskImage) ? taskImage : defaultImage;
        if (!StringUtils.hasText(image)) {
            throw new IllegalArgumentException(missingMessage);
        }
        return image;
    }

    // Applies the config shared by EVERY TVM runnable — Job (build/compile) and Serve
    // alike: run identity, task kind, image, function label, envs/secrets, context
    // refs, resources, volumes and the pod security context. The caller builds the
    // runnable with only its type-specific fields (Job: command/args/contextSources;
    // Serve: ports/replicas/service) and passes it here to fill in the rest.
    protected <T extends K8sRunnable> T applyCommon(
        T runnable,
        Run run,
        String taskKind,
        String funcName,
        String image,
        List<CoreEnv> envs,
        List<CoreEnv> secrets,
        List<CoreVolume> volumes,
        List<ContextRef> contextRefs,
        K8sFunctionTaskBaseSpec taskSpec
    ) {
        runnable.setRuntime(TvmRuntime.RUNTIME);
        runnable.setTask(taskKind);
        runnable.setState(State.READY.name());
        runnable.setLabels(functionLabels(funcName));
        runnable.setImage(image);
        runnable.setEnvs(envs);
        runnable.setSecrets(secrets);
        runnable.setContextRefs(contextRefs);
        runnable.setResources(k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(taskSpec.getResources()) : null);
        runnable.setVolumes(volumes);
        runnable.setTemplate(taskSpec.getProfile());
        runnable.setFsGroup(groupId);
        runnable.setRunAsGroup(groupId);
        runnable.setRunAsUser(userId);
        runnable.setId(run.getId());
        runnable.setProject(run.getProject());
        return runnable;
    }
}
