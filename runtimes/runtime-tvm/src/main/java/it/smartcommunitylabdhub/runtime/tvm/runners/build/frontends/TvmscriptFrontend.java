/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.build.frontends;

import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.build.TvmBuildFrontendRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.build.TvmFrontend;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import java.util.Map;
import org.springframework.stereotype.Component;

// TVMScript frontend: user .py exposing a `Module` decorated with @I.ir_module
// or `get_module() -> tvm.ir.IRModule`. Container has TVM only (no onnx/torch).
@Component
public class TvmscriptFrontend extends TvmBuildFrontendRunner implements TvmFrontend {

    public static final String FORMAT = "tvmscript";

    public TvmscriptFrontend(TvmProperties properties, K8sBuilderHelper k8sBuilderHelper) {
        super(properties, k8sBuilderHelper);
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }

    @Override
    public boolean canHandle(String path, String format) {
        // no .py auto-detect (could be pytorch): user must specify format
        return FORMAT.equalsIgnoreCase(format);
    }

    @Override
    public K8sJobRunnable produce(
        Run run,
        TvmFunctionSpec functionSpec,
        TvmBuildTaskSpec taskSpec,
        Map<String, String> secretData
    ) {
        // No format-specific env vars: TVMScript IR is self-contained.
        return buildJobRunnable(run, functionSpec, taskSpec, secretData, FORMAT, "model.py", null);
    }
}
