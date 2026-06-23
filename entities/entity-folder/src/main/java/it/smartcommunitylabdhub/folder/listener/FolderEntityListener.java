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
import it.smartcommunitylabdhub.core.events.AbstractEntityListener;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.folder.Folder;
import it.smartcommunitylabdhub.folder.persistence.FolderEntity;
import it.smartcommunitylabdhub.folder.services.FolderEntriesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@Slf4j
public class FolderEntityListener extends AbstractEntityListener<FolderEntity, Folder> {

    @Autowired
    private FolderEntriesService entriesService;

    public FolderEntityListener(Converter<FolderEntity, Folder> converter) {
        super(converter);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void receive(EntityEvent<FolderEntity> event) {
        if (event.getEntity() == null) {
            return;
        }
        super.dispatch(event);
    }

    @Override
    public void handle(Message<EntityEvent<FolderEntity>> message) {
        // index + relationships
        super.handle(message);

        EntityEvent<FolderEntity> event = message.getPayload();
        FolderEntity entity = event.getEntity();
        FolderEntity prev = event.getPrev();
        if (log.isTraceEnabled()) {
            log.trace("{}: {}", clazz.getSimpleName(), String.valueOf(entity));
        }

        //always align entries
        if (entriesService != null) {
            try {
                entriesService.registerEntry(entity.getProject(), entity.getParentId(), converter.convert(entity));
            } catch (StoreException e) {
                log.error("store error", e);
            }
        }

        //always broadcast updates
        super.broadcast(event);
    }

    @Override
    protected void onDelete(FolderEntity entity, Folder dto) {
        super.onDelete(entity, dto);

        //delete all entries
        if (entriesService != null) {
            try {
                //delete the folder entry
                entriesService.deleteEntry(entity.getId());

                //delete all children entries
                entriesService.deleteAllEntriesByFolderId(entity.getProject(), entity.getId());
            } catch (StoreException e) {
                log.error("store error", e);
            }
        }
    }
}
