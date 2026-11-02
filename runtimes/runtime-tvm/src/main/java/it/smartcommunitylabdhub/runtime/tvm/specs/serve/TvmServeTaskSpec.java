/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.serve;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import java.io.Serializable;
import java.util.Map;

@SpecType(
    runtime = TvmRuntime.RUNTIME,
    kind = TvmServeTaskSpec.KIND,
    entity = Task.class,
    uiSchema = "runtime-tvm/tvm-serve/uiSchema.json"
)
public class TvmServeTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "tvm+serve";

    public static TvmServeTaskSpec with(Map<String, Serializable> data) {
        TvmServeTaskSpec spec = new TvmServeTaskSpec();
        spec.configure(data);
        return spec;
    }
}
