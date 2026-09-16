/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners;

import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

// Base class of the three TVM runners (build, compile, serve). It holds what every TVM
// pod shares: the pod identity, the home folder, the entrypoint script, the standard env
// variables, the scratch volume and the fields common to every runnable.
public abstract class TvmBaseRunner {

    private static final String DEFAULT_ENTRYPOINT = "classpath:/runtime-tvm/docker/entrypoint.sh";

    protected final TvmProperties properties;
    protected final K8sBuilderHelper k8sBuilderHelper;
    protected final K8sLabelHelper k8sLabelHelper;

    protected final int userId;
    protected final int groupId;
    protected final String homeDir;
    protected final String volumeSizeSpec;
    protected final String entrypoint;

    protected TvmBaseRunner(
        TvmProperties properties,
        K8sBuilderHelper k8sBuilderHelper,
        K8sLabelHelper k8sLabelHelper
    ) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties;
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.k8sLabelHelper = k8sLabelHelper;

        // Pod identity and storage: runtime-tvm.yml wins, TvmRuntime holds the defaults.
        this.userId = Objects.requireNonNullElse(properties.getUserId(), TvmRuntime.UID);
        this.groupId = Objects.requireNonNullElse(properties.getGroupId(), TvmRuntime.GID);
        this.homeDir = Objects.requireNonNullElse(properties.getHomeDir(), TvmRuntime.HOME_DIR);
        this.volumeSizeSpec = properties.getVolumeSize();
        this.entrypoint = TvmRunnerHelper.loadClasspath(
            Objects.requireNonNullElse(properties.getEntrypoint(), DEFAULT_ENTRYPOINT)
        );
    }

    // Adds NAME=value to the pod env only when the value is set. Unset values (null or
    // blank) are skipped, so the pod scripts keep their own defaults.
    protected static void addEnv(List<CoreEnv> envs, String name, Object value) {
        if (value != null && StringUtils.hasText(String.valueOf(value))) {
            envs.add(new CoreEnv(name, String.valueOf(value)));
        }
    }

    // True when the task sets this env variable itself through spec.envs.
    protected static boolean hasTaskEnv(K8sFunctionTaskBaseSpec taskSpec, String name) {
        return (
            taskSpec.getEnvs() != null &&
            taskSpec
                .getEnvs()
                .stream()
                .anyMatch(env -> name.equals(env.name()))
        );
    }

    // CPU cores requested through resources.cpu (rounded up), or null when not set.
    protected static Integer requestedCpuCores(K8sFunctionTaskBaseSpec taskSpec) {
        if (taskSpec.getResources() == null || !StringUtils.hasText(taskSpec.getResources().getCpu())) {
            return null;
        }
        return TvmRunnerHelper.parseCpuCores(taskSpec.getResources().getCpu());
    }

    // Env variables every TVM pod receives, followed by the task's own spec.envs.
    protected List<CoreEnv> createEnvList(Run run, K8sFunctionTaskBaseSpec taskSpec) {
        List<CoreEnv> envs = new ArrayList<>();
        envs.add(new CoreEnv("PROJECT_NAME", run.getProject()));
        envs.add(new CoreEnv("RUN_ID", run.getId()));
        envs.add(new CoreEnv("TVM_HOME_DIR", homeDir));
        envs.add(new CoreEnv("TVM_INPUT_DIR", homeDir + "/input"));
        envs.add(new CoreEnv("TVM_OUTPUT_DIR", homeDir + "/output"));
        if (taskSpec.getEnvs() != null) {
            envs.addAll(taskSpec.getEnvs());
        }
        return envs;
    }

    // Project secrets become plain env variables of the pod.
    protected List<CoreEnv> createSecrets(Map<String, String> secretData) {
        if (secretData == null) {
            return null;
        }
        return secretData
            .entrySet()
            .stream()
            .map(e -> new CoreEnv(e.getKey(), e.getValue()))
            .toList();
    }

    // The task volumes plus a shared scratch volume for input/ and output/, sized from
    // resources.disk or the runtime default.
    protected List<CoreVolume> createVolumes(K8sFunctionTaskBaseSpec taskSpec) {
        List<CoreVolume> volumes = new ArrayList<>(taskSpec.getVolumes() != null ? taskSpec.getVolumes() : List.of());
        if (k8sBuilderHelper == null) {
            return volumes;
        }
        CoreResource disk = new CoreResource();
        disk.setDisk(
            taskSpec.getResources() != null && taskSpec.getResources().getDisk() != null
                ? taskSpec.getResources().getDisk()
                : volumeSizeSpec
        );
        CoreVolume shared = k8sBuilderHelper.buildSharedVolume(disk);
        if (shared != null) {
            volumes.add(shared);
        }
        return volumes;
    }

    // The function=<name> label put on every TVM runnable (null without a label helper,
    // e.g. in unit tests).
    protected List<CoreLabel> functionLabels(String funcName) {
        return k8sLabelHelper != null
            ? List.of(new CoreLabel(k8sLabelHelper.buildCoreLabel("function"), funcName))
            : null;
    }

    // The image to run: the task override wins over the configured default.
    protected String resolveImage(String taskImage, String defaultImage, String missingMessage) {
        String image = StringUtils.hasText(taskImage) ? taskImage : defaultImage;
        if (!StringUtils.hasText(image)) {
            throw new IllegalArgumentException(missingMessage);
        }
        return image;
    }

    // Fills the fields shared by the Job and Serve runnables; each runner only sets its
    // own specific fields before calling this.
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
        runnable.setResources(
            k8sBuilderHelper != null ? k8sBuilderHelper.convertResources(taskSpec.getResources()) : null
        );
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
