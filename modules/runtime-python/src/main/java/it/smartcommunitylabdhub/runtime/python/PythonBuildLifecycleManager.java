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

package it.smartcommunitylabdhub.runtime.python;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RuntimeComponent;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.runtime.python.specs.PythonBuildRunSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonRunSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonRunStatus;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunLifecycleManager;

@RuntimeComponent(runtime = PythonBuildRunSpec.KIND)
public class PythonBuildLifecycleManager extends RunLifecycleManager<PythonRunSpec, PythonRunStatus, K8sRunnable> {

    PythonBuildLifecycleManager(PythonRuntime runtime) {
        super(runtime);
    }
}
