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

package it.smartcommunitylabdhub.files.base;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.FileInfo;
import it.smartcommunitylabdhub.files.models.FileInfoFieldAccessor;
import it.smartcommunitylabdhub.files.models.FilesInfo;
import it.smartcommunitylabdhub.files.models.PathAccessor;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import it.smartcommunitylabdhub.files.service.FilesInfoService;
import it.smartcommunitylabdhub.files.service.FilesService;
import it.smartcommunitylabdhub.files.utils.PathUtils;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class BaseFilesService<
    D extends BaseDTO & SpecDTO & StatusDTO
> implements EntityFilesService<D>, InitializingBean {

    public static final String STATE_UPLOADING = "UPLOADING";

    protected final Class<D> type;

    protected EntityRepository<D> entityService;

    protected FilesService filesService;
    protected FilesInfoService filesInfoService;
    protected CredentialsService credentialsService;

    @SuppressWarnings("unchecked")
    protected BaseFilesService() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = (Class<D>) t;
    }

    protected String resolveBasePath(String project, String name, String id, String filename) throws StoreException {
        //managed files live under the project base path
        return filesService.getProjectBasePath(project) + EntityUtils.getEntityName(type).toLowerCase();
    }

    private String resolveUploadBasePath(String path) {
        if (path.endsWith("/")) {
            return path;
        }

        int separator = path.lastIndexOf('/');
        return separator >= 0 ? path.substring(0, separator + 1) : "";
    }

    private String resolveUploadPath(String path, @Nullable String filename) {
        String filePath = filename != null && filename.startsWith("/") ? filename.substring(1) : filename;
        String basePath = resolveUploadBasePath(path);

        if (!StringUtils.hasText(filePath)) {
            return basePath;
        }

        if (!path.endsWith("/") && (path.equals(filePath) || path.endsWith("/" + filePath))) {
            return path;
        }

        return basePath + filePath;
    }

    @Autowired
    public void setEntityService(EntityRepository<D> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setFilesService(FilesService filesService) {
        this.filesService = filesService;
    }

    @Autowired
    public void setFilesInfoService(FilesInfoService filesInfoService) {
        this.filesInfoService = filesInfoService;
    }

    @Autowired
    public void setCredentialsService(CredentialsService credentialsService) {
        this.credentialsService = credentialsService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(entityService, "entity service can not be null");
        Assert.notNull(filesService, "files service can not be null");
        Assert.notNull(filesInfoService, "files info service can not be null");
    }

    @Override
    public Class<D> getType() {
        return type;
    }

    protected String defaultPath(D dto) throws StoreException {
        //name as encoded in the key, same as the managed path assigned on UPLOADING
        String keyName = KeyAccessor.with(dto.getKey()).getName();
        String name = StringUtils.hasText(keyName) ? keyName : KeyUtils.encode(dto.getName());
        String subpath = (name != null ? name + "/" : "") + dto.getId();
        return resolveBasePath(dto.getProject(), name, dto.getId(), null) + "/" + subpath + "/";
    }

    @Override
    public DownloadInfo downloadFileAsUrl(@NotNull String id) throws NoSuchEntityException, SystemException {
        log.debug("download url for dto with id {}", String.valueOf(id));

        try {
            //fetch
            D dto = entityService.get(id);

            //extract path from spec
            PathAccessor accessor = PathAccessor.with(dto.getSpec());
            String path = accessor.getPath();
            if (!StringUtils.hasText(path)) {
                path = defaultPath(dto);
                // throw new NoSuchEntityException("file");
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials =
                auth != null && credentialsService != null ? credentialsService.getCredentials(auth) : null;

            DownloadInfo info = filesService.getDownloadAsUrl(path, null, credentials);
            if (log.isTraceEnabled()) {
                log.trace("download url for entity with id {}: {} -> {}", id, path, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public DownloadInfo downloadFileAsUrl(@NotNull String id, @NotNull String sub)
        throws NoSuchEntityException, SystemException {
        log.debug("download url for dto file with id {} and path {}", String.valueOf(id), String.valueOf(sub));

        try {
            //fetch
            D dto = entityService.get(id);

            //extract path from spec
            PathAccessor accessor = PathAccessor.with(dto.getSpec());
            String path = accessor.getPath();
            if (!StringUtils.hasText(path)) {
                //always set a subpath to avoid collisions
                path = defaultPath(dto);
                // throw new NoSuchEntityException("file");
            }

            String resolvedPath = path;
            String fullPath = Optional.ofNullable(sub)
                .map(s -> {
                    //build sub path *only* if not matching spec path
                    return resolvedPath.endsWith(sub) ? resolvedPath : resolvedPath + sub;
                })
                .orElse(path);

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials =
                auth != null && credentialsService != null ? credentialsService.getCredentials(auth) : null;

            DownloadInfo info = filesService.getDownloadAsUrl(fullPath, null, credentials);
            if (log.isTraceEnabled()) {
                log.trace("download url for dto with id {} and path {}: {} -> {}", id, sub, path, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public Slice<FileInfo> getFileInfo(@NotNull String id, @Nullable Pageable pageable)
        throws NoSuchEntityException, SystemException {
        log.debug("get storage metadata for dto with id {}", String.valueOf(id));
        try {
            //fetch
            D dto = entityService.get(id);

            //info are in status
            FileInfoFieldAccessor fileInfoAccessor = FileInfoFieldAccessor.with(dto.getStatus());
            List<FileInfo> files = fileInfoAccessor.getFiles();

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials =
                auth != null && credentialsService != null ? credentialsService.getCredentials(auth) : null;

            if (files == null || files.isEmpty()) {
                FilesInfo filesInfo = filesInfoService.getFilesInfo(EntityUtils.getEntityName(type), id);
                if (filesInfo != null && (filesInfo.getFiles() != null)) {
                    files = filesInfo.getFiles();
                } else {
                    files = null;
                }
            }

            Slice result = null;

            if (files != null) {
                result = new PageImpl<>(files, pageable != null ? pageable : Pageable.unpaged(), files.size());
            } else {
                //extract path from spec
                PathAccessor accessor = PathAccessor.with(dto.getSpec());
                String path = accessor.getPath();
                if (!StringUtils.hasText(path)) {
                    path = defaultPath(dto);
                    // throw new NoSuchEntityException("file");
                }

                result = filesService.getFileInfo(path, credentials, pageable);
            }

            if (result == null) {
                result = Page.empty(pageable != null ? pageable : Pageable.unpaged());
            }

            if (log.isTraceEnabled()) {
                log.trace("files info for entity with id {}: {} -> {}", id, EntityUtils.getEntityName(type), result);
            }

            return result;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public void storeFileInfo(@NotNull String id, List<FileInfo> files) throws SystemException {
        try {
            //fetch
            D dto = entityService.get(id);

            if (files != null) {
                log.debug("store files info for {}", dto.getId());
                filesInfoService.saveFilesInfo(EntityUtils.getEntityName(type), id, files);
            }
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /*
     * Uploads
     */

    /**
     * The folder where uploads are allowed for the object, ending with "/".
     *
     * Default: only for objects in UPLOADING, in the folder of their spec.path (assigned when entering
     * UPLOADING).
     */
    protected String uploadFolder(@NotNull D dto) throws StoreException {
        String state = StatusFieldAccessor.with(dto.getStatus()).getState();
        if (!STATE_UPLOADING.equals(state)) {
            throw new IllegalArgumentException("uploads are allowed only in " + STATE_UPLOADING + " state");
        }

        String specPath = PathAccessor.with(dto.getSpec()).getPath();
        if (!StringUtils.hasText(specPath)) {
            throw new IllegalArgumentException("missing path");
        }

        String folder = resolveUploadBasePath(specPath);
        if (!StringUtils.hasText(folder)) {
            throw new IllegalArgumentException("invalid path");
        }

        return folder;
    }

    /**
     * Folder for uploads of an object not created yet (pre-create flow): <base><type>/<name>/<id>/,
     * name encoded as in keys, random id when missing
     */
    protected String preCreateFolder(@NotNull String project, @Nullable String name, @Nullable String id)
        throws NoSuchEntityException, StoreException {
        String subpath =
            (StringUtils.hasText(name) ? KeyUtils.encode(name) + "/" : "") +
            (id != null ? id : UUID.randomUUID().toString());
        return resolveBasePath(project, name, id, null) + "/" + subpath + "/";
    }

    /**
     * Base for multipart uploads of an object not created yet: the type folder <base><type>/
     */
    protected String preCreateBase(@NotNull String project, @Nullable String id)
        throws NoSuchEntityException, StoreException {
        return resolveBasePath(project, null, id, null) + "/";
    }

    private D find(@Nullable String id) throws StoreException {
        return id != null ? entityService.find(id) : null;
    }

    private List<Credentials> credentials() {
        //credentials of the current user (the owner)
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        return auth != null && credentialsService != null ? credentialsService.getCredentials(auth) : null;
    }

    private String uploadPath(String folder, String filename) {
        String fullPath = resolveUploadPath(folder, filename);
        if (!PathUtils.isWithin(fullPath, folder)) {
            throw new IllegalArgumentException("invalid file path");
        }
        return fullPath;
    }

    @Override
    public UploadInfo uploadFileAsUrl(
        @NotNull String project,
        @Nullable String name,
        @Nullable String id,
        @Nullable String filename
    ) throws NoSuchEntityException, SystemException {
        log.debug("upload url for dto with id {}: {}", String.valueOf(id), filename);
        try {
            //existing objects follow their upload rules, missing ones the pre-create folder
            D dto = find(id);
            String folder = dto != null ? uploadFolder(dto) : preCreateFolder(project, name, id);
            return uploadFileAsUrl(folder, filename);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /**
     * Upload url for a file within the given folder
     */
    protected UploadInfo uploadFileAsUrl(@NotNull String folder, @Nullable String filename) throws StoreException {
        if (!StringUtils.hasText(filename)) {
            //build only base upload info, we can not provide a full file path
            return UploadInfo.builder().path(folder).build();
        }

        UploadInfo info = filesService.getUploadAsUrl(uploadPath(folder, filename), credentials());
        if (log.isTraceEnabled()) {
            log.trace("upload url in {}: {}", folder, info);
        }

        return info;
    }

    @Override
    public UploadInfo startMultiPartUpload(
        @NotNull String project,
        @Nullable String name,
        @Nullable String id,
        @NotNull String filename
    ) throws NoSuchEntityException, SystemException {
        log.debug("start upload url for dto with id {}: {}", String.valueOf(id), filename);
        try {
            D dto = find(id);
            String folder = dto != null ? uploadFolder(dto) : preCreateFolder(project, name, id);
            return startMultiPartUpload(folder, filename);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /**
     * Start a multipart upload for a file within the given folder
     */
    protected UploadInfo startMultiPartUpload(@NotNull String folder, @NotNull String filename) throws StoreException {
        UploadInfo info = filesService.startMultiPartUpload(uploadPath(folder, filename), credentials());
        if (log.isTraceEnabled()) {
            log.trace("start upload url in {}: {}", folder, info);
        }

        return info;
    }

    @Override
    public UploadInfo uploadMultiPart(
        @NotNull String project,
        @Nullable String id,
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull Integer partNumber
    ) throws NoSuchEntityException, SystemException {
        log.debug("upload part url for dto {}: {}", String.valueOf(id), partNumber);
        try {
            D dto = find(id);
            String folder = dto != null ? uploadFolder(dto) : preCreateBase(project, id);
            return uploadMultiPart(folder, path, uploadId, partNumber);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /**
     * Upload url for a part of a multipart upload within the given folder
     */
    protected UploadInfo uploadMultiPart(
        @NotNull String folder,
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull Integer partNumber
    ) throws StoreException {
        if (!PathUtils.isWithin(path, folder)) {
            throw new IllegalArgumentException("invalid file path");
        }

        UploadInfo info = filesService.uploadMultiPart(path, uploadId, partNumber, credentials());
        if (log.isTraceEnabled()) {
            log.trace("part upload url for path {}: {}", path, info);
        }

        return info;
    }

    @Override
    public UploadInfo completeMultiPartUpload(
        @NotNull String project,
        @Nullable String id,
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> eTagPartList
    ) throws NoSuchEntityException, SystemException {
        log.debug("complete upload url for dto {}: {}", String.valueOf(id), path);
        try {
            D dto = find(id);
            String folder = dto != null ? uploadFolder(dto) : preCreateBase(project, id);
            return completeMultiPartUpload(folder, path, uploadId, eTagPartList);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    /**
     * Complete a multipart upload within the given folder
     */
    protected UploadInfo completeMultiPartUpload(
        @NotNull String folder,
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> eTagPartList
    ) throws StoreException {
        if (!PathUtils.isWithin(path, folder)) {
            throw new IllegalArgumentException("invalid file path");
        }

        UploadInfo info = filesService.completeMultiPartUpload(path, uploadId, eTagPartList, credentials());
        if (log.isTraceEnabled()) {
            log.trace("complete upload url for path {}: {}", path, info);
        }

        return info;
    }
}
