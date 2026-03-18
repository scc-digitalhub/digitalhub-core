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

package it.smartcommunitylabdhub.extensions.persistence;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.extensions.model.Extension;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
public class ExtensionBuilder implements Converter<ExtensionEntity, Extension> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    public ExtensionBuilder(AttributeConverter<Map<String, Serializable>, byte[]> converter) {
        Assert.notNull(converter, "map converter is required");
        this.converter = converter;
    }

    @Override
    public Extension convert(@NonNull ExtensionEntity entity) {
        return Extension
            .builder()
            .id(entity.getId())
            .entity(entity.getEntity())
            .project(entity.getProject())
            .parent(entity.getParent())
            .name(entity.getName())
            .kind(entity.getKind())
            .spec(converter.convertToEntityAttribute(entity.getSpec()))
            .build();
    }

    public static Extension from(BaseDTO dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(dto.getKind()).append("://");
        sb.append(dto.getProject()).append("/");
        sb.append(dto.getName()).append(":").append(dto.getId());
        String parent = sb.toString();

        return Extension.builder().project(dto.getProject()).parent(parent).build();
    }
}
