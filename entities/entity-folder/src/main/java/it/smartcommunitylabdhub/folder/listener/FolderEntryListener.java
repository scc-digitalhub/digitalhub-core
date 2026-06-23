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

package it.smartcommunitylabdhub.folder.listener;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.components.cloud.CloudEntityEvent;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.folder.Folder;
import it.smartcommunitylabdhub.folder.services.FolderEntriesService;
import it.smartcommunitylabdhub.folder.specs.FolderMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FolderEntryListener {

    @Autowired
    private FolderEntriesService entriesService;

    @Async
    @EventListener
    public void receive(CloudEntityEvent<? extends BaseDTO> event) {
        if (!(event.getDto() instanceof MetadataDTO) || event.getAction().equals(EntityAction.READ)) {
            return;
        }

        //skip folders and unsupported
        if (Folder.class.equals(event.getClazz()) || !entriesService.isSupported(event.getClazz())) {
            return;
        }

        log.debug("receive event for {}: {}", event.getClass(), event.getDto().getId());

        try {
            var dto = (MetadataDTO & BaseDTO) event.getDto();
            if (event.getAction().equals(EntityAction.DELETE)) {
                //remove all registrations for the deleted entity
                log.debug("delete entries for {}:{}", dto.getProject(), dto.getId());
                entriesService.deleteEntry(dto.getId());
            } else {
                //align folder entries via registration when needed
                FolderMetadata metadata = FolderMetadata.from(dto.getMetadata());

                if (log.isTraceEnabled()) {
                    log.trace("metadata for entry {}: {}", dto.getId(), metadata);
                }

                //update the registrations for the entity
                //NOTE: we always register even if parent is missing, this will put the entry at root level
                log.debug("register entries for {}:{}", dto.getProject(), dto.getId());
                entriesService.registerEntry(dto.getProject(), metadata.getFolderId(), dto);
            }
        } catch (StoreException e) {
            log.error("error when updating folder entries: {}", e.getMessage());
        }
    }
}
