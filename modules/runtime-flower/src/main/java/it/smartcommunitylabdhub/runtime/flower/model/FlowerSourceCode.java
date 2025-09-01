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

package it.smartcommunitylabdhub.runtime.flower.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.Getter;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowerSourceCode implements Serializable {

    @Nullable
    @Schema(title = "fields.flowerSourceCode.source.title", description = "fields.flowerSourceCode.source.description")
    private String source;

    @Nullable
    @Schema(
        title = "fields.flowerSourceCode.clientapp.title",
        description = "fields.flowerSourceCode.clientapp.description"
    )
    private String clientapp;

    @Nullable
    @Schema(
        title = "fields.flowerSourceCode.serverapp.title",
        description = "fields.flowerSourceCode.serverapp.description"
    )
    private String serverapp;

    @Schema(
        title = "fields.flowerSourceCode.clientbase64.title",
        description = "fields.flowerSourceCode.clientbase64.description"
    )
    private String clientbase64;

    @Schema(
        title = "fields.flowerSourceCode.serverbase64.title",
        description = "fields.flowerSourceCode.serverbase64.description"
    )
    private String serverbase64;
}
