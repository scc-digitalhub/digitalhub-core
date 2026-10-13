/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.compile;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.tvm.TvmRuntime;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmFunctionSpec;
import it.smartcommunitylabdhub.runtime.tvm.specs.TvmRunSpec;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(
    runtime = TvmRuntime.RUNTIME,
    kind = TvmCompileRunSpec.KIND,
    entity = Run.class,
    uiSchema = "runtime-tvm/tvm-compile-run/uiSchema.json"
)
public final class TvmCompileRunSpec extends TvmRunSpec {

    public static final String KIND = TvmCompileTaskSpec.KIND + ":run";

    @JsonIgnore
    private Map<String, Serializable> inactiveTuning = Map.of();

    @JsonSchemaIgnore
    @JsonUnwrapped
    private TvmFunctionSpec functionSpec;

    @JsonUnwrapped
    private TvmCompileTaskSpec taskCompileSpec;

    @Override
    public void configure(Map<String, Serializable> data) {
        Map<String, Serializable> active = TvmCompileOptions.active(data);
        this.inactiveTuning = TvmCompileOptions.inactive(data, active);
        data = active;
        super.configure(data);
        TvmCompileRunSpec spec = mapper.convertValue(data, TvmCompileRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskCompileSpec = spec.getTaskCompileSpec();
    }

    @Override
    public Map<String, Serializable> toMap() {
        return TvmCompileOptions.stored(super.toMap(), inactiveTuning);
    }

    public static TvmCompileRunSpec with(Map<String, Serializable> data) {
        TvmCompileRunSpec spec = new TvmCompileRunSpec();
        spec.configure(data);
        return spec;
    }
}
