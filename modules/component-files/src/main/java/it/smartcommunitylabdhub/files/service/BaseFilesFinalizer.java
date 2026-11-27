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
import it.smartcommunitylabdhub.files.utils.PathUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

/**
 * Files finalizer: remove the files OWNED by the object, then clear the files info.
 *
 * Ownership is declared by subclasses ({@link #ownedFolder}): by default an object owns nothing and
 * its files are never removed. The owned folder is removed (whole) only when spec.path is within it
 * and no other object still references it ({@link #isReferenced}). Paths outside the owned folder
 * (external stores, raw project files, other entities) are never removed.
 *
 * Annotated as onDeleting effect by each subclass for its type. A failed removal fails the
 * finalization (CoreRuntimeException): with a lifecycle the object moves to ERROR and can be deleted
 * again. Idempotent.
 */
@Slf4j
public abstract class BaseFilesFinalizer<D extends BaseDTO & SpecDTO & StatusDTO> implements Effect<D> {

    protected FilesService filesService;
    protected FilesInfoService filesInfoService;
    protected CredentialsService credentialsService;

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

    /**
     * The folder owned by the object, under the project base path. Default: nothing is owned.
     *
     * @param base the project base path, ending with "/"
     */
    @Nullable
    protected String ownedFolder(@NotNull D dto, @NotNull String base) {
        return null;
    }

    /**
     * True when another object still references the owned folder: it must not be removed.
     */
    protected boolean isReferenced(@NotNull D dto, @NotNull String folder) {
        return false;
    }

    @Override
    public void process(String stage, D dto, @Nullable Serializable output) {
        if (filesService == null || filesInfoService == null) {
            return;
        }

        String id = dto.getId();
        String path = PathAccessor.with(dto.getSpec()).getPath();

        try {
            String folder = null;
            if (StringUtils.hasText(path)) {
                String base = filesService.getProjectBasePath(dto.getProject());
                String owned = ownedFolder(dto, base);
                if (owned != null && PathUtils.isWithin(path, owned) && !isReferenced(dto, owned)) {
                    folder = owned;
                }
            }

            if (folder != null) {
                log.debug("cascade delete files in {} for {} with id {}", folder, dto.getClass().getSimpleName(), id);

                //credentials of the current user (the owner)
                UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
                List<Credentials> credentials = auth != null && credentialsService != null
                    ? credentialsService.getCredentials(auth)
                    : null;

                filesService.remove(folder, credentials);
            } else if (StringUtils.hasText(path)) {
                log.debug("files at {} not owned by {} {}, keep them", path, dto.getClass().getSimpleName(), id);
            }

            //files info always goes with the object
            filesInfoService.clearFilesInfo(EntityUtils.getEntityName(dto.getClass()), id);
        } catch (StoreException e) {
            log.error("error deleting files for {}: {}", id, e.getMessage());
            throw new CoreRuntimeException("error deleting files: " + e.getMessage(), e);
        }
    }
}
