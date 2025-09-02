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

package it.smartcommunitylabdhub.core.dataitems.specs;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = "table", entity = EntityName.DATAITEM)
public class DataItemTableSpec extends DataItemBaseSpec {

    //TODO adopt tableschema
    //see https://github.com/frictionlessdata/tableschema-java
    // private Map<String, Serializable> schema;
    private TableSchema schema;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        DataItemTableSpec spec = mapper.convertValue(data, DataItemTableSpec.class);

        this.schema = spec.getSchema();
    }
}
