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

package it.smartcommunitylabdhub.core.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.persistence.MetadataEntity;
import it.smartcommunitylabdhub.core.persistence.SpecEntity;
import it.smartcommunitylabdhub.core.persistence.StatusEntity;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

public class BaseEntityBuilder<D extends BaseDTO, E extends BaseEntity> implements Converter<D, E> {

    protected static final ObjectMapper mapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;
    protected final Class<E> clazz;
    protected final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    @SuppressWarnings("unchecked")
    protected BaseEntityBuilder(AttributeConverter<Map<String, Serializable>, byte[]> cborConverter) {
        this.converter = cborConverter;

        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.clazz = (Class<E>) t;
    }

    public BaseEntityBuilder(Class<E> type, AttributeConverter<Map<String, Serializable>, byte[]> cborConverter) {
        this.converter = cborConverter;
        this.clazz = type;
    }

    @Override
    public E convert(@NonNull D dto) {
        //base entity should match dto, let's convert it via map
        Map<String, Serializable> map = Map.of(
            "id",
            dto.getId(),
            "name",
            dto.getName(),
            "kind",
            dto.getKind(),
            "project",
            dto.getProject()
        );

        E entity = mapper.convertValue(map, clazz);

        //metadata is optional
        if (dto instanceof MetadataDTO metadataDto && entity instanceof MetadataEntity metadataEntity) {
            BaseMetadata metadata = BaseMetadata.from(metadataDto.getMetadata());
            metadataEntity.setMetadata(converter.convertToDatabaseColumn(metadataDto.getMetadata()));

            if (entity instanceof AbstractEntity abstractEntity) {
                abstractEntity.setCreated(
                    metadata.getCreated() != null
                        ? Date.from(metadata.getCreated().atZoneSameInstant(ZoneOffset.UTC).toInstant())
                        : null
                );
                abstractEntity.setUpdated(
                    metadata.getUpdated() != null
                        ? Date.from(metadata.getUpdated().atZoneSameInstant(ZoneOffset.UTC).toInstant())
                        : null
                );
            }
        }

        //spec is optional
        if (dto instanceof SpecDTO specDto && entity instanceof SpecEntity specEntity) {
            specEntity.setSpec(converter.convertToDatabaseColumn(specDto.getSpec()));
        }

        //status is optional
        if (dto instanceof StatusDTO statusDto && entity instanceof StatusEntity statusEntity) {
            StatusFieldAccessor statusFieldAccessor = StatusFieldAccessor.with(statusDto.getStatus());
            statusEntity.setStatus(converter.convertToDatabaseColumn(statusDto.getStatus()));
            statusEntity.setState(
                // Store status if not present
                statusFieldAccessor.getState() == null ? "CREATED" : statusFieldAccessor.getState()
            );
        }

        return entity;
    }
}
