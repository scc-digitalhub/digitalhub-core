/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.build;

import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.models.ModelManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.runners.TvmRunnerHelper;
import it.smartcommunitylabdhub.runtime.tvm.specs.model.TvmFormat;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildRunSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

// Dispatches tvm+build to the right TvmFrontend by explicit format or path
// extension auto-detect.
public class TvmBuildRunner {

    private final Map<String, TvmFrontend> byFormat;
    private final List<TvmFrontend> all;
    private final ModelManager modelService;

    public TvmBuildRunner(List<TvmFrontend> frontends, ModelManager modelService) {
        this.all = frontends;
        this.byFormat = frontends.stream().collect(Collectors.toMap(TvmFrontend::getFormat, Function.identity()));
        this.modelService = modelService;
    }

    public K8sJobRunnable produce(Run run, Map<String, String> secretData) {
        TvmBuildRunSpec runSpec = TvmBuildRunSpec.with(run.getSpec());
        TvmFunctionSpec functionSpec = runSpec.getFunctionSpec();
        TvmBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();

        // Resolve a store:// model key to the Model's concrete spec.path (s3://) before
        // dispatching to a frontend: the builder init container has no 'store' protocol,
        // and format auto-detect needs the source file extension. Direct s3:// / http(s)://
        // paths pass through unchanged.
        functionSpec.setModel(
            TvmRunnerHelper.resolveModelPath(functionSpec.getModel(), modelService)
        );

        String format = resolveFormat(functionSpec);
        TvmFrontend frontend = byFormat.get(format);
        if (frontend == null) {
            throw new IllegalArgumentException(
                "no TVM frontend registered for format: " + format + " (available: " + byFormat.keySet() + ")"
            );
        }
        return frontend.produce(run, functionSpec, taskSpec, secretData);
    }

    private String resolveFormat(TvmFunctionSpec functionSpec) {
        TvmFormat fmt = functionSpec.getFormat();
        if (fmt != null && fmt != TvmFormat.auto) {
            return fmt.name();
        }
        return all
            .stream()
            .filter(f -> f.canHandle(functionSpec.getModel(), null))
            .map(TvmFrontend::getFormat)
            .findFirst()
            .orElseThrow(() ->
                new IllegalStateException(
                    "cannot auto-detect TVM source format for path: " +
                    functionSpec.getModel() +
                    " — set spec.format explicitly (onnx) when the source has " +
                    "no recognizable extension (e.g. a store:// or folder path)"
                )
            );
    }
}
