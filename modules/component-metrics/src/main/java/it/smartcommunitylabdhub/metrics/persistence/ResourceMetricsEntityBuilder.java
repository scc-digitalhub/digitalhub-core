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

import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class ResourceMetricsEntityBuilder implements Converter<ResourceMetrics, ResourceMetricsEntity> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    public ResourceMetricsEntityBuilder(
        @Qualifier("cborMapConverter") AttributeConverter<Map<String, Serializable>, byte[]> cborConverter
    ) {
        this.converter = cborConverter;
    }

    @Override
    public ResourceMetricsEntity convert(@NonNull ResourceMetrics dto) {
        Map<String, Serializable> data =
            dto.getMetrics() == null
                ? null
                : dto
                      .getMetrics()
                      .entrySet()
                      .stream()
                      .collect(Collectors.toMap(e -> e.getKey(), e -> new ArrayList<>(e.getValue())));

        return ResourceMetricsEntity.builder()
            .id(dto.getId())
            .project(dto.getProject())
            .run(dto.getRun())
            .metadata(converter.convertToDatabaseColumn(dto.getMetadata()))
            .data(converter.convertToDatabaseColumn(data))
            .build();
    }
}
