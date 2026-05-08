/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.k8s;

import io.kubernetes.client.openapi.ApiClient;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.framework.ray.model.ray.JobSubmissionMode;
import it.smartcommunitylabdhub.framework.ray.model.ray.RayClusterSpec;
import it.smartcommunitylabdhub.framework.ray.model.ray.RayJobSpec;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayJobRunnable;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@FrameworkComponent(framework = K8sRayJobFramework.FRAMEWORK)
public class K8sRayJobFramework extends K8sRayBaseFramework<K8sRayJobRunnable> {

    public static final String FRAMEWORK = "rayjob";
    public static final String KIND = "RayJob";
    public static final String PLURAL = "rayjobs";

    public static final int DEADLINE_SECONDS = 3600 * 24 * 3; //3 days
    public static final int DEADLINE_MIN = 120;

    public static final int DEFAULT_BACKOFF_LIMIT = 0;

    private int activeDeadlineSeconds = DEADLINE_SECONDS;


    public K8sRayJobFramework(ApiClient apiClient) {
        super(apiClient);
    }

    @Autowired
    public void setActiveDeadlineSeconds(
        @Value("${ray.job.activeDeadlineSeconds}") Integer activeDeadlineSeconds
    ) {
        Assert.isTrue(activeDeadlineSeconds > DEADLINE_MIN, "Minimum deadline seconds is " + DEADLINE_MIN);
        this.activeDeadlineSeconds = activeDeadlineSeconds;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        //load templates
        this.templates = loadTemplates(K8sRayJobRunnable.class);
    }

    @Override
    protected String getKind() {
        return KIND;
    }

    @Override
    protected String getPlural() {
        return PLURAL;
    }

    @Override
    protected Map<String, Serializable> getSpec(K8sRayJobRunnable runnable, RayClusterSpec clusterSpec) {

        int backoffLimit = Optional.ofNullable(runnable.getSpec().getBackoffLimit()).orElse(DEFAULT_BACKOFF_LIMIT).intValue();

        RayJobSpec spec = RayJobSpec.builder()
            .jobId(runnable.getId())
            .activeDeadlineSeconds(activeDeadlineSeconds)
            .backoffLimit(backoffLimit)
            .rayClusterSpec(clusterSpec)
            .clusterSelector(runnable.getSpec().getClusterSelector())
            .entrypoint(runnable.getSpec().getEntrypoint())
            .runtimeEnvYAML(buildEnvYAML(runnable))
            .shutdownAfterJobFinishes(runnable.getSpec().getShutdownAfterJobFinishes())
            .ttlSecondsAfterFinished(runnable.getSpec().getTtlSecondsAfterFinished())
            .preRunningDeadlineSeconds(runnable.getSpec().getPreRunningDeadlineSeconds())
            .submissionMode(JobSubmissionMode.SidecarMode)
            .build();
        return mapper.convertValue(spec, typeRef);
    }

    @Override
    protected void setStatus(K8sRayJobRunnable runnable, Map<String, Serializable> status) {
        runnable.setStatus(status);
    }

    private String buildEnvYAML(K8sRayJobRunnable runnable) {
        // working dir
        // env vars ? only if cluster ref is used. from envs, no secrets.
        // dependencies ? uv run | ./requirements.txt | conda env 
        return "";
    }
}
