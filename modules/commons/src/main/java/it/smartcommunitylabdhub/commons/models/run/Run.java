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

package it.smartcommunitylabdhub.commons.models.run;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.lang.Nullable;
import org.springframework.util.StringUtils;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@ToString
@JsonPropertyOrder(alphabetic = true)
public class Run implements BaseDTO, MetadataDTO, SpecDTO, StatusDTO {

    @Nullable
    @Pattern(regexp = Keys.SLUG_PATTERN)
    private String id;

    @Nullable
    @Pattern(regexp = Keys.SLUG_PATTERN)
    private String name;

    @NotNull
    @Pattern(regexp = Keys.SLUG_PATTERN)
    private String project;

    private String user;

    @NotNull
    @Pattern(regexp = Keys.KIND_PATTERN)
    private String kind;

    @Builder.Default
    private Map<String, Serializable> metadata = new HashMap<>();

    @Builder.Default
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Map<String, Serializable> spec = new HashMap<>();

    @Builder.Default
    private Map<String, Serializable> status = new HashMap<>();

    @Override
    public String getName() {
        return StringUtils.hasText(name) ? name : id;
    }

    @Override
    public String getKey() {
        return (
            Keys.STORE_PREFIX +
            getProject() +
            Keys.PATH_DIVIDER +
            EntityName.RUN.getValue() +
            Keys.PATH_DIVIDER +
            getKind() +
            Keys.PATH_DIVIDER +
            getId() +
            Keys.ID_DIVIDER +
            getId()
        );
    }
}
