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

package it.smartcommunitylabdhub.metrics.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class ResourceMetricsDTOBuilder implements Converter<ResourceMetricsEntity, ResourceMetrics> {

    private static final TypeReference<HashMap<String, ArrayList<ResourceMetrics.Metric>>> typeRef =
        new TypeReference<>() {};

    private static final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    public ResourceMetricsDTOBuilder(
        @Qualifier("cborMapConverter") AttributeConverter<Map<String, Serializable>, byte[]> cborConverter
    ) {
        this.converter = cborConverter;
    }

    @Override
    public ResourceMetrics convert(@NonNull ResourceMetricsEntity entity) {
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

        //metrics data
        Map<String, Serializable> data =
            entity.getData() != null ? converter.convertToEntityAttribute(entity.getData()) : Map.of();
        Map<String, List<ResourceMetrics.Metric>> metrics = null;
        try {
            Map<String, ArrayList<ResourceMetrics.Metric>> map = mapper.convertValue(data, typeRef);
            metrics = map.entrySet().stream().collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
        } catch (IllegalArgumentException e) {
            log.error("Metrics build error: {}", e.getMessage());
        }

        return ResourceMetrics.builder()
            .id(entity.getId())
            .project(entity.getProject())
            .user(entity.getCreatedBy())
            .run(entity.getRun())
            .metadata(metadata)
            .metrics(metrics)
            .build();
    }
}
