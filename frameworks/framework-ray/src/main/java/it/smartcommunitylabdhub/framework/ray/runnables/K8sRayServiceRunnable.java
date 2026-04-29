/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.runnables;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayServiceFramework;
import it.smartcommunitylabdhub.framework.ray.model.RayServiceModel;

import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@RunnableComponent(framework = K8sRayServiceFramework.FRAMEWORK)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class K8sRayServiceRunnable extends K8sRunnable {

    private RayServiceModel spec;

    private Map<String, Serializable> status;

    private Boolean requiresSecret;

    @Override
    public String getFramework() {
        return K8sRayServiceFramework.FRAMEWORK;
    }
}
