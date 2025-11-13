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

package it.smartcommunitylabdhub.runtime.container.deploy;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.runtime.container.ContainerRuntime;
import jakarta.validation.constraints.Min;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = ContainerRuntime.RUNTIME, kind = ContainerDeployTaskSpec.KIND, entity = EntityName.TASK)
public class ContainerDeployTaskSpec extends K8sFunctionTaskBaseSpec {

    public static final String KIND = "container+deploy";

    @Min(1)
    private Integer replicas;

    @JsonProperty("fs_group")
    @Min(1)
    private Integer fsGroup;

    @JsonProperty("run_as_user")
    @Min(1)
    private Integer runAsUser;

    @JsonProperty("run_as_group")
    @Min(1)
    private Integer runAsGroup;

    public ContainerDeployTaskSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        ContainerDeployTaskSpec spec = mapper.convertValue(data, ContainerDeployTaskSpec.class);
        this.replicas = spec.getReplicas();
        this.fsGroup = spec.getFsGroup();
        this.runAsUser = spec.getRunAsUser();
        this.runAsGroup = spec.getRunAsGroup();
    }
}
