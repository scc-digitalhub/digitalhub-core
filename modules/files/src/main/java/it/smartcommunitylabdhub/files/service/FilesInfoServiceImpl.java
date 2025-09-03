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

package it.smartcommunitylabdhub.files.service;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.files.models.FileInfo;
import it.smartcommunitylabdhub.files.models.FilesInfo;
import it.smartcommunitylabdhub.files.persistence.FilesInfoDTOBuilder;
import it.smartcommunitylabdhub.files.persistence.FilesInfoEntity;
import it.smartcommunitylabdhub.files.persistence.FilesInfoEntityBuilder;
import it.smartcommunitylabdhub.files.persistence.FilesInfoRepository;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

@Service
@Transactional
@Slf4j
public class FilesInfoServiceImpl implements FilesInfoService {

    @Value("${files.max-column-size}")
    private int maxColumnSize;

    @Autowired
    private FilesInfoDTOBuilder dtoBuilder;

    @Autowired
    private FilesInfoEntityBuilder entityBuilder;

    @Autowired
    private FilesInfoRepository repository;

    private StringKeyGenerator keyGenerator = () -> UUID.randomUUID().toString().replace("-", "");

    @Autowired(required = false)
    public void setKeyGenerator(StringKeyGenerator keyGenerator) {
        Assert.notNull(keyGenerator, "key generator can not be null");
        this.keyGenerator = keyGenerator;
    }

    @Override
    public FilesInfo getFilesInfo(@NotNull String entityName, @NotNull String entityId)
        throws StoreException, SystemException {
        log.debug("get files info for entity {} id {}", entityId, entityName);

        FilesInfoEntity entity = repository.findByEntityNameAndEntityId(entityName, entityId);
        if (entity != null) {
            return dtoBuilder.convert(entity);
        }

        return null;
    }

    @Override
    public FilesInfo saveFilesInfo(@NotNull String entityName, @NotNull String entityId, List<FileInfo> files)
        throws StoreException, SystemException {
        log.debug("save files info for entity {} id {}", entityName, entityId);
        FilesInfo dto = FilesInfo.builder().entityName(entityName).entityId(entityId).files(files).build();

        FilesInfoEntity entity = repository.findByEntityNameAndEntityId(entityName, entityId);
        if (entity != null) {
            dto.setId(entity.getId());
        } else {
            dto.setId(keyGenerator.generateKey());
        }

        entity = entityBuilder.convert(dto);

        //check files size before persisting
        if (entity.getFiles() != null && entity.getFiles().length > maxColumnSize) {
            throw new IllegalArgumentException("files column exceeds maximum size " + String.valueOf(maxColumnSize));
        }

        entity = repository.save(entity);
        return dtoBuilder.convert(entity);
    }

    @Override
    public void clearFilesInfo(@NotNull String entityName, @NotNull String entityId)
        throws StoreException, SystemException {
        log.debug("clear files info for entity {} id {}", entityName, entityId);

        FilesInfoEntity entity = repository.findByEntityNameAndEntityId(entityName, entityId);
        if (entity != null) {
            repository.delete(entity);
        }
    }
}
