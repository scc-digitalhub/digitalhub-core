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

package it.smartcommunitylabdhub.core.repositories.converters.types;

import it.smartcommunitylabdhub.commons.models.enums.RelationshipName;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class RelationshipStringAttributeConverter implements AttributeConverter<RelationshipName, String> {

    @Override
    public String convertToDatabaseColumn(RelationshipName attribute) {
        if (attribute == null) {
            return null;
        }

        return attribute.name();
    }

    @Override
    public RelationshipName convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }

        //enum is uppercase
        return RelationshipName.valueOf(dbData);
    }
}
