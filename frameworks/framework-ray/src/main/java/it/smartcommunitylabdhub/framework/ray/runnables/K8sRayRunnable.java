/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.runnables;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayJobFramework;
import it.smartcommunitylabdhub.framework.ray.model.RayJobModel;
import it.smartcommunitylabdhub.framework.ray.model.RayModel;

import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public abstract class K8sRayRunnable<T extends RayModel> extends K8sRunnable {

    private T spec;
    private Map<String, Serializable> status;

    public abstract boolean initAllPods();
}
