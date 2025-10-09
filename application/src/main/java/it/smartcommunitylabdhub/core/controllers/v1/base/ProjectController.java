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

package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.authorization.model.ResourceShareEntity;
import it.smartcommunitylabdhub.authorization.services.ShareableAwareEntityService;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.config.SecurityProperties;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.services.ProjectManager;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.projects.filters.ProjectEntityFilter;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
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

@RestController
@ApiVersion("v1")
@RequestMapping("/projects")
@PreAuthorize("hasAuthority('ROLE_USER')")
@Validated
@Slf4j
@Tag(name = "Project base API", description = "Endpoints related to project management")
public class ProjectController {

    @Autowired
    ProjectManager projectManager;

    @Autowired
    ShareableAwareEntityService<Project> shareService;

    @Autowired
    RelationshipsAwareEntityService<Project> relationshipsService;

    @Autowired
    private AuditorAware<String> auditor;

    @Autowired
    SecurityProperties securityProperties;

    @Operation(summary = "List project", description = "Return a list of all projects")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Project> getProjects(
        @ParameterObject @Valid @Nullable ProjectEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "name", direction = Direction.ASC) }
        ) Pageable pageable,
        Authentication auth
    ) {
        SearchFilter<Project> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return projectManager.searchProjects(pageable, sf);
    }

    @Operation(summary = "Create project", description = "Create an project and return")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Project createProject(@RequestBody @Valid @NotNull Project dto)
        throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException {
        return projectManager.createProject(dto);
    }

    @Operation(summary = "Get an project by id", description = "Return an project")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Project getProject(@PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id)
        throws NoSuchEntityException {
        return projectManager.getProject(id);
    }

    @Operation(summary = "Update specific project", description = "Update and return the project")
    @PutMapping(
        path = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Project updateProject(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Project dto,
        Authentication auth
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        if (securityProperties.isRequired()) {
            //custom authorization check: only owner or with (scoped) admin role can update
            //TODO move to bean
            if (auth == null || auditor == null) {
                throw new InsufficientAuthenticationException("missing valid authentication");
            }

            String user = auditor
                .getCurrentAuditor()
                .orElseThrow(() -> new InsufficientAuthenticationException("missing valid authentication"));

            Project project = projectManager.getProject(id);
            if (
                auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())) &&
                auth.getAuthorities().stream().noneMatch(a -> (id + ":ROLE_ADMIN").equals(a.getAuthority())) &&
                !user.equals(project.getUser())
            ) {
                throw new InsufficientAuthenticationException("current user is not authorized");
            }
        }

        return projectManager.updateProject(id, dto);
    }

    @Operation(summary = "Delete a project", description = "Delete a specific project, with optional cascade")
    @DeleteMapping(path = "/{id}")
    public void deleteProject(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(required = false) Boolean cascade,
        Authentication auth
    ) {
        if (securityProperties.isRequired()) {
            //custom authorization check: only owner or with (scoped) admin role can delete
            //TODO move to bean
            if (auth == null || auditor == null) {
                throw new InsufficientAuthenticationException("missing valid authentication");
            }

            String user = auditor
                .getCurrentAuditor()
                .orElseThrow(() -> new InsufficientAuthenticationException("missing valid authentication"));

            Project project = projectManager.findProject(id);

            if (project != null) {
                if (
                    auth.getAuthorities().stream().noneMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())) &&
                    auth.getAuthorities().stream().noneMatch(a -> (id + ":ROLE_ADMIN").equals(a.getAuthority())) &&
                    !user.equals(project.getUser())
                ) {
                    throw new InsufficientAuthenticationException("current user is not authorized");
                }
            }
        }

        projectManager.deleteProject(id, cascade);
    }

    @Operation(summary = "Share a project with a user", description = "Share project")
    @PostMapping(path = "/{id}/share", produces = "application/json; charset=UTF-8")
    public ResourceShareEntity shareProject(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam @Valid @NotNull String user,
        Authentication auth
    ) throws NoSuchEntityException {
        if (auth == null || auditor == null) {
            throw new InsufficientAuthenticationException("missing valid authentication");
        }

        //only owner is authorized
        checkAuthorization(auth, id);

        //sanity check: user should be distinct
        String curUser = auditor
            .getCurrentAuditor()
            .orElseThrow(() -> new InsufficientAuthenticationException("missing valid authentication"));

        if (curUser.equals(user)) {
            throw new IllegalArgumentException("user should be distinct from current auth");
        }

        return shareService.share(id, user);
    }

    @Operation(summary = "List project shares", description = "Share project")
    @GetMapping(path = "/{id}/share", produces = "application/json; charset=UTF-8")
    public List<ResourceShareEntity> shares(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        Authentication auth
    ) throws NoSuchEntityException {
        return shareService.listSharesById(id);
    }

    @Operation(summary = "Revoke a share with a user", description = "Revoke sharing project")
    @DeleteMapping(path = "/{id}/share", produces = "application/json; charset=UTF-8")
    public void revokeShare(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestParam(name = "id") @Valid @NotNull String shareId,
        Authentication auth
    ) throws NoSuchEntityException {
        //only owner is authorized
        checkAuthorization(auth, id);

        shareService.revoke(id, shareId);
    }

    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        return relationshipsService.getRelationships(id);
    }

    private void checkAuthorization(Authentication auth, String id) {
        if (securityProperties.isRequired()) {
            //custom authorization check: only owner or with (scoped) admin role can delete
            //TODO move to bean
            if (auth == null || auditor == null) {
                throw new InsufficientAuthenticationException("missing valid authentication");
            }

            String user = auditor
                .getCurrentAuditor()
                .orElseThrow(() -> new InsufficientAuthenticationException("missing valid authentication"));

            Project project = projectManager.findProject(id);

            if (project != null) {
                boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
                boolean hasRole = auth
                    .getAuthorities()
                    .stream()
                    .anyMatch(a -> (id + ":ROLE_ADMIN").equals(a.getAuthority()));
                boolean isOwner = user.equals(project.getUser());

                if (!isAdmin && !isOwner && !hasRole) {
                    throw new InsufficientAuthenticationException("current user is not authorized");
                }
            }
        }
    }
}
