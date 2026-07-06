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

// PyTorch frontend. User file must expose MODEL+EXAMPLE_INPUTS or
// get_model() -> (nn.Module, example_inputs). Builder runs torch.export then
// from_exported_program. Requires torch in the builder image (tvm-toolkit
// default does not include it, ~+2GB).
@Component
public class PytorchFrontend extends TvmBuildFrontendRunner implements TvmFrontend {

    public static final String FORMAT = "pytorch";

    public PytorchFrontend(TvmProperties properties, K8sBuilderHelper k8sBuilderHelper) {
        super(properties, k8sBuilderHelper);
    }

    @Override
    public String getFormat() {
        return FORMAT;
    }

    @Override
    public boolean canHandle(String path, String format) {
        // no .py auto-detect (could be tvmscript): user must specify format
        return FORMAT.equalsIgnoreCase(format);
    }

    @Override
    public K8sJobRunnable produce(
            Run run,
            TvmFunctionSpec functionSpec,
            TvmBuildTaskSpec taskSpec,
            Map<String, String> secretData) {
        List<CoreEnv> envs = new ArrayList<>();
        if (taskSpec.getKeepParamsInInput() != null) {
            envs.add(new CoreEnv("TVM_KEEP_PARAMS_IN_INPUT", String.valueOf(taskSpec.getKeepParamsInInput())));
        }
        // from_exported_program flags (PyTorch only); left unset -> TVM defaults apply.
        if (taskSpec.getUnwrapUnitReturnTuple() != null) {
            envs.add(new CoreEnv("TVM_UNWRAP_UNIT_RETURN_TUPLE", String.valueOf(taskSpec.getUnwrapUnitReturnTuple())));
        }
        if (taskSpec.getNoBindReturnTuple() != null) {
            envs.add(new CoreEnv("TVM_NO_BIND_RETURN_TUPLE", String.valueOf(taskSpec.getNoBindReturnTuple())));
        }
        if (taskSpec.getRunEpDecomposition() != null) {
            envs.add(new CoreEnv("TVM_RUN_EP_DECOMPOSITION", String.valueOf(taskSpec.getRunEpDecomposition())));
        }

        return buildJobRunnable(run, functionSpec, taskSpec, secretData, FORMAT, "model.py", envs);
    }
}
