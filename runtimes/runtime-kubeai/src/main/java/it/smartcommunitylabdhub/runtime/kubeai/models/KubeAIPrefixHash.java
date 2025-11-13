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

package it.smartcommunitylabdhub.runtime.kubeai.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KubeAIPrefixHash {

    @Schema(title = "fields.kubeai.meanloadfactor.title", description = "fields.kubeai.meanloadfactor.description")
    @Builder.Default
    @JsonProperty("mean_load_factor")
    private Integer meanLoadFactor = 125;

    @Schema(title = "fields.kubeai.replication.title", description = "fields.kubeai.replication.description")
    @Builder.Default
    private Integer replication = 256;

    @Schema(title = "fields.kubeai.prefixcharlength.title", description = "fields.kubeai.prefixcharlength.description")
    @Builder.Default
    @JsonProperty("prefix_char_length")
    private Integer prefixCharLength = 100;
}
