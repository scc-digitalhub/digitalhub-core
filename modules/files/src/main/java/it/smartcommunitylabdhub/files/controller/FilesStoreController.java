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

package it.smartcommunitylabdhub.files.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsService;
import it.smartcommunitylabdhub.authorization.utils.UserAuthenticationHelper;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.services.EntityService;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.FileInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.service.FilesService;
import it.smartcommunitylabdhub.files.service.FilesStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/files")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Files context API", description = "Endpoints related to files management in project")
public class FilesStoreController {

    @Autowired
    private FilesService filesService;

    @Autowired
    private EntityService<Project> projectService;

    @Autowired
    private CredentialsService credentialsService;

    @GetMapping(path = "/stores", produces = "application/json; charset=UTF-8")
    public Collection<String> getFilesStores(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project
    ) throws NoSuchEntityException, StoreException {
        //TODO remove workaround and expose project file store as is
        return Collections.singleton(filesService.getDefaultStore(projectService.get(project)) + "/" + project);
    }

    @GetMapping(path = "/info", produces = "application/json; charset=UTF-8")
    public List<FileInfo> listFiles(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam @Valid @NotNull String path
    ) throws NoSuchEntityException, StoreException {
        //try to resolve credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        List<Credentials> credentials = auth != null && credentialsService != null
            ? credentialsService.getCredentials(auth)
            : null;

        Optional<FilesStore> store = Optional.ofNullable(filesService.getStore(path));
        if (!store.isPresent()) {
            throw new NoSuchEntityException();
        }

        return store.get().fileInfo(path, false, credentials);
    }

    /*
     * Files
     */
    @GetMapping(path = "/download", produces = "application/json; charset=UTF-8")
    public DownloadInfo downloadAsUrl(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam @Valid @NotNull String path
    ) throws NoSuchEntityException, StoreException {
        //try to resolve credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        List<Credentials> credentials = auth != null && credentialsService != null
            ? credentialsService.getCredentials(auth)
            : null;

        Optional<FilesStore> store = Optional.ofNullable(filesService.getStore(path));
        if (!store.isPresent()) {
            throw new NoSuchEntityException();
        }

        return store.get().downloadAsUrl(path, null, credentials);
    }

    @PostMapping(path = "/download", produces = "application/json; charset=UTF-8")
    public DownloadInfo downloadAsUrl(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam @Valid @NotNull String path,
        @RequestParam(required = false) Integer duration
    ) throws NoSuchEntityException, StoreException {
        //try to resolve credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        List<Credentials> credentials = auth != null && credentialsService != null
            ? credentialsService.getCredentials(auth)
            : null;

        Optional<FilesStore> store = Optional.ofNullable(filesService.getStore(path));
        if (!store.isPresent()) {
            throw new NoSuchEntityException();
        }

        return store.get().downloadAsUrl(path, duration, credentials);
    }

    @DeleteMapping(path = "/delete", produces = "application/json; charset=UTF-8")
    public void delete(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam @Valid @NotNull String path
    ) throws NoSuchEntityException, StoreException {
        //try to resolve credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        List<Credentials> credentials = auth != null && credentialsService != null
            ? credentialsService.getCredentials(auth)
            : null;

        Optional<FilesStore> store = Optional.ofNullable(filesService.getStore(path));
        if (!store.isPresent()) {
            throw new NoSuchEntityException();
        }

        store.get().remove(path, true, credentials);
    }

    @PostMapping(path = "/upload", produces = "application/json; charset=UTF-8")
    public UploadInfo uploadAsUrlArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam @Valid @NotNull String path,
        @RequestParam @NotNull String filename
    ) throws NoSuchEntityException, StoreException {
        //try to resolve credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        List<Credentials> credentials = auth != null && credentialsService != null
            ? credentialsService.getCredentials(auth)
            : null;

        Optional<FilesStore> store = Optional.ofNullable(filesService.getStore(path));
        if (!store.isPresent()) {
            throw new NoSuchEntityException();
        }

        if (filename.contains("/")) {
            throw new IllegalArgumentException("invalid filename");
        }

        String fullPath = path.endsWith("/") ? path + filename : path + "/" + filename;
        return store.get().uploadAsUrl(fullPath, credentials);
    }

    @PostMapping(path = "/multipart/start", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartStartUploadAsUrlArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam @Valid @NotNull String path,
        @RequestParam @NotNull String filename
    ) throws NoSuchEntityException, StoreException {
        //try to resolve credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        List<Credentials> credentials = auth != null && credentialsService != null
            ? credentialsService.getCredentials(auth)
            : null;

        Optional<FilesStore> store = Optional.ofNullable(filesService.getStore(path));
        if (!store.isPresent()) {
            throw new NoSuchEntityException();
        }

        if (filename.contains("/")) {
            throw new IllegalArgumentException("invalid filename");
        }

        String fullPath = path.endsWith("/") ? path + filename : path + "/" + filename;
        return store.get().startMultiPartUpload(fullPath, credentials);
    }

    @PutMapping(path = "/multipart/part", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartPartUploadAsUrlArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam @Valid @NotNull String path,
        @RequestParam @NotNull String filename,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull Integer partNumber
    ) throws NoSuchEntityException, StoreException {
        //try to resolve credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        List<Credentials> credentials = auth != null && credentialsService != null
            ? credentialsService.getCredentials(auth)
            : null;

        Optional<FilesStore> store = Optional.ofNullable(filesService.getStore(path));
        if (!store.isPresent()) {
            throw new NoSuchEntityException();
        }

        if (filename.contains("/")) {
            throw new IllegalArgumentException("invalid filename");
        }

        String fullPath = path.endsWith("/") ? path + filename : path + "/" + filename;
        return store.get().uploadMultiPart(fullPath, uploadId, partNumber, credentials);
    }

    @PostMapping(path = "/multipart/complete", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartCompleteUploadAsUrlArtifactById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam @Valid @NotNull String path,
        @RequestParam @NotNull String filename,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull List<String> partList
    ) throws NoSuchEntityException, StoreException {
        //try to resolve credentials
        UserAuthentication<?> auth = UserAuthenticationHelper.getUserAuthentication();
        List<Credentials> credentials = auth != null && credentialsService != null
            ? credentialsService.getCredentials(auth)
            : null;

        Optional<FilesStore> store = Optional.ofNullable(filesService.getStore(path));
        if (!store.isPresent()) {
            throw new NoSuchEntityException();
        }

        if (filename.contains("/")) {
            throw new IllegalArgumentException("invalid filename");
        }

        String fullPath = path.endsWith("/") ? path + filename : path + "/" + filename;
        return store.get().completeMultiPartUpload(fullPath, uploadId, partList, credentials);
    }
}
