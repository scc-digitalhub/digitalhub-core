/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.tvm.status;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Status for the three TVM tasks: build/compile report the produced Model key; serve reports the K8s service.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TvmRunStatus extends RunBaseStatus {

    // Key of the Model produced by build (tvm-ir) or compile (tvm-so).
    @JsonProperty("model_key")
    private String modelKey;

    // Populated by tvm+serve only.
    private K8sServiceInfo service;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);
        TvmRunStatus s = mapper.convertValue(data, TvmRunStatus.class);
        this.modelKey = s.getModelKey();
        this.service = s.getService();
    }

    public static TvmRunStatus with(Map<String, Serializable> data) {
        TvmRunStatus s = new TvmRunStatus();
        s.configure(data);
        return s;
    }
}
