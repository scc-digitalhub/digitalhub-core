/*
 * Copyright 2026 the original author or authors.
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
 */

package it.smartcommunitylabdhub.security.listeners;

import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.projects.persistence.ProjectEntity;
import it.smartcommunitylabdhub.security.service.SecurityProjectProvisioner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class ProjectSecurityProvisioningListener {

    private final SecurityProjectProvisioner provisioner;

    public ProjectSecurityProvisioningListener(SecurityProjectProvisioner provisioner) {
        this.provisioner = provisioner;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void receive(EntityEvent<ProjectEntity> event) throws Exception {
        if (event.getAction() != EntityAction.CREATE || event.getEntity() == null) {
            return;
        }

        ProjectEntity project = event.getEntity();
        try {
            provisioner.provision(project.getId(), project.getCreatedBy());
        } catch (Exception e) {
            log.error("Failed to provision security analyzers for project {}", project.getId(), e);
            throw e;
        }
    }
}
