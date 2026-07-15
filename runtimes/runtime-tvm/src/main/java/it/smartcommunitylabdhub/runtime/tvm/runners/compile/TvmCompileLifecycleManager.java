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

// Registers the tvm+compile run kind with the standard RunLifecycleManager.
@RuntimeComponent(runtime = TvmCompileRunSpec.KIND)
public class TvmCompileLifecycleManager extends RunLifecycleManager<TvmRunSpec, TvmRunStatus, K8sRunnable> {

    TvmCompileLifecycleManager(TvmRuntime runtime) {
        super(runtime);
    }
}
