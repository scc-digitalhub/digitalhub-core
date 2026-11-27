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

package it.smartcommunitylabdhub.extensions.service;

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import it.smartcommunitylabdhub.extensions.model.Extension;
import it.smartcommunitylabdhub.extensions.model.ParentAccessor;
import it.smartcommunitylabdhub.files.base.BaseFilesService;
import it.smartcommunitylabdhub.files.models.PathAccessor;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import it.smartcommunitylabdhub.files.utils.PathUtils;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Extension files: they live inside the parent folder, owned by the parent.
 *
 * <base><parent type>/<parent name>/<parent id>/extensions/<ext name>/
 *
 * Uploads are allowed with the parent in any state, also before the extension exists (via the parent
 * endpoints, by extension name).
 */
@Service
@Slf4j
public class ExtensionsFilesService extends BaseFilesService<Extension> {

    /**
     * Folder of an extension, inside the parent folder (parent name encoded as in keys)
     */
    public static String extensionFolder(
        @NotNull String base,
        @NotNull String parentType,
        @NotNull String parentName,
        @NotNull String parentId,
        @NotNull String name
    ) {
        return (
            base +
            parentType.toLowerCase() +
            "/" +
            KeyUtils.encode(parentName) +
            "/" +
            parentId +
            "/extensions/" +
            name +
            "/"
        );
    }

    private String folder(Extension extension) {
        if (!StringUtils.hasText(extension.getEntity()) || !StringUtils.hasText(extension.getName())) {
            throw new IllegalArgumentException("invalid extension");
        }

        //parent key carries the plain parent name
        ParentAccessor parent = ParentAccessor.with(extension.getParent());
        if (!StringUtils.hasText(parent.getName()) || !StringUtils.hasText(parent.getId())) {
            throw new IllegalArgumentException("invalid extension parent");
        }

        return extensionFolder(
            filesService.getProjectBasePath(extension.getProject()),
            extension.getEntity(),
            parent.getName(),
            parent.getId(),
            extension.getName()
        );
    }

    private String folder(BaseDTO parent, String name) {
        Assert.hasText(name, "extension name is required");
        return extensionFolder(
            filesService.getProjectBasePath(parent.getProject()),
            EntityUtils.getEntityName(parent.getClass()),
            parent.getName(),
            parent.getId(),
            name
        );
    }

    @Override
    protected String defaultPath(Extension extension) throws StoreException {
        return folder(extension);
    }

    /**
     * Extensions upload in their folder, no state check (on the extension or on the parent)
     */
    @Override
    protected String uploadFolder(@NotNull Extension extension) throws StoreException {
        String folder = folder(extension);

        String specPath = PathAccessor.with(extension.getSpec()).getPath();
        if (StringUtils.hasText(specPath) && !PathUtils.isWithin(specPath, folder)) {
            throw new IllegalArgumentException("invalid path: extension files must be within " + folder);
        }

        return folder;
    }

    /**
     * Extensions upload by id only when they exist: before creation, upload via the parent
     */
    @Override
    protected String preCreateFolder(@NotNull String project, @Nullable String name, @Nullable String id)
        throws NoSuchEntityException {
        throw new NoSuchEntityException(EntityUtils.getEntityName(Extension.class));
    }

    @Override
    protected String preCreateBase(@NotNull String project, @Nullable String id) throws NoSuchEntityException {
        throw new NoSuchEntityException(EntityUtils.getEntityName(Extension.class));
    }

    /*
     * Uploads by parent and extension name: the extension may not exist yet, the parent owns the folder
     */

    public UploadInfo uploadFileAsUrl(@NotNull BaseDTO parent, @NotNull String name, @Nullable String filename)
        throws SystemException {
        try {
            return uploadFileAsUrl(folder(parent, name), filename);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    public UploadInfo startMultiPartUpload(@NotNull BaseDTO parent, @NotNull String name, @NotNull String filename)
        throws SystemException {
        try {
            return startMultiPartUpload(folder(parent, name), filename);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    public UploadInfo uploadMultiPart(
        @NotNull BaseDTO parent,
        @NotNull String name,
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull Integer partNumber
    ) throws SystemException {
        try {
            return uploadMultiPart(folder(parent, name), path, uploadId, partNumber);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }

    public UploadInfo completeMultiPartUpload(
        @NotNull BaseDTO parent,
        @NotNull String name,
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> eTagPartList
    ) throws SystemException {
        try {
            return completeMultiPartUpload(folder(parent, name), path, uploadId, eTagPartList);
        } catch (StoreException e) {
            log.error("store error: {}", e.getMessage());
            throw new SystemException(e.getMessage());
        }
    }
}
