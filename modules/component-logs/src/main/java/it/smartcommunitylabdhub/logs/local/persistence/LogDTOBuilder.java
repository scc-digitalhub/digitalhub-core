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

package it.smartcommunitylabdhub.logs.local.persistence;

import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.logs.Log;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class LogDTOBuilder implements Converter<LogEntity, Log> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    private final AttributeConverter<String, byte[]> stringConverter;

    public LogDTOBuilder(
        @Qualifier("cborMapConverter") AttributeConverter<Map<String, Serializable>, byte[]> cborConverter,
        @Qualifier("cborStringConverter") AttributeConverter<String, byte[]> stringConverter
    ) {
        this.converter = cborConverter;
        this.stringConverter = stringConverter;
    }

    @Override
    public Log convert(@NonNull LogEntity entity) {
        //read metadata as-is
        Map<String, Serializable> meta = converter.convertToEntityAttribute(entity.getMetadata());

        BaseMetadata basemeta = BaseMetadata.from(meta);

        //inflate with values from entity
        basemeta.setProject(entity.getProject());

        basemeta.setCreated(
            entity.getCreated() != null
                ? OffsetDateTime.ofInstant(entity.getCreated().toInstant(), ZoneOffset.UTC)
                : null
        );
        basemeta.setUpdated(
            entity.getUpdated() != null
                ? OffsetDateTime.ofInstant(entity.getUpdated().toInstant(), ZoneOffset.UTC)
                : null
        );

        // merge metadata
        Map<String, Serializable> metadata = new HashMap<>();
        metadata.putAll(meta);
        metadata.putAll(basemeta.toMap());

        return Log.builder()
            .id(entity.getId())
            .project(entity.getProject())
            .user(entity.getCreatedBy())
            .run(entity.getRun())
            .metadata(metadata)
            .content(stringConverter.convertToEntityAttribute(entity.getContent()))
            .extensions(converter.convertToEntityAttribute(entity.getStatus()))
            .build();
    }
}
