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
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.metadata.AuditMetadataBuilder;
import it.smartcommunitylabdhub.core.metadata.BaseMetadataBuilder;
import it.smartcommunitylabdhub.core.metadata.VersioningMetadataBuilder;
import it.smartcommunitylabdhub.core.persistence.BaseEntity;
import it.smartcommunitylabdhub.core.persistence.MetadataEntity;
import it.smartcommunitylabdhub.core.persistence.SpecEntity;
import it.smartcommunitylabdhub.core.persistence.StatusEntity;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;

public class BaseDTOBuilder<E extends BaseEntity, D extends BaseDTO> implements Converter<E, D> {

    protected static final ObjectMapper mapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;
    protected final Class<D> clazz;
    protected final AttributeConverter<Map<String, Serializable>, byte[]> converter;
    protected BaseMetadataBuilder baseMetadataBuilder;
    protected AuditMetadataBuilder auditingMetadataBuilder;
    protected VersioningMetadataBuilder versioningMetadataBuilder;

    @SuppressWarnings("unchecked")
    protected BaseDTOBuilder(AttributeConverter<Map<String, Serializable>, byte[]> cborConverter) {
        this.converter = cborConverter;

        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[1];
        this.clazz = (Class<D>) t;
    }

    public BaseDTOBuilder(Class<D> clazz, AttributeConverter<Map<String, Serializable>, byte[]> cborConverter) {
        this.clazz = clazz;
        this.converter = cborConverter;
    }

    @Autowired
    public void setBaseMetadataBuilder(BaseMetadataBuilder baseMetadataBuilder) {
        this.baseMetadataBuilder = baseMetadataBuilder;
    }

    @Autowired
    public void setAuditingMetadataBuilder(AuditMetadataBuilder auditingMetadataBuilder) {
        this.auditingMetadataBuilder = auditingMetadataBuilder;
    }

    @Autowired
    public void setVersioningMetadataBuilder(VersioningMetadataBuilder versioningMetadataBuilder) {
        this.versioningMetadataBuilder = versioningMetadataBuilder;
    }

    @Override
    public D convert(@NonNull E entity) {
        //base dto should match entity, let's convert it via map
        Map<String, Serializable> map = Map.of(
            "id",
            entity.getId(),
            "name",
            entity.getName(),
            "kind",
            entity.getKind(),
            "project",
            entity.getProject(),
            "user",
            entity.getCreatedBy()
        );

        D dto = mapper.convertValue(map, clazz);

        //metadata is optional
        if (dto instanceof MetadataDTO metadataDto && entity instanceof MetadataEntity metadataEntity) {
            //read metadata map as-is
            Map<String, Serializable> meta = converter.convertToEntityAttribute(metadataEntity.getMetadata());

            // build metadata
            Map<String, Serializable> metadata = new HashMap<>();
            metadata.putAll(meta);

            Optional.ofNullable(baseMetadataBuilder.convert(entity)).ifPresent(m -> metadata.putAll(m.toMap()));
            Optional.ofNullable(auditingMetadataBuilder.convert(entity)).ifPresent(m -> metadata.putAll(m.toMap()));
            Optional.ofNullable(versioningMetadataBuilder.convert(entity)).ifPresent(m -> metadata.putAll(m.toMap()));

            metadataDto.setMetadata(metadata);
        }

        //spec is optional
        if (dto instanceof SpecDTO specDto && entity instanceof SpecEntity specEntity) {
            specDto.setSpec(converter.convertToEntityAttribute(specEntity.getSpec()));
        }

        //status is optional
        if (dto instanceof StatusDTO statusDto && entity instanceof StatusEntity statusEntity) {
            Map<String, Serializable> status = MapUtils.mergeMultipleMaps(
                converter.convertToEntityAttribute(statusEntity.getStatus()),
                Map.of("state", statusEntity.getState())
            );
            statusDto.setStatus(status);
        }

        return dto;
    }
}
