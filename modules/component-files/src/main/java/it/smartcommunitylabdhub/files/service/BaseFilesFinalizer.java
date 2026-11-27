/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.files.service;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.infrastructure.Effect;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.files.models.PathAccessor;
import jakarta.annotation.Nullable;
import java.io.Serializable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/**
 * Files finalizer: remove the files at spec.path, then clear the files info.
 *
 * Extended by every entity with files, annotated as onDeleting effect for its type. A failed removal
 * fails the finalization (CoreRuntimeException): with a lifecycle the object moves to ERROR and can
 * be deleted again. Idempotent.
 *
 * NOTE: the path is always removed, ownership (only paths managed in the project) is a separate plan
 */
@Slf4j
public abstract class BaseFilesFinalizer<D extends BaseDTO & SpecDTO & StatusDTO> implements Effect<D> {

    private FilesService filesService;
    private FilesInfoService filesInfoService;
    private CredentialsService credentialsService;

    @Autowired(required = false)
    public void setFilesService(FilesService filesService) {
        this.filesService = filesService;
    }

    @Autowired(required = false)
    public void setFilesInfoService(FilesInfoService filesInfoService) {
        this.filesInfoService = filesInfoService;
    }

    @Autowired(required = false)
    public void setCredentialsService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    @Override
    public void process(String stage, D dto, @Nullable Serializable output) {
        if (filesService == null || filesInfoService == null) {
            return;
        }

        String id = dto.getId();
        String path = PathAccessor.with(dto.getSpec()).getPath();
        if (!StringUtils.hasText(path)) {
            return;
        }

        log.debug("cascade delete files for {} with id {}", dto.getClass().getSimpleName(), id);
        try {
            //credentials of the current user (the owner)
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            filesService.remove(path, credentials);
            filesInfoService.clearFilesInfo(EntityUtils.getEntityName(dto.getClass()), id);
        } catch (StoreException e) {
            log.error("error deleting files for {}: {}", id, e.getMessage());
            throw new CoreRuntimeException("error deleting files: " + e.getMessage(), e);
        }
    }
}
