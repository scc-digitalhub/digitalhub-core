/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.framework.k8s.base;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.base.ExecutableBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtimes.base.AbstractBaseRuntime;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class K8sBaseRuntime<
    F extends ExecutableBaseSpec, S extends RunBaseSpec, Z extends RunBaseStatus, R extends K8sRunnable
>
    extends AbstractBaseRuntime<F, S, Z, R> {

    @Nullable
    protected K8sBuilderHelper k8sBuilderHelper;

    @Autowired(required = false)
    public void setK8sBuilderHelper(K8sBuilderHelper k8sBuilderHelper) {
        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Z onRunning(@NotNull Run run, RunRunnable runnable) {
        if (runnable != null && runnable instanceof K8sRunnable) {
            K8sRunnable k8sRunnable = (K8sRunnable) runnable;
            RunBaseStatus status = RunBaseStatus
                .baseBuilder()
                .state(k8sRunnable.getState())
                .message(k8sRunnable.getMessage())
                .build();

            return (Z) status;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Z onComplete(@NotNull Run run, RunRunnable runnable) {
        if (runnable != null && runnable instanceof K8sRunnable) {
            K8sRunnable k8sRunnable = (K8sRunnable) runnable;
            RunBaseStatus status = RunBaseStatus
                .baseBuilder()
                .state(k8sRunnable.getState())
                .message(k8sRunnable.getMessage())
                .build();

            return (Z) status;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Z onError(@NotNull Run run, RunRunnable runnable) {
        if (runnable != null && runnable instanceof K8sRunnable) {
            K8sRunnable k8sRunnable = (K8sRunnable) runnable;
            RunBaseStatus status = RunBaseStatus
                .baseBuilder()
                .state(k8sRunnable.getState())
                .message(k8sRunnable.getError())
                .build();

            return (Z) status;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Z onStopped(@NotNull Run run, RunRunnable runnable) {
        if (runnable != null && runnable instanceof K8sRunnable) {
            K8sRunnable k8sRunnable = (K8sRunnable) runnable;
            RunBaseStatus status = RunBaseStatus
                .baseBuilder()
                .state(k8sRunnable.getState())
                .message(k8sRunnable.getMessage())
                .build();

            return (Z) status;
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    @Override
    public Z onDeleted(@NotNull Run run, RunRunnable runnable) {
        //cleanup
        super.onDeleted(run, runnable);

        //collect
        if (runnable != null && runnable instanceof K8sRunnable) {
            K8sRunnable k8sRunnable = (K8sRunnable) runnable;
            RunBaseStatus status = RunBaseStatus
                .baseBuilder()
                .state(k8sRunnable.getState())
                .message(k8sRunnable.getMessage())
                .build();

            return (Z) status;
        }

        return null;
    }
}
