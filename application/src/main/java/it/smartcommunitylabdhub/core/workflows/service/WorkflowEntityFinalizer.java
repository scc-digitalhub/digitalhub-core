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

package it.smartcommunitylabdhub.core.workflows.service;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.commons.models.workflow.Workflow;
import it.smartcommunitylabdhub.commons.services.TaskService;
import it.smartcommunitylabdhub.core.persistence.AbstractEntity_;
import it.smartcommunitylabdhub.core.queries.specifications.CommonSpecification;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.core.services.EntityFinalizer;
import it.smartcommunitylabdhub.core.tasks.persistence.TaskEntity;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WorkflowEntityFinalizer implements EntityFinalizer<Workflow>, InitializingBean {

    //TODO replace with finalizer!
    @Autowired
    private TaskService taskService;

    @Autowired
    private SearchableEntityRepository<TaskEntity, Task> taskEntityService;

    @Override
    public void afterPropertiesSet() throws Exception {}

    @Override
    public void finalize(@NotNull Workflow workflow) throws StoreException {
        log.debug("finalize workflow with id {}", workflow.getId());

        try {
            //delete tasks
            //define a spec for tasks building workflow path
            String path =
                (workflow.getKind() +
                    "://" +
                    workflow.getProject() +
                    "/" +
                    workflow.getName() +
                    ":" +
                    workflow.getId());

            Specification<TaskEntity> where = Specification.allOf(
                CommonSpecification.projectEquals(workflow.getProject()),
                createWorkflowSpecification(path)
            );

            //fetch all tasks ordered by kind ASC
            Specification<TaskEntity> specification = (root, query, builder) -> {
                query.orderBy(builder.asc(root.get(AbstractEntity_.KIND)));
                return where.toPredicate(root, query, builder);
            };

            List<Task> tasks = taskEntityService.searchAll(specification);

            //delete all with cascade
            tasks.forEach(task -> {
                taskService.deleteTask(task.getId(), true);
            });
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    private Specification<TaskEntity> createWorkflowSpecification(String workflow) {
        return (root, query, criteriaBuilder) -> {
            return criteriaBuilder.equal(root.get(Fields.WORKFLOW), workflow);
        };
    }
}
