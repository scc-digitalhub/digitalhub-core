/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.specs;

import it.smartcommunitylabdhub.runs.specs.RunBaseSpec;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
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

    protected static Map<String, Serializable> normalizeModelPath(Map<String, Serializable> data, String field) {
        if (!data.containsKey("model_path")) {
            return data;
        }
        Map<String, Serializable> normalized = new LinkedHashMap<>(data);
        Serializable previous = normalized.remove("model_path");
        if (normalized.containsKey(field) && !Objects.equals(normalized.get(field), previous)) {
            throw new IllegalArgumentException("Conflicting values for model_path and " + field);
        }
        normalized.put(field, previous);
        return normalized;
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TvmRunSpec spec = mapper.convertValue(data, TvmRunSpec.class);

        this.inputs = spec.getInputs();
    }
}
