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
import it.smartcommunitylabdhub.commons.models.metrics.Metrics;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MetricsDTOBuilder implements Converter<MetricsEntity, Metrics> {

    private static final TypeReference<NumberOrNumberArray> typeRef = new TypeReference<>() {};

    private static final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;

    public Metrics build(MetricsEntity entity) {
        NumberOrNumberArray data = null;
        try {
            if ((entity.getData() != null) && entity.getData().length > 0) {
                data = mapper.readValue(entity.getData(), typeRef);
            }
        } catch (IOException e) {
            log.error("Metrics build error: {}", e.getMessage());
        }

        return Metrics
            .builder()
            .id(entity.getId())
            .entityId(entity.getEntityId())
            .entityName(entity.getEntityName())
            .name(entity.getName())
            .data(data)
            .build();
    }

    @Override
    public Metrics convert(MetricsEntity source) {
        return build(source);
    }
}
