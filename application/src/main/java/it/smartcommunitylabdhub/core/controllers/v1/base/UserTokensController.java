/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

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

package it.smartcommunitylabdhub.core.controllers.v1.base;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.authorization.model.PersonalAccessToken;
import it.smartcommunitylabdhub.authorization.model.RefreshToken;
import it.smartcommunitylabdhub.authorization.model.TokenResponse;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.TokenService;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.task.Task;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("tokens")
@PreAuthorize("hasAuthority('ROLE_USER')")
@Validated
@Slf4j
@Tag(name = "Tokens base API")
public class UserTokensController {

    @Autowired
    private TokenService tokenService;

    /*
     * Refresh tokens
     */

    @Operation(summary = "List refresh tokens")
    @GetMapping(path = "/refresh", produces = "application/json; charset=UTF-8")
    public Page<RefreshToken> listRefreshTokens(Pageable pageable, Authentication auth) {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        List<RefreshToken> tokens = tokenService.getRefreshTokens((UserAuthentication<?>) auth);
        return new PageImpl<>(tokens, pageable, tokens.size());
    }

    @Operation(summary = "Get refresh tokens")
    @GetMapping(path = "/refresh/{id}", produces = "application/json; charset=UTF-8")
    public RefreshToken getRefreshToken(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        Authentication auth
    ) {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        RefreshToken token = tokenService.getRefreshToken((UserAuthentication<?>) auth, id);
        if (token == null) {
            throw new NoSuchEntityException("Refresh token not found");
        }

        return token;
    }

    @Operation(summary = "Revoke refresh tokens")
    @DeleteMapping(path = "/refresh/{id}")
    public void revokeRefreshToken(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        Authentication auth
    ) {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        tokenService.revokeRefreshToken((UserAuthentication<?>) auth, id);
    }

    /*
     * Personal access tokens
     */
    @Operation(summary = "Create personal access token")
    @PostMapping(
        path = "/personal",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public TokenResponse createPersonalAccessToken(@RequestBody @NotNull PersonalAccessToken dto, Authentication auth)
        throws IllegalArgumentException, SystemException, BindException {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        String name = dto != null ? dto.getName() : "";
        List<String> scopes = dto != null && dto.getScopes() != null
            ? List.copyOf(dto.getScopes())
            : List.of("openid", "profile", "credentials");

        return tokenService.generatePersonalAccessToken((UserAuthentication<?>) auth, name, scopes);
    }

    @Operation(summary = "List personal access tokens")
    @GetMapping(path = "/personal", produces = "application/json; charset=UTF-8")
    public Page<PersonalAccessToken> listPersonalAccessTokens(Pageable pageable, Authentication auth) {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        List<PersonalAccessToken> tokens = tokenService.getPersonalAccessTokens((UserAuthentication<?>) auth);
        return new PageImpl<>(tokens, pageable, tokens.size());
    }

    @Operation(summary = "Get personal access tokens")
    @GetMapping(path = "/personal/{id}", produces = "application/json; charset=UTF-8")
    public PersonalAccessToken getPersonalAccessToken(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        Authentication auth
    ) {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        PersonalAccessToken token = tokenService.getPersonalAccessToken((UserAuthentication<?>) auth, id);
        if (token == null) {
            throw new NoSuchEntityException("PersonalAccess token not found");
        }

        return token;
    }

    @Operation(summary = "Revoke personal access tokens")
    @DeleteMapping(path = "/personal/{id}")
    public void revokePersonalAccessToken(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        Authentication auth
    ) {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        tokenService.revokePersonalAccessToken((UserAuthentication<?>) auth, id);
    }
}
