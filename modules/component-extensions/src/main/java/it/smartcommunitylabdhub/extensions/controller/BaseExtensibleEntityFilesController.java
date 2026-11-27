/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.extensions.controller;

import io.swagger.v3.oas.annotations.Operation;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.extensions.service.ExtensionsFilesService;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Extension files uploads under the parent: /{id}/extensions/{name}/files/...
 *
 * The parent must exist in the project, in any state; the extension may not exist yet. Files go in the
 * extension folder inside the parent folder, owned by the parent. Extended by entities with files.
 */
public abstract class BaseExtensibleEntityFilesController<D extends BaseDTO> {

    protected EntityService<D> entityService;
    protected ExtensionsFilesService filesService;

    @Autowired
    public void setEntityService(EntityService<D> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setFilesService(ExtensionsFilesService filesService) {
        this.filesService = filesService;
    }

    private D parent(String project, String id) throws NoSuchEntityException, StoreException {
        D dto = entityService.get(id);

        //check for project match
        if (!dto.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return dto;
    }

    @Operation(summary = "Create an upload url for an extension of a given dto")
    @PostMapping(path = "/{id}/extensions/{name}/files/upload", produces = "application/json; charset=UTF-8")
    public UploadInfo uploadExtensionAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam(required = false) @Nullable String filename
    ) throws NoSuchEntityException, StoreException {
        return filesService.uploadFileAsUrl(parent(project, id), name, filename);
    }

    @Operation(summary = "Start a multipart upload for an extension of a given dto")
    @PostMapping(
        path = "/{id}/extensions/{name}/files/multipart/start",
        produces = "application/json; charset=UTF-8"
    )
    public UploadInfo multipartStartExtensionUploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam @NotNull String filename
    ) throws NoSuchEntityException, StoreException {
        return filesService.startMultiPartUpload(parent(project, id), name, filename);
    }

    @Operation(summary = "Generate an upload url for a part of a multipart upload for an extension of a given dto")
    @PutMapping(path = "/{id}/extensions/{name}/files/multipart/part", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartPartExtensionUploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam @NotNull String path,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull Integer partNumber
    ) throws NoSuchEntityException, StoreException {
        return filesService.uploadMultiPart(parent(project, id), name, path, uploadId, partNumber);
    }

    @Operation(summary = "Complete a multipart upload for an extension of a given dto")
    @PostMapping(
        path = "/{id}/extensions/{name}/files/multipart/complete",
        produces = "application/json; charset=UTF-8"
    )
    public UploadInfo multipartCompleteExtensionUploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name,
        @RequestParam @NotNull String path,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull List<String> partList
    ) throws NoSuchEntityException, StoreException {
        return filesService.completeMultiPartUpload(parent(project, id), name, path, uploadId, partList);
    }
}
