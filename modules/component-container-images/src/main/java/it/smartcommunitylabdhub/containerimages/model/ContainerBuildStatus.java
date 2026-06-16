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

package it.smartcommunitylabdhub.containerimages.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ContainerBuildStatus extends RunBaseStatus {

    private Map<String, Serializable> outputs;
    private ContainerBuildInfo build;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        ContainerBuildStatus spec = mapper.convertValue(data, ContainerBuildStatus.class);
        this.build = spec.getBuild();
        this.outputs = spec.getOutputs();
    }

    public static ContainerBuildStatus with(Map<String, Serializable> data) {
        ContainerBuildStatus spec = new ContainerBuildStatus();
        spec.configure(data);

        return spec;
    }
}
