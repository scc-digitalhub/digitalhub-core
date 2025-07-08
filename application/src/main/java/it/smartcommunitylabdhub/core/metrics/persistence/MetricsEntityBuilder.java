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

package it.smartcommunitylabdhub.core.metrics.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.metrics.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MetricsEntityBuilder implements Converter<Metrics, MetricsEntity> {

    private final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;

    public MetricsEntity build(Metrics dto) {
        byte[] data = null;
        try {
            if (dto.getData() != null) {
                data = mapper.writeValueAsBytes(dto.getData());
            }
        } catch (JsonProcessingException e) {
            log.error("MetricsEntity build error: {}", e.getMessage());
        }

        return MetricsEntity
            .builder()
            .id(dto.getId())
            .entityId(dto.getEntityId())
            .entityName(dto.getEntityName())
            .name(dto.getName())
            .data(data)
            .build();
    }

    @Override
    public MetricsEntity convert(Metrics source) {
        return build(source);
    }
}
