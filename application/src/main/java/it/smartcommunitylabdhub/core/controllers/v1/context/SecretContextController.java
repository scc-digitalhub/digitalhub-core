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

package it.smartcommunitylabdhub.core.controllers.v1.context;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.secret.Secret;
import it.smartcommunitylabdhub.commons.services.SecretService;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
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

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/secrets")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Secret context API", description = "Endpoints related to secrets management for project")
public class SecretContextController {

    @Autowired
    SecretService secretService;

    @Operation(summary = "Create a secret in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Secret createSecret(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestBody @Valid @NotNull Secret dto
    ) throws DuplicatedEntityException, IllegalArgumentException, SystemException, BindException {
        //enforce project match
        dto.setProject(project);

        return secretService.createSecret(dto);
    }

    @Operation(summary = "Retrieve all secrets for the project")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Secret> searchSecrets(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "name", direction = Direction.ASC) }
        ) Pageable pageable
    ) {
        return secretService.listSecretsByProject(project, pageable);
    }

    @Operation(summary = "Retrieve a specific secret given the secret id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Secret getSecretById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Secret secret = secretService.getSecret(id);

        //check for project match
        if (!secret.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return secret;
    }

    @Operation(summary = "Update if exist a secret in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Secret updateSecretById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Secret secretDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        Secret secret = secretService.getSecret(id);

        //check for project match
        if (!secret.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return secretService.updateSecret(id, secretDTO);
    }

    @Operation(summary = "Delete a specific secret version")
    @DeleteMapping(path = "/{id}")
    public void deleteSecretById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Secret secret = secretService.getSecret(id);

        //check for project match
        if (!secret.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // Remove secret and return
        secretService.deleteSecret(id);
    }

    /*
     * Data
     */
    @Operation(summary = "Read project secret data", description = "Get project secrets data for the specified keys")
    @GetMapping(path = "/data", produces = "application/json; charset=UTF-8")
    public Map<String, String> getSecretData(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam @Valid @NotNull Set<String> keys
    ) {
        return secretService.getSecretData(project, keys);
    }

    @Operation(summary = "Store project secret data", description = "Store project secrets data")
    @PutMapping(path = "/data", produces = "application/json; charset=UTF-8")
    public void storeSecretData(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestBody @Valid @NotNull Map<String, String> values
    ) {
        secretService.storeSecretData(project, values);
    }
}
