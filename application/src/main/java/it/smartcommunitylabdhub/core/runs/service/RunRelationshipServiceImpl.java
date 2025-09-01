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

package it.smartcommunitylabdhub.core.runs.service;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import it.smartcommunitylabdhub.relationships.BaseRelationshipsAwareEntityService;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipName;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@Transactional
@Slf4j
public class RunRelationshipServiceImpl extends BaseRelationshipsAwareEntityService<Run> {

    @Override
    public List<RelationshipDetail> getRelationships(String id) {
        log.debug("get relationships for run {}", String.valueOf(id));

        try {
            Run run = entityService.get(id);
            List<RelationshipDetail> list = super.getRelationships(id);

            //run is *always* related to function, check and inject if missing
            String taskPath = RunBaseSpec.with(run.getSpec()).getTask();
            if (StringUtils.hasText(taskPath)) {
                // Read spec and retrieve executables
                TaskSpecAccessor accessor = TaskSpecAccessor.with(run.getSpec());

                if (accessor.isValid()) {
                    //rebuild key and check
                    String fk = accessor.getWorkflowId() != null
                        ? KeyUtils.buildKey(
                            accessor.getProject(),
                            EntityName.WORKFLOW.getValue(),
                            accessor.getRuntime(),
                            accessor.getWorkflow(),
                            accessor.getWorkflowId()
                        )
                        : KeyUtils.buildKey(
                            accessor.getProject(),
                            EntityName.FUNCTION.getValue(),
                            accessor.getRuntime(),
                            accessor.getFunction(),
                            accessor.getFunctionId()
                        );

                    if (
                        list.stream().noneMatch(r -> r.getType() == RelationshipName.RUN_OF && fk.equals(r.getDest()))
                    ) {
                        //missing, let's add
                        RelationshipDetail fr = new RelationshipDetail(RelationshipName.RUN_OF, run.getKey(), fk);
                        list = Stream.concat(list.stream(), Stream.of(fr)).toList();
                    }
                }
            }

            return list;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
