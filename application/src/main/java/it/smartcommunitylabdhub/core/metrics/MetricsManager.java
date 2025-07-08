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

package it.smartcommunitylabdhub.core.metrics;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.metrics.Metrics;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.core.metrics.persistence.MetricsDTOBuilder;
import it.smartcommunitylabdhub.core.metrics.persistence.MetricsEntity;
import it.smartcommunitylabdhub.core.metrics.persistence.MetricsEntityBuilder;
import it.smartcommunitylabdhub.core.metrics.persistence.MetricsRepository;
import it.smartcommunitylabdhub.core.utils.UUIDKeyGenerator;
import jakarta.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class MetricsManager {

    @Value("${files.max-column-size}")
    private int maxColumnSize;

    @Autowired
    private MetricsEntityBuilder entityBuilder;

    @Autowired
    private MetricsDTOBuilder dtoBuilder;

    @Autowired
    private MetricsRepository repository;

    private StringKeyGenerator keyGenerator = new UUIDKeyGenerator();

    @Autowired(required = false)
    public void setKeyGenerator(StringKeyGenerator keyGenerator) {
        Assert.notNull(keyGenerator, "key generator can not be null");
        this.keyGenerator = keyGenerator;
    }

    public NumberOrNumberArray getMetrics(@NotNull String entityName, @NotNull String entityId, @NotNull String name)
        throws StoreException, SystemException {
        log.debug("get {} metrics info for entity {} id {}", name, entityName, entityId);
        MetricsEntity entity = repository.findByEntityNameAndEntityIdAndName(entityName, entityId, name);
        if (entity != null) {
            Metrics dto = dtoBuilder.convert(entity);
            return dto.getData();
        }
        return null;
    }

    public Map<String, NumberOrNumberArray> getMetrics(@NotNull String entityName, @NotNull String entityId)
        throws StoreException, SystemException {
        log.debug("get metrics info for entity {} id {}", entityName, entityId);
        List<MetricsEntity> list = repository.findByEntityNameAndEntityId(entityName, entityId);
        Map<String, NumberOrNumberArray> response = new HashMap<>();
        for (MetricsEntity entity : list) {
            Metrics dto = dtoBuilder.convert(entity);
            response.put(dto.getName(), dto.getData());
        }
        return response;
    }

    public Metrics saveMetrics(
        @NotNull String entityName,
        @NotNull String entityId,
        @NotNull String name,
        NumberOrNumberArray data
    ) throws StoreException, SystemException {
        log.debug("save {} metrics info for entity {} id {}", name, entityName, entityId);

        Metrics dto = Metrics.builder().entityId(entityId).entityName(entityName).name(name).data(data).build();

        MetricsEntity entity = repository.findByEntityNameAndEntityIdAndName(entityName, entityId, name);

        if (entity != null) {
            dto.setId(entity.getId());
        } else {
            dto.setId(keyGenerator.generateKey());
        }

        entity = entityBuilder.convert(dto);

        //check files size before persisting
        if (entity.getData() != null && entity.getData().length > maxColumnSize) {
            throw new IllegalArgumentException("files column exceeds maximum size " + String.valueOf(maxColumnSize));
        }

        entity = repository.save(entity);
        return dtoBuilder.build(entity);
    }
}
