/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.build;

import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import java.util.Map;

// Strategy for tvm+build, one implementation per input format. To add a new
// format: implement this interface as a @Component and add a builders entry
// in runtime-tvm.yml.
public interface TvmFrontend {
    String getFormat();

    boolean canHandle(String path, String format);

    K8sJobRunnable produce(
            Run run,
            TvmFunctionSpec functionSpec,
            TvmBuildTaskSpec taskSpec,
            Map<String, String> secretData);
}
