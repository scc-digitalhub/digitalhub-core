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
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.FileInfo;
import it.smartcommunitylabdhub.files.models.FileInfoFieldAccessor;
import it.smartcommunitylabdhub.files.models.FilesInfo;
import it.smartcommunitylabdhub.files.models.PathAccessor;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import it.smartcommunitylabdhub.files.service.FilesInfoService;
import it.smartcommunitylabdhub.files.service.FilesService;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Transactional
@Slf4j
public class BaseFilesService<D extends BaseDTO & MetadataDTO & SpecDTO & StatusDTO>
    implements EntityFilesService<D>, InitializingBean {

    protected final EntityName type;

    protected EntityRepository<D> entityService;

    private EntityRepository<Project> projectService;
    private FilesService filesService;
    private FilesInfoService filesInfoService;
    private CredentialsService credentialsService;

    @SuppressWarnings("unchecked")
    public BaseFilesService() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = EntityUtils.getEntityName((Class<D>) t);
    }

    @Autowired
    public void setEntityService(EntityRepository<D> entityService) {
        this.entityService = entityService;
    }

    @Autowired
    public void setProjectService(EntityRepository<Project> projectService) {
        this.projectService = projectService;
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
        Assert.notNull(projectService, "project service can not be null");
        Assert.notNull(filesService, "files service can not be null");
        Assert.notNull(filesInfoService, "files info service can not be null");
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
                throw new NoSuchEntityException("file");
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            DownloadInfo info = filesService.getDownloadAsUrl(path, null, credentials);
            if (log.isTraceEnabled()) {
                log.trace("download url for entity with id {}: {} -> {}", id, path, info);
            }

            return info;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(type.name());
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
                throw new NoSuchEntityException("file");
            }

            String fullPath = Optional
                .ofNullable(sub)
                .map(s -> {
                    //build sub path *only* if not matching spec path
                    return path.endsWith(sub) ? path : path + sub;
                })
                .orElse(path);

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            DownloadInfo info = filesService.getDownloadAsUrl(fullPath, null, credentials);
            if (log.isTraceEnabled()) {
                log.trace("download url for dto with id {} and path {}: {} -> {}", id, sub, path, info);
            }

            return info;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(type.name());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public List<FileInfo> getFileInfo(@NotNull String id) throws NoSuchEntityException, SystemException {
        log.debug("get storage metadata for dto with id {}", String.valueOf(id));
        try {
            //fetch
            D dto = entityService.get(id);

            //info are in status
            FileInfoFieldAccessor fileInfoAccessor = FileInfoFieldAccessor.with(dto.getStatus());
            List<FileInfo> files = fileInfoAccessor.getFiles();

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            if (files == null || files.isEmpty()) {
                FilesInfo filesInfo = filesInfoService.getFilesInfo(type.name(), id);
                if (filesInfo != null && (filesInfo.getFiles() != null)) {
                    files = filesInfo.getFiles();
                } else {
                    files = null;
                }
            }

            if (files == null) {
                //extract path from spec
                PathAccessor accessor = PathAccessor.with(dto.getSpec());
                String path = accessor.getPath();
                if (!StringUtils.hasText(path)) {
                    throw new NoSuchEntityException("file");
                }

                files = filesService.getFileInfo(path, credentials);
            }

            if (files == null) {
                files = Collections.emptyList();
            }

            if (log.isTraceEnabled()) {
                log.trace("files info for entity with id {}: {} -> {}", id, type.name(), files);
            }

            return files;
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(type.name());
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
                filesInfoService.saveFilesInfo(type.name(), id, files);
            }
        } catch (NoSuchEntityException e) {
            throw new NoSuchEntityException(type.name());
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    @Override
    public UploadInfo uploadFileAsUrl(
        @NotNull String project,
        @Nullable String name,
        @Nullable String id,
        @NotNull String filename
    ) throws NoSuchEntityException, SystemException {
        log.debug("upload url for dto with id {}: {}", String.valueOf(id), filename);

        try {
            //always set a subpath to avoid collisions
            String subpath = (name != null ? name + "/" : "") + (id != null ? id : UUID.randomUUID().toString());

            String path =
                filesService.getDefaultStore(projectService.get(project)) +
                "/" +
                project +
                "/" +
                type.name().toLowerCase() +
                "/" +
                subpath +
                "/";

            String fullPath = filename.startsWith("/") ? path + filename.substring(1) : path + filename;

            //entity may not exists (yet)
            D dto = entityService.find(id);
            if (dto != null) {
                //extract path from spec
                PathAccessor accessor = PathAccessor.with(dto.getSpec());
                String specPath = accessor.getPath();
                if (!StringUtils.hasText(specPath)) {
                    throw new NoSuchEntityException("file");
                }

                //path is either full or path + filename
                if (!fullPath.equals(specPath) && !fullPath.startsWith(specPath)) {
                    throw new IllegalArgumentException("invalid file path");
                }
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            UploadInfo info = filesService.getUploadAsUrl(fullPath, credentials);
            if (log.isTraceEnabled()) {
                log.trace("upload url for dto with id {}: {}", id, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
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
            //always set a subpath to avoid collisions
            String subpath = (name != null ? name + "/" : "") + (id != null ? id : UUID.randomUUID().toString());

            String path =
                filesService.getDefaultStore(projectService.get(project)) +
                "/" +
                project +
                "/" +
                type.name().toLowerCase() +
                "/" +
                subpath +
                "/";

            String fullPath = filename.startsWith("/") ? path + filename.substring(1) : path + filename;

            //entity may not exists (yet)
            D dto = entityService.find(id);
            if (dto != null) {
                //extract path from spec
                PathAccessor accessor = PathAccessor.with(dto.getSpec());
                String specPath = accessor.getPath();
                if (!StringUtils.hasText(specPath)) {
                    throw new NoSuchEntityException("file");
                }

                //path is either full or path + filename
                if (!fullPath.equals(specPath) && !fullPath.startsWith(specPath)) {
                    throw new IllegalArgumentException("invalid file path");
                }
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            UploadInfo info = filesService.startMultiPartUpload(fullPath, credentials);
            if (log.isTraceEnabled()) {
                log.trace("start upload url for dto with id {}: {}", id, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
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
            //entity may not exists (yet)
            D dto = entityService.find(id);
            if (dto != null) {
                //extract path from spec
                PathAccessor accessor = PathAccessor.with(dto.getSpec());
                String specPath = accessor.getPath();
                if (!StringUtils.hasText(specPath)) {
                    throw new NoSuchEntityException("file");
                }

                //path is either full or path + filename
                if (!path.equals(specPath) && !path.startsWith(specPath)) {
                    throw new IllegalArgumentException("invalid file path");
                }
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            UploadInfo info = filesService.uploadMultiPart(path, uploadId, partNumber, credentials);
            if (log.isTraceEnabled()) {
                log.trace("part upload url for dto with path {}: {}", path, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
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
            //entity may not exists (yet)
            D dto = entityService.find(id);
            if (dto != null) {
                //extract path from spec
                PathAccessor accessor = PathAccessor.with(dto.getSpec());
                String specPath = accessor.getPath();
                if (!StringUtils.hasText(specPath)) {
                    throw new NoSuchEntityException("file");
                }

                //path is either full or path + filename
                if (!path.equals(specPath) && !path.startsWith(specPath)) {
                    throw new IllegalArgumentException("invalid file path");
                }
            }

            //try to resolve credentials
            UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
            List<Credentials> credentials = auth != null && credentialsService != null
                ? credentialsService.getCredentials(auth)
                : null;

            UploadInfo info = filesService.completeMultiPartUpload(path, uploadId, eTagPartList, credentials);
            if (log.isTraceEnabled()) {
                log.trace("complete upload url for dto with path {}: {}", path, info);
            }

            return info;
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
