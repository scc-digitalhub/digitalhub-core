/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.compile;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.compile.TvmCompileRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.status.TvmRunStatus;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunLifecycleManager;

// Binds the run lifecycle (state transitions and completion hooks) for
// tvm+compile runs to TvmRuntime; all behavior comes from RunLifecycleManager.
@RuntimeComponent(runtime = TvmCompileRunSpec.KIND)
public class TvmCompileLifecycleManager extends RunLifecycleManager<TvmRunSpec, TvmRunStatus, K8sRunnable> {

    TvmCompileLifecycleManager(TvmRuntime runtime) {
        super(runtime);
    }
}
