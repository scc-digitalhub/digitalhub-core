/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.monitor;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayServiceFramework;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayServiceRunnable;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import java.io.Serializable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = K8sRayServiceFramework.FRAMEWORK)
public class K8sRayServiceMonitor extends K8sRayBaseMonitor<K8sRayServiceRunnable> {

    public K8sRayServiceMonitor(
        RunnableStore<K8sRayServiceRunnable> runnableStore,
        K8sRayServiceFramework framework
    ) {
        super(runnableStore, framework);
    }

    @Override
    protected void storeStatus(K8sRayServiceRunnable runnable, Map<String, Serializable> status) {
        runnable.setStatus(status);
    }

    @Override
    protected void mapStatus(K8sRayServiceRunnable runnable, Map<String, Serializable> status) {
        if (status == null || status.isEmpty()) {
            runnable.setState(K8sRunnableState.PENDING.name());
            return;
        }

        // RayService status: serviceStatus ("Running"/"WaitForServeDeploymentReady"/"FailedToGetOrCreateRayCluster"
        // /"WaitForDashboard"/"Restarting"), activeServiceStatus etc.
        Object svcObj = status.get("serviceStatus");
        String svc = svcObj != null ? svcObj.toString() : null;

        if (svc == null) {
            runnable.setState(K8sRunnableState.RUNNING.name());
            return;
        }

        if ("Running".equalsIgnoreCase(svc)) {
            runnable.setState(K8sRunnableState.RUNNING.name());
        } else if (svc.toLowerCase().startsWith("failed")) {
            runnable.setState(K8sRunnableState.ERROR.name());
            Object reason = status.get("message");
            runnable.setError(
                "RayService failed: " + (reason != null ? reason.toString() : svc)
            );
        } else {
            //transitional states (WaitForServeDeploymentReady, Restarting, etc.)
            runnable.setState(K8sRunnableState.PENDING.name());
        }
    }
}
