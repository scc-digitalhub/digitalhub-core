/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.runners.build.frontends;

import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.config.TvmProperties;
import it.smartcommunitylabdhub.runtime.tvm.runners.build.TvmBuildFrontendRunner;
import it.smartcommunitylabdhub.runtime.tvm.runners.build.TvmFrontend;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.build.TvmBuildTaskSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

// ONNX frontend: produces model.relax.ir + metadata.json [+ params.bin]. All
// from_onnx params and ONNX preprocessing flags are forwarded as env vars to
// builder_onnx.py.
@Component
public class OnnxFrontend extends TvmBuildFrontendRunner implements TvmFrontend {

    public static final String FORMAT = "onnx";

    public OnnxFrontend(TvmProperties properties, K8sBuilderHelper k8sBuilderHelper) {
        super(properties, k8sBuilderHelper);
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }

    @Override
    public boolean canHandle(String path, String format) {
        if (FORMAT.equalsIgnoreCase(format))
            return true;
        return path != null && path.toLowerCase().endsWith(".onnx");
    }

    @Override
    public K8sJobRunnable produce(
            Run run,
            TvmFunctionSpec functionSpec,
            TvmBuildTaskSpec taskSpec,
            Map<String, String> secretData) {
        List<CoreEnv> envs = new ArrayList<>();
        // from_onnx() conversion flags
        if (taskSpec.getKeepParamsInInput() != null) {
            envs.add(new CoreEnv("TVM_KEEP_PARAMS_IN_INPUT", String.valueOf(taskSpec.getKeepParamsInInput())));
        }
        if (taskSpec.getSanitizeInputNames() != null) {
            envs.add(new CoreEnv("TVM_SANITIZE_INPUT_NAMES", String.valueOf(taskSpec.getSanitizeInputNames())));
        }

        // ONNX preprocessing applied before conversion (simplify + shape inference)
        if (Boolean.TRUE.equals(taskSpec.getSimplify())) {
            envs.add(new CoreEnv("TVM_SIMPLIFY", "true"));
        }
        if (taskSpec.getTargetOpset() != null) {
            envs.add(new CoreEnv("TVM_TARGET_OPSET", String.valueOf(taskSpec.getTargetOpset())));
        }
        if (taskSpec.getOpsetOverride() != null) {
            envs.add(new CoreEnv("TVM_OPSET_OVERRIDE", String.valueOf(taskSpec.getOpsetOverride())));
        }
        if (Boolean.TRUE.equals(taskSpec.getStrictShapeInference())) {
            envs.add(new CoreEnv("TVM_STRICT_SHAPE_INFER", "true"));
        }
        if (Boolean.TRUE.equals(taskSpec.getDataProp())) {
            envs.add(new CoreEnv("TVM_DATA_PROP", "true"));
        }

        return buildJobRunnable(run, functionSpec, taskSpec, secretData, FORMAT, "model.onnx", envs);
    }
}
