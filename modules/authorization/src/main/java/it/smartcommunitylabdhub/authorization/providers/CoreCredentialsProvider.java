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

package it.smartcommunitylabdhub.authorization.providers;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.AuthorizableAwareEntityService;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.commons.models.project.Project;
import jakarta.validation.constraints.NotNull;
import java.util.HashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CoreCredentialsProvider implements CredentialsProvider {

    AuthorizableAwareEntityService<Project> projectAuthHelper;

    @Autowired(required = false)
    public void setProjectAuthHelper(AuthorizableAwareEntityService<Project> projectAuthHelper) {
        this.projectAuthHelper = projectAuthHelper;
    }

    @Override
    public CoreCredentials get(@NotNull UserAuthentication<?> auth) {
        return process(auth);
    }

    @Override
    public <T extends AbstractAuthenticationToken> CoreCredentials process(@NotNull T token) {
        //inflate token by adding project authorizations
        String username = token.getName();

        if (projectAuthHelper == null) {
            return null;
        }

        Set<String> projects = new HashSet<>();

        //inject roles from ownership of projects
        //derive a scoped ADMIN role
        projectAuthHelper
            .findIdsByCreatedBy(username)
            .forEach(p -> {
                projects.add(p);
            });

        //inject roles from sharing of projects
        //derive a scoped USER role
        //TODO make configurable?
        projectAuthHelper
            .findIdsBySharedTo(username)
            .forEach(p -> {
                projects.add(p);
            });

        CoreCredentials cred = CoreCredentials.builder().projects(projects).build();

        return cred;
    }
}
