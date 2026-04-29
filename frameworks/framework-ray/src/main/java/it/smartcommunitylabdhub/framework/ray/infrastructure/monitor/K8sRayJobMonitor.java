/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.monitor;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayJobFramework;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayJobRunnable;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import java.io.Serializable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = K8sRayJobFramework.FRAMEWORK)
public class K8sRayJobMonitor extends K8sRayBaseMonitor<K8sRayJobRunnable> {

    public K8sRayJobMonitor(RunnableStore<K8sRayJobRunnable> runnableStore, K8sRayJobFramework framework) {
        super(runnableStore, framework);
    }

    @Override
    protected void storeStatus(K8sRayJobRunnable runnable, Map<String, Serializable> status) {
        runnable.setStatus(status);
    }

    @Override
    protected void mapStatus(K8sRayJobRunnable runnable, Map<String, Serializable> status) {
        if (status == null || status.isEmpty()) {
            runnable.setState(K8sRunnableState.PENDING.name());
            return;
        }

        // RayJob status: jobDeploymentStatus ("Initializing"/"Running"/"Complete"/"Failed"/"Suspended"),
        // jobStatus ("PENDING"/"RUNNING"/"SUCCEEDED"/"FAILED"/"STOPPED"), message
        Object deployObj = status.get("jobDeploymentStatus");
        Object jobObj = status.get("jobStatus");
        String deploy = deployObj != null ? deployObj.toString() : null;
        String job = jobObj != null ? jobObj.toString() : null;

        Object message = status.get("message");
        if (message != null) {
            runnable.setMessage(message.toString());
        }

        //terminal states first
        if ("Failed".equalsIgnoreCase(deploy) || "FAILED".equalsIgnoreCase(job)) {
            runnable.setState(K8sRunnableState.ERROR.name());
            runnable.setError("RayJob failed: " + (message != null ? message.toString() : "unknown"));
            return;
        }
        if ("Complete".equalsIgnoreCase(deploy) || "SUCCEEDED".equalsIgnoreCase(job)) {
            runnable.setState(K8sRunnableState.COMPLETED.name());
            return;
        }
        if ("Suspended".equalsIgnoreCase(deploy) || "STOPPED".equalsIgnoreCase(job)) {
            runnable.setState(K8sRunnableState.STOPPED.name());
            return;
        }
        if ("Running".equalsIgnoreCase(deploy) || "RUNNING".equalsIgnoreCase(job)) {
            runnable.setState(K8sRunnableState.RUNNING.name());
            return;
        }

        //default: still pending
        runnable.setState(K8sRunnableState.PENDING.name());
    }
}
