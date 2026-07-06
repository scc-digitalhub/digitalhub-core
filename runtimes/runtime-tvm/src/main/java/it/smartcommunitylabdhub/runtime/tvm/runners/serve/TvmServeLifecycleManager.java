/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.serve;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.serve.TvmServeRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.status.TvmRunStatus;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunLifecycleManager;

// Binds the run lifecycle (state transitions and completion hooks) for
// tvm+serve runs to TvmRuntime. The @RuntimeComponent kind registers this
// manager for tvm+serve:run so the framework drives those runs through it.
@RuntimeComponent(runtime = TvmServeRunSpec.KIND)
public class TvmServeLifecycleManager extends RunLifecycleManager<TvmRunSpec, TvmRunStatus, K8sRunnable> {

    TvmServeLifecycleManager(TvmRuntime runtime) {
        super(runtime);
    }
}
