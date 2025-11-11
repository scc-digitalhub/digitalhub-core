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

package it.smartcommunitylabdhub.framework.k8s.infrastructure.watcher;

import io.fabric8.kubernetes.client.KubernetesClient;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sDeploymentFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor.K8sDeploymentMonitor;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sDeploymentRunnable;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class K8sDeploymentWatcher extends K8sBaseWatcher<K8sDeploymentRunnable> {

    public K8sDeploymentWatcher(KubernetesClient client, K8sDeploymentMonitor k8sDeploymentMonitor) {
        super(client, k8sDeploymentMonitor);
    }

    @Override
    public void start() {
        //build core labels for deployment
        String prefix = k8sLabelHelper.getCoreLabelsNamespace();
        Map<String, String> labels = Map.of(
            "app.kubernetes.io/managed-by",
            prefix,
            prefix + "/" + "framework",
            K8sDeploymentFramework.FRAMEWORK
        );

        //watch deployments with core labels
        log.debug("watch deployments with labels: {}", labels);
        executor.submit(() -> watchDeployments(labels));

        //watch pods with core labels
        log.debug("watch pods with labels: {}", labels);
        executor.submit(() -> watchPods(labels));
    }

    private void watchDeployments(Map<String, String> labels) {
        client.apps().deployments().inNamespace(namespace).withLabels(labels).watch(buildWatcher());
    }

    private void watchPods(Map<String, String> labels) {
        client.pods().inNamespace(namespace).withLabels(labels).watch(buildWatcher());
    }
}
