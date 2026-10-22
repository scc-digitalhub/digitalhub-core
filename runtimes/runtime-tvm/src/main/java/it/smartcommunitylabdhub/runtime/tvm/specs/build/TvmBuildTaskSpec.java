/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.build;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import java.io.Serializable;
import java.util.Map;

@SpecType(
    runtime = TvmRuntime.RUNTIME,
    kind = TvmBuildTaskSpec.KIND,
    entity = Task.class,
    uiSchema = "runtime-tvm/tvm-build/uiSchema.json"
)
public class TvmBuildTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+build";

    public static TvmBuildTaskSpec with(Map<String, Serializable> data) {
        TvmBuildTaskSpec spec = new TvmBuildTaskSpec();
        spec.configure(data);
        return spec;
    }
}
