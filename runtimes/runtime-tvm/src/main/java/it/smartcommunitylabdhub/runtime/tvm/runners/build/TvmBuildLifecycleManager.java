/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.build;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.status.TvmRunStatus;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunLifecycleManager;

// Registers the tvm+build run kind with the standard run lifecycle (state machine
// and runnable dispatch); all behavior is inherited from RunLifecycleManager.
@RuntimeComponent(runtime = TvmBuildRunSpec.KIND)
public class TvmBuildLifecycleManager extends RunLifecycleManager<TvmRunSpec, TvmRunStatus, K8sRunnable> {

    TvmBuildLifecycleManager(TvmRuntime runtime) {
        super(runtime);
    }
}
