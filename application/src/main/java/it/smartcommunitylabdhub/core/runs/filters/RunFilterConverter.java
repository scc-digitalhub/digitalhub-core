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

package it.smartcommunitylabdhub.core.runs.filters;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.core.queries.filters.AbstractEntityFilterConverter;
import it.smartcommunitylabdhub.core.runs.persistence.RunEntity;
import jakarta.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RunFilterConverter extends AbstractEntityFilterConverter<Run, RunEntity> {

    private static final String[] FIELDS = { Fields.TASK, Fields.FUNCTION, Fields.WORKFLOW };

    private static final List<String> fields;

    static {
        fields = Arrays.asList(FIELDS);
    }

    @Override
    protected String map(@NotNull String source) {
        if (fields.contains(source)) {
            return source;
        }

        return super.map(source);
    }
}
