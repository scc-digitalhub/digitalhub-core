package it.smartcommunitylabdhub.files.service;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.files.FileInfo;
import it.smartcommunitylabdhub.files.models.DownloadInfo;
import it.smartcommunitylabdhub.files.models.UploadInfo;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public interface FilesStore {
    DownloadInfo downloadAsUrl(@NotNull String path, UserAuthentication<?> auth) throws StoreException;

    UploadInfo uploadAsUrl(@NotNull String path, UserAuthentication<?> auth) throws StoreException;

    UploadInfo startMultiPartUpload(@NotNull String path, UserAuthentication<?> auth) throws StoreException;

    UploadInfo uploadMultiPart(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull Integer partNumber,
        UserAuthentication<?> auth
    ) throws StoreException;

    UploadInfo completeMultiPartUpload(
        @NotNull String path,
        @NotNull String uploadId,
        @NotNull List<String> partList,
        UserAuthentication<?> auth
    ) throws StoreException;

    //TODO
    // InputStream downloadAsStream(@NotNull String path);

    List<FileInfo> fileInfo(@NotNull String path, UserAuthentication<?> auth) throws StoreException;

    void remove(@NotNull String path, UserAuthentication<?> auth) throws StoreException;
}
