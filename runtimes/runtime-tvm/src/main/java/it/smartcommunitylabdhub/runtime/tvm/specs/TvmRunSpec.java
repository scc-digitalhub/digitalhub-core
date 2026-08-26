/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs;

import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TvmRunSpec extends RunBaseSpec {

    // Entity keys the run consumes, keyed by input name.
    private Map<String, String> inputs = new HashMap<>();

    public TvmRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TvmRunSpec spec = mapper.convertValue(data, TvmRunSpec.class);

        this.inputs = spec.getInputs();
    }
}
