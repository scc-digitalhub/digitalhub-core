/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.core.dataitems.specs;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.commons.models.status.Status;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "baseBuilder")
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DataItemBaseStatus extends BaseSpec implements Status {

    private String state;
    private String message;

    public DataItemBaseStatus(String state) {
        this.state = state;
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        DataItemBaseStatus spec = mapper.convertValue(data, DataItemBaseStatus.class);

        this.state = spec.getState();
        this.message = spec.getMessage();
    }

    public static DataItemBaseStatus with(Map<String, Serializable> data) {
        DataItemBaseStatus spec = new DataItemBaseStatus();
        spec.configure(data);

        return spec;
    }
}
