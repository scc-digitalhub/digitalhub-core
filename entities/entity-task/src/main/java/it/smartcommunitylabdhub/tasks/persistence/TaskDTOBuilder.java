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

package it.smartcommunitylabdhub.tasks.persistence;

import it.smartcommunitylabdhub.commons.models.task.Task;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class TaskDTOBuilder implements Converter<TaskEntity, Task> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    public TaskDTOBuilder(AttributeConverter<Map<String, Serializable>, byte[]> cborConverter) {
        this.converter = cborConverter;
    }

    public Task build(TaskEntity entity) {
        return Task
            .builder()
            .id(entity.getId())
            .kind(entity.getKind())
            .project(entity.getProject())
            .user(entity.getCreatedBy())
            .spec(converter.convertToEntityAttribute(entity.getSpec()))
            .build();
    }

    @Override
    public Task convert(TaskEntity source) {
        return build(source);
    }
}
