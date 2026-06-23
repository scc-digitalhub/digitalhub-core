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

package it.smartcommunitylabdhub.folder.services;

import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.core.services.BaseEntityServiceImpl;
import it.smartcommunitylabdhub.folder.Folder;
import it.smartcommunitylabdhub.folder.lifecycle.FolderState;
import it.smartcommunitylabdhub.folder.persistence.FolderEntity;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.validation.BindException;

@Service
public class FolderEntityService extends BaseEntityServiceImpl<Folder, FolderEntity> {

    @Override
    public Folder create(@NotNull Folder dto)
        throws IllegalArgumentException, BindException, DuplicatedEntityException, StoreException {
        //always set status to READY at creation
        dto.setStatus(MapUtils.mergeMultipleMaps(dto.getStatus(), Map.of("state", FolderState.READY.name())));
        return super.create(dto);
    }
}
