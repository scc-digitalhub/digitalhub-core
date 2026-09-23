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

import it.smartcommunitylabdhub.commons.models.function.FunctionTaskBaseSpec;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.workflow.WorkflowTaskBaseSpec;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import jakarta.persistence.AttributeConverter;
import java.io.Serializable;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class TaskEntityBuilder implements Converter<Task, TaskEntity> {

    private final AttributeConverter<Map<String, Serializable>, byte[]> converter;

    public TaskEntityBuilder(AttributeConverter<Map<String, Serializable>, byte[]> cborConverter) {
        this.converter = cborConverter;
    }

    @Override
    public TaskEntity convert(@NonNull Task dto) {
        //build key
        String key = KeyUtils.buildKey(
            dto.getProject(),
            EntityUtils.getEntityName(Task.class).toLowerCase(),
            dto.getKind(),
            dto.getName(),
            dto.getId()
        );

        return TaskEntity.builder()
            .id(dto.getId())
            .kind(dto.getKind())
            .project(dto.getProject())
            .key(key)
            .spec(converter.convertToDatabaseColumn(dto.getSpec()))
            //extract refs from specs
            .function(FunctionTaskBaseSpec.from(dto.getSpec()).getFunction())
            .workflow(WorkflowTaskBaseSpec.from(dto.getSpec()).getWorkflow())
            .build();
    }
}
