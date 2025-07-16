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
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.commons.models.project.Project;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.user.MyUserManager;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("users")
@PreAuthorize("hasAuthority('ROLE_USER')")
@Validated
@Slf4j
@Tag(name = "User API")
public class UserController {

    @Autowired
    private MyUserManager userManager;

    @Operation(summary = "List my projects")
    @GetMapping(path = "/me/projects", produces = "application/json; charset=UTF-8")
    public List<Project> listMyProjects(Authentication auth) {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        return userManager.listMyProjects();
    }

    @Operation(summary = "Delete all my projects")
    @DeleteMapping(path = "/me/projects")
    public void deleteMyProjects(Authentication auth) {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        userManager.deleteAllMyProjects();
    }

    @Operation(summary = "Delete user")
    @DeleteMapping(path = "/me")
    public void deleteUser(Authentication auth) {
        if (auth == null || !(auth instanceof UserAuthentication)) {
            throw new InsufficientAuthenticationException("Invalid or missing authentication");
        }

        userManager.deleteMyUser();
    }
}
