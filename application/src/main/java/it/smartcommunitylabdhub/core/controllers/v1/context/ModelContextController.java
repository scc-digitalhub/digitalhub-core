package it.smartcommunitylabdhub.core.controllers.v1.context;

import java.util.List;

import javax.annotation.Nullable;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.base.DownloadInfo;
import it.smartcommunitylabdhub.commons.models.base.FileInfo;
import it.smartcommunitylabdhub.commons.models.base.RelationshipDetail;
import it.smartcommunitylabdhub.commons.models.base.UploadInfo;
import it.smartcommunitylabdhub.commons.models.entities.model.Model;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.models.entities.ModelEntity;
import it.smartcommunitylabdhub.core.models.queries.filters.entities.ModelEntityFilter;
import it.smartcommunitylabdhub.core.models.queries.services.SearchableModelService;
import it.smartcommunitylabdhub.core.models.relationships.RelationshipsModelService;
import it.smartcommunitylabdhub.files.service.EntityFilesService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/models")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Model context API", description = "Endpoints related to models management in project")
public class ModelContextController {

    @Autowired
    SearchableModelService modelService;

    @Autowired
    EntityFilesService<Model> filesService;
    
    @Autowired
    RelationshipsModelService relationshipsService;

    @Operation(summary = "Create a model in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Model createModel(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody Model dto
    ) throws DuplicatedEntityException, SystemException, IllegalArgumentException, BindException {
        //enforce project match
        dto.setProject(project);

        //create as new
        return modelService.createModel(dto);
    }

    @Operation(summary = "Search models")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Model> searchModels(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable ModelEntityFilter filter,
        @ParameterObject @RequestParam(required = false, defaultValue = "latest") String versions,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<ModelEntity> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        if ("all".equals(versions)) {
            return modelService.searchModelsByProject(project, pageable, sf);
        } else {
            return modelService.searchLatestModelsByProject(project, pageable, sf);
        }
    }

    @Operation(summary = "Delete all version of a model")
    @DeleteMapping(path = "")
    public void deleteAllModel(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @RequestParam @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String name
    ) {
        modelService.deleteModels(project, name);
    }

    /*
     * Versions
     */

    @Operation(summary = "Retrieve a specific model version given the model id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Model getModelById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Model model = modelService.getModel(id);

        //check for project and name match
        if (!model.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return model;
    }

    @Operation(summary = "Update if exist a model in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Model updateModelById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Model modelDTO
    ) throws NoSuchEntityException, SystemException, IllegalArgumentException, BindException {
        Model model = modelService.getModel(id);

        //check for project and name match
        if (!model.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return modelService.updateModel(id, modelDTO);
    }

    @Operation(
        summary = "Delete a specific model version",
        description = "First check if project exist, then delete a specific model version"
    )
    @DeleteMapping(path = "/{id}")
    public void deleteModelById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Model model = modelService.getModel(id);

        //check for project and name match
        if (!model.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        modelService.deleteModel(id);
    }

    /*
     * Files
     */
    @Operation(summary = "Get download url for a given entity, if available")
    @GetMapping(path = "/{id}/files/download", produces = "application/json; charset=UTF-8")
    public DownloadInfo downloadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @ParameterObject @RequestParam(required = false) String sub
    ) throws NoSuchEntityException {
        Model entity = modelService.getModel(id);

        //check for project and name match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        if (sub != null) {
            return filesService.downloadFileAsUrl(id, sub);
        }

        return filesService.downloadFileAsUrl(id);
    }

    @Operation(summary = "Get download url for a given artifact file, if available")
    @GetMapping(path = "/{id}/files/download/**", produces = "application/json; charset=UTF-8")
    public DownloadInfo downloadAsUrlFile(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        HttpServletRequest request
    ) throws NoSuchEntityException {
        Model model = modelService.getModel(id);

        //check for project and name match
        if (!model.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }
        String path = request.getRequestURL().toString().split("files/download/")[1];
        return filesService.downloadFileAsUrl(id, path);
    }

    @Operation(summary = "Create an upload url for a given entity, if available")
    @PostMapping(path = "/{id}/files/upload", produces = "application/json; charset=UTF-8")
    public UploadInfo uploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam @NotNull String filename
    ) throws NoSuchEntityException {
        Model entity = modelService.findModel(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.uploadFileAsUrl(id, filename);
    }

    @Operation(summary = "Create a starting multipart upload url for a given entity, if available")
    @PostMapping(path = "/{id}/files/multipart/start", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartStartUploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam @NotNull String filename
    ) throws NoSuchEntityException {
        Model entity = modelService.findModel(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.startMultiPartUpload(id, filename);
    }

    @Operation(summary = "Create a multipart upload url for a given entity, if available")
    @PutMapping(path = "/{id}/files/multipart/part", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartPartUploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam @NotNull String filename,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull Integer partNumber
    ) throws NoSuchEntityException {
        Model entity = modelService.findModel(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.uploadMultiPart(id, filename, uploadId, partNumber);
    }

    @Operation(summary = "Create a completing multipart upload url for a given entity, if available")
    @PostMapping(path = "/{id}/files/multipart/complete", produces = "application/json; charset=UTF-8")
    public UploadInfo multipartCompleteUploadAsUrlById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam @NotNull String filename,
        @RequestParam @NotNull String uploadId,
        @RequestParam @NotNull List<String> partList
    ) throws NoSuchEntityException {
        Model entity = modelService.findModel(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.completeMultiPartUpload(id, filename, uploadId, partList);
    }

    @Operation(summary = "Get file info for a given entity, if available")
    @GetMapping(path = "/{id}/files/info", produces = "application/json; charset=UTF-8")
    public List<FileInfo> getFilesInfoById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Model entity = modelService.getModel(id);

        //check for project and name match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return filesService.getFileInfo(id);
    }

    @Operation(summary = "Store file info for a given entity, if available")
    @PutMapping(path = "/{id}/files/info", produces = "application/json; charset=UTF-8")
    public void storeFilesInfoById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody List<FileInfo> files
    ) throws NoSuchEntityException {
        Model entity = modelService.getModel(id);

        //check for project and name match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        filesService.storeFileInfo(id, files);
    }
    
    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
    		@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
    		@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id) throws NoSuchEntityException {
        Model entity = modelService.findModel(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }
        
        return relationshipsService.getRelationships(id);
    }
}
