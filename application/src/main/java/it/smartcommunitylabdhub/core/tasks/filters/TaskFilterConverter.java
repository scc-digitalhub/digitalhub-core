/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.core.tasks.filters;

import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilterConverter;
import it.smartcommunitylabdhub.core.tasks.persistence.TaskEntity;
import jakarta.validation.constraints.NotNull;
import org.springframework.stereotype.Component;

@Component
public class TaskFilterConverter extends AbstractEntityFilterConverter<Task, TaskEntity> {

    @Override
    protected String map(@NotNull String source) {
        //function is top level
        if ("function".equals(source)) {
            return "function";
        }

        //workflow is top level
        if ("workflow".equals(source)) {
            return "workflow";
        }

        //no name
        if ("name".equals(source)) {
            return "id";
        }

        return super.map(source);
    }
}
