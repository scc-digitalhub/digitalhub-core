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

package it.smartcommunitylabdhub.commons.models.metadata;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
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
@SpecType(kind = "metadata.base", entity = EntityName.METADATA)
public final class BaseMetadata extends BaseSpec implements Metadata {

    protected String project;

    protected String name;
    protected String description;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    protected OffsetDateTime created;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    protected OffsetDateTime updated;

    protected Set<String> labels;

    @Override
    public void configure(Map<String, Serializable> data) {
        BaseMetadata meta = mapper.convertValue(data, BaseMetadata.class);

        this.project = meta.getProject();

        this.name = meta.getName();
        this.description = meta.getDescription();

        this.created = meta.getCreated();
        this.updated = meta.getUpdated();

        this.labels = meta.getLabels();
    }

    public static BaseMetadata from(Map<String, Serializable> map) {
        BaseMetadata meta = new BaseMetadata();
        meta.configure(map);

        return meta;
    }
}
