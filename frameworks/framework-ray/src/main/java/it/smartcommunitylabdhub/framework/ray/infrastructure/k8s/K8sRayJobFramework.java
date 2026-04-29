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
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FrameworkComponent(framework = K8sRayJobFramework.FRAMEWORK)
public class K8sRayJobFramework extends K8sRayBaseFramework<K8sRayJobRunnable> {

    public static final String FRAMEWORK = "rayjob";
    public static final String KIND = "RayJob";
    public static final String PLURAL = "rayjobs";

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
