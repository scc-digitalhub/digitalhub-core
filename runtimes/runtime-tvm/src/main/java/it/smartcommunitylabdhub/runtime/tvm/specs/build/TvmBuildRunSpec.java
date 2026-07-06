/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs.build;

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

// Run spec for the tvm+build task. Merges the parent function's spec and the
// build task spec into one flat document (both @JsonUnwrapped), which is what
// TvmRuntime.build() assembles and the build runner consumes.
@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = TvmRuntime.RUNTIME, kind = TvmBuildRunSpec.KIND, entity = Run.class)
public final class TvmBuildRunSpec extends TvmRunSpec {

    public static final String KIND = TvmBuildTaskSpec.KIND + ":run";

    @JsonSchemaIgnore
    @JsonUnwrapped
    private TvmFunctionSpec functionSpec;

    @JsonUnwrapped
    private TvmBuildTaskSpec taskBuildSpec;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        TvmBuildRunSpec spec = mapper.convertValue(data, TvmBuildRunSpec.class);
        this.functionSpec = spec.getFunctionSpec();
        this.taskBuildSpec = spec.getTaskBuildSpec();
    }

    public static TvmBuildRunSpec with(Map<String, Serializable> data) {
        TvmBuildRunSpec spec = new TvmBuildRunSpec();
        spec.configure(data);
        return spec;
    }
}
