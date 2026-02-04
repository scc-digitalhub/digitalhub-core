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

package it.smartcommunitylabdhub.extensions.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class ExtensionSchema implements Schema, Serializable {

    private static final String ENTITY = EntityUtils.getEntityName(Extension.class);
    private final String kind;

    @JsonIgnore
    private final transient JsonNode schema;

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public String runtime() {
        return null;
    }

    @Override
    public String entity() {
        return ENTITY;
    }

    @Override
    public JsonNode schema() {
        return schema;
    }
}
