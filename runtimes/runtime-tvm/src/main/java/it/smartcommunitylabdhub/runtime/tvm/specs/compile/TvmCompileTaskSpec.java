/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.compile;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import java.io.Serializable;
import java.util.Map;

@SpecType(
    runtime = TvmRuntime.RUNTIME,
    kind = TvmCompileTaskSpec.KIND,
    entity = Task.class,
    uiSchema = "runtime-tvm/tvm-compile/uiSchema.json"
)
public class TvmCompileTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+compile";

    public static TvmCompileTaskSpec with(Map<String, Serializable> data) {
        TvmCompileTaskSpec spec = new TvmCompileTaskSpec();
        spec.configure(data);
        return spec;
    }
}
