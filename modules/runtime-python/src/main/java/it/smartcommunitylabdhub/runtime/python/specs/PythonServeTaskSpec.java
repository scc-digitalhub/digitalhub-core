/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.runtime.python.specs;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SpecType(runtime = PythonRuntime.RUNTIME, kind = PythonServeTaskSpec.KIND, entity = EntityName.TASK)
public class PythonServeTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "python+serve";

    @JsonProperty("replicas")
    @Min(0)
    private Integer replicas;

    @JsonProperty(value = "service_type", defaultValue = "ClusterIP")
    @Schema(defaultValue = "ClusterIP")
    private CoreServiceType serviceType;

    @JsonProperty("service_name")
    private String serviceName;

    public PythonServeTaskSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        PythonServeTaskSpec spec = mapper.convertValue(data, PythonServeTaskSpec.class);

        this.replicas = spec.getReplicas();
        this.setServiceType(spec.getServiceType());
        this.setServiceName(spec.getServiceName());
    }
}
