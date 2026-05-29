/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.job;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.ray.RayRuntime;
import it.smartcommunitylabdhub.runtime.ray.specs.RayRunSpec;
import it.smartcommunitylabdhub.runtime.ray.specs.RayRunStatus;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunLifecycleManager;

@RuntimeComponent(runtime = RayJobRunSpec.KIND)
public class RayJobLifecycleManager extends RunLifecycleManager<RayRunSpec, RayRunStatus, K8sRunnable> {

    RayJobLifecycleManager(RayRuntime runtime) {
        super(runtime);
    }
}
