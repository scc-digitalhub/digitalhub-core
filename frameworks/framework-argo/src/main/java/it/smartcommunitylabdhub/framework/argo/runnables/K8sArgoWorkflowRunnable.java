/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.argo.runnables;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.framework.argo.infrastructure.k8s.K8sArgoWorkflowFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@RunnableComponent(framework = K8sArgoWorkflowFramework.FRAMEWORK)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public final class K8sArgoWorkflowRunnable extends K8sRunnable {

    private String workflowSpec;

    private Map<String, Serializable> parameters;

    @Override
    public String getFramework() {
        return K8sArgoWorkflowFramework.FRAMEWORK;
    }
}
