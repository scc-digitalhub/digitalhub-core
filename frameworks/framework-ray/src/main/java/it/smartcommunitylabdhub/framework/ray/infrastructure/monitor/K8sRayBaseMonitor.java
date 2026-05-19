/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.monitor;

import io.kubernetes.client.openapi.models.EventsV1Event;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sBaseMonitor;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayBaseFramework;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayRunnable;
import it.smartcommunitylabdhub.runtimes.store.RunnableStore;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

/**
 * Base monitor for Ray CR-backed runnables. Refreshes state by reading the CR's
 * {@code status.phase} (or status-equivalent field) plus pod/event/log/metric collection.
 *
 * Subclasses translate the CR-specific phase string into a {@link K8sRunnableState}.
 */
@Slf4j
public abstract class K8sRayBaseMonitor<T extends K8sRayRunnable<?>> extends K8sBaseMonitor<T> {

    protected final K8sRayBaseFramework<T> framework;

    protected K8sRayBaseMonitor(RunnableStore<T> runnableStore, K8sRayBaseFramework<T> framework) {
        super(runnableStore);
        Assert.notNull(framework, "ray framework is required");
        this.framework = framework;
    }

    /**
     * Map a Ray CR status into a runnable state. Default implementation handles common
     * transitions; subclasses may override to inspect type-specific fields.
     *
     * @param runnable runnable being refreshed (will be mutated for state/error/message)
     * @param status   the {@code status} sub-tree of the CR, may be null/empty
     */
    protected abstract void mapStatus(T runnable, Map<String, Serializable> status);

    /**
     * Persist the parsed status onto the runnable.
     */
    protected abstract void storeStatus(T runnable, Map<String, Serializable> status);

    @Override
    public T refresh(T runnable) {
        try {
            DynamicKubernetesObject cr = framework.get(framework.build(runnable));
            if (cr == null) {
                log.error("Missing or invalid Ray CR for {}", runnable.getId());
                runnable.setState(K8sRunnableState.ERROR.name());
                runnable.setError("Ray CR missing or invalid");
                return runnable;
            }

            Map<String, Serializable> status = K8sRayBaseFramework.extractMap(cr, "status");
            storeStatus(runnable, status);
            mapStatus(runnable, status);

            //expose CR spec/status snapshot in results
            try {
                HashMap<String, Serializable> crMap = K8sRayBaseFramework.jsonElementToMap(cr.getRaw());
                runnable.setResults(
                    MapUtils.mergeMultipleMaps(runnable.getResults(), Map.of(cr.getKind(), crMap))
                );
            } catch (Exception e) {
                log.error("error reading CR raw: {}", e.getMessage());
            }

            //events
            List<EventsV1Event> events = null;
            try {
                events = framework.events(cr);
            } catch (K8sFrameworkException e) {
                log.error("error collecting events for {}: {}", runnable.getId(), e.getMessage());
            }

            if (events != null) {
                runnable.setEvents(new ArrayList<>(mapper.convertValue(events, arrayRef)));
            }

            // all pods related to the CR
            List<V1Pod> pods = framework.pods(cr);
            // only the pods relevant for status, logs and metrics
            List<V1Pod> statusPods = framework.statusPods(pods, runnable);
            // need to include log pods in results for compatibility of the representation
            try {
                runnable.setResults(
                    MapUtils.mergeMultipleMaps(
                        runnable.getResults(),
                        Map.of("pods", statusPods != null ? mapper.convertValue(statusPods, arrayRef) : new ArrayList<>())
                    )
                );
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }
            //logs
            try {
                runnable.setLogs(framework.logs(pods, runnable));
            } catch (K8sFrameworkException e1) {
                log.error("error collecting logs for {}: {}", runnable.getId(), e1.getMessage());
            }

            //metrics
            try {
                runnable.setMetrics(framework.metrics(pods, runnable));
            } catch (K8sFrameworkException e1) {
                log.error("error collecting metrics for {}: {}", runnable.getId(), e1.getMessage());
            }
        } catch (K8sFrameworkException e) {
            runnable.setState(K8sRunnableState.ERROR.name());
            runnable.setError(e.toError());
        }

        return runnable;
    }
}
