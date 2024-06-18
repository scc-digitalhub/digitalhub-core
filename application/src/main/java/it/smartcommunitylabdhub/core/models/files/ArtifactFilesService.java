package it.smartcommunitylabdhub.core.models.files;

import java.util.List;
import java.util.Map;

import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.DownloadInfo;
import it.smartcommunitylabdhub.commons.models.base.FileInfo;
import jakarta.validation.constraints.NotNull;

public interface ArtifactFilesService {
    public DownloadInfo downloadArtifactAsUrl(@NotNull String id) throws NoSuchEntityException, SystemException;
    
    public Map<String, List<FileInfo>> getObjectMetadata(@NotNull String id) throws NoSuchEntityException, SystemException;
}
