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

package it.smartcommunitylabdhub.commons.models.objects;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.lang.Nullable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SourceCode<T extends Enum<?>> implements Serializable {

    @Nullable
    @Schema(title = "fields.sourceCode.source.title", description = "fields.sourceCode.source.description")
    private String source;

    @Nullable
    @Schema(title = "fields.sourceCode.handler.title", description = "fields.sourceCode.handler.description")
    private String handler;

    @Schema(title = "fields.sourceCode.base64.title", description = "fields.sourceCode.base64.description")
    private String base64;

    @Schema(title = "fields.sourceCode.lang.title", description = "fields.sourceCode.lang.description")
    private T lang;
}
