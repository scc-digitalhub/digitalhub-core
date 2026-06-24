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

package it.smartcommunitylabdhub.containerimages.controllers;

import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.containerimages.ContainerImagesService;
import it.smartcommunitylabdhub.containerimages.model.ImageDescription;
import it.smartcommunitylabdhub.core.services.EntityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/-/{project}/containerimages")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "ContainerImage context API", description = "Endpoints related to containerImages management in project")
public class ContainerImagesController {

    private final EntityService<ContainerImage> entityService;
    private final ContainerImagesService containerImagesService;

    public ContainerImagesController(
        EntityService<ContainerImage> entityService,
        ContainerImagesService containerImagesService
    ) {
        this.entityService = entityService;
        this.containerImagesService = containerImagesService;
    }

    @Operation(
        summary = "Get description for a container image",
        description = "Returns OCI image annotations and, for Docker Hub images, the full README description."
    )
    @GetMapping(path = "/{id}/description", produces = "application/json; charset=UTF-8")
    public ResponseEntity<ImageDescription> getContainerImageDescription(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException, StoreException, IOException, InvalidImageReferenceException {
        ContainerImage entity = entityService.get(id);

        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        Map<String, java.io.Serializable> spec = entity.getSpec();
        String imageReference = spec != null ? (String) spec.get("image") : null;
        if (!StringUtils.hasText(imageReference)) {
            return ResponseEntity.notFound().build();
        }

        ImageDescription description = containerImagesService.getDescription(imageReference);
        return ResponseEntity.ok(description);
    }
}
