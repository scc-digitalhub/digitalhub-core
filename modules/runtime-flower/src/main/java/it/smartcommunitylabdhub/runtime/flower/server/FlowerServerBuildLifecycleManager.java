/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.runtime.flower.server;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerBuildRunSpec;
import it.smartcommunitylabdhub.runtime.flower.server.specs.FlowerServerRunSpec;
import it.smartcommunitylabdhub.runtime.flower.specs.FlowerRunStatus;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunLifecycleManager;

@RuntimeComponent(runtime = FlowerServerBuildRunSpec.KIND)
public class FlowerServerBuildLifecycleManager
    extends RunLifecycleManager<FlowerServerRunSpec, FlowerRunStatus, K8sRunnable> {

    FlowerServerBuildLifecycleManager(FlowerServerRuntime runtime) {
        super(runtime);
    }
}
