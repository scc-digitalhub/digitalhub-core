/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.runnables;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.framework.ray.infrastructure.k8s.K8sRayServiceFramework;
import it.smartcommunitylabdhub.framework.ray.model.RayServiceModel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@RunnableComponent(framework = K8sRayServiceFramework.FRAMEWORK)
@Getter
@Setter
@NoArgsConstructor
@SuperBuilder(toBuilder = true)
@ToString(callSuper = true)
public class K8sRayServiceRunnable extends K8sRayRunnable<RayServiceModel> {

    @Override
    public String getFramework() {
        return K8sRayServiceFramework.FRAMEWORK;
    }
    @Override
    public boolean initAllPods() {
        return true;
    }
}
