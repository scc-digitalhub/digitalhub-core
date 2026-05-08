/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.k8s;

import io.kubernetes.client.openapi.ApiClient;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.framework.ray.model.ray.RayClusterSpec;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayServiceRunnable;
import java.io.Serializable;
import java.util.Map;

import org.apache.commons.lang3.NotImplementedException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@FrameworkComponent(framework = K8sRayServiceFramework.FRAMEWORK)
public class K8sRayServiceFramework extends K8sRayBaseFramework<K8sRayServiceRunnable> {

    public static final String FRAMEWORK = "rayservice";
    public static final String KIND = "RayService";
    public static final String PLURAL = "rayservices";

    public K8sRayServiceFramework(ApiClient apiClient) {
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
    protected Map<String, Serializable> getSpec(K8sRayServiceRunnable runnable, RayClusterSpec clusterSpec) {
        // return runnable.getSpec();
        throw new NotImplementedException("getSpec is not supported for RayService");
    }

    @Override
    protected void setStatus(K8sRayServiceRunnable runnable, Map<String, Serializable> status) {
        runnable.setStatus(status);
    }
}
