/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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

package it.smartcommunitylabdhub.commons.utils;

import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.log.Log;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.secret.Secret;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import java.util.HashMap;
import java.util.Map;

@Deprecated(forRemoval = true)
public class EntityUtils {

    public static final Map<Class<? extends BaseDTO>, EntityName> ENTITY_NAME_MAP = new HashMap<>();

    static {
        ENTITY_NAME_MAP.put(Project.class, EntityName.PROJECT);
        ENTITY_NAME_MAP.put(Workflow.class, EntityName.WORKFLOW);
        ENTITY_NAME_MAP.put(Function.class, EntityName.FUNCTION);
        ENTITY_NAME_MAP.put(Artifact.class, EntityName.ARTIFACT);
        ENTITY_NAME_MAP.put(DataItem.class, EntityName.DATAITEM);
        ENTITY_NAME_MAP.put(Model.class, EntityName.MODEL);

        ENTITY_NAME_MAP.put(Task.class, EntityName.TASK);
        ENTITY_NAME_MAP.put(Trigger.class, EntityName.TRIGGER);
        ENTITY_NAME_MAP.put(Run.class, EntityName.RUN);

        ENTITY_NAME_MAP.put(Log.class, EntityName.LOG);

        ENTITY_NAME_MAP.put(Secret.class, EntityName.SECRET);
    }

    private EntityUtils() {
        // Utility class, no instantiation
    }

    public static EntityName getEntityName(Class<? extends BaseDTO> clazz) {
        EntityName entityName = ENTITY_NAME_MAP.get(clazz);
        if (entityName == null) {
            throw new IllegalArgumentException("Unsupported entity class: " + clazz.getName());
        }
        return entityName;
    }
}
