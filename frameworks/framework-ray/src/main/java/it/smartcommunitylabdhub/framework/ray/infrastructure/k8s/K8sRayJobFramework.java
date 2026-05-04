/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.k8s;

import io.kubernetes.client.openapi.ApiClient;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayJobRunnable;
import java.io.Serializable;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@FrameworkComponent(framework = K8sRayJobFramework.FRAMEWORK)
public class K8sRayJobFramework extends K8sRayBaseFramework<K8sRayJobRunnable> {

    public static final int DEADLINE_SECONDS = 3600 * 24 * 3; //3 days
    public static final int DEADLINE_MIN = 120;

    public static final String FRAMEWORK = "rayjob";
    public static final String KIND = "RayJob";
    public static final String PLURAL = "rayjobs";

    private int activeDeadlineSeconds = DEADLINE_SECONDS;
    private boolean suspend = false;

    public K8sRayJobFramework(ApiClient apiClient) {
        super(apiClient);
    }

    @Override
    protected String getKind() {
        return KIND;
    }

    @Override
    protected String getPlural() {
        return PLURAL;
    }

    @Autowired
    public void setActiveDeadlineSeconds(
        @Value("${ray.job.activeDeadlineSeconds}") Integer activeDeadlineSeconds
    ) {
        Assert.isTrue(activeDeadlineSeconds > DEADLINE_MIN, "Minimum deadline seconds is " + DEADLINE_MIN);
        this.activeDeadlineSeconds = activeDeadlineSeconds;
    }
    @Autowired
    public void setSuspend(@Value("${ray.job.suspend}") Boolean suspend) {
        if (suspend != null) {
            this.suspend = suspend.booleanValue();
        }
    }


    @Override
    protected Map<String, Serializable> getSpec(K8sRayJobRunnable runnable) {
        return runnable.getSpec();
    }

    @Override
    protected boolean isRequiresSecret(K8sRayJobRunnable runnable) {
        return Boolean.TRUE.equals(runnable.getRequiresSecret());
    }

    @Override
    protected void setStatus(K8sRayJobRunnable runnable, Map<String, Serializable> status) {
        runnable.setStatus(status);
    }
}
