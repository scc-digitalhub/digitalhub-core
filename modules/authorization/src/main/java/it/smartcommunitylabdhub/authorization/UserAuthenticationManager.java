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

package it.smartcommunitylabdhub.authorization;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.AuthorizableAwareEntityService;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.commons.models.project.Project;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class UserAuthenticationManager extends ProviderManager {

    private List<CredentialsProvider> providers = Collections.emptyList();
    private AuthorizableAwareEntityService<Project> projectAuthHelper;

    public UserAuthenticationManager(AuthenticationProvider... providers) {
        this(Arrays.asList(providers));
    }

    public UserAuthenticationManager(List<AuthenticationProvider> providers) {
        super(providers, null);
    }

    @Autowired
    public void setProviders(List<CredentialsProvider> providers) {
        if (providers != null) {
            this.providers = providers;
        }
    }

    @Autowired
    public void setProjectAuthHelper(AuthorizableAwareEntityService<Project> projectAuthHelper) {
        this.projectAuthHelper = projectAuthHelper;
    }

    @Override
    public UserAuthentication<?> authenticate(Authentication authentication) throws AuthenticationException {
        //let providers resolve auth
        Authentication auth = super.authenticate(authentication);

        return process(auth);
    }

    public UserAuthentication<?> process(Authentication auth) throws AuthenticationException {
        if (auth != null && auth.isAuthenticated() && auth instanceof AbstractAuthenticationToken) {
            Set<GrantedAuthority> authorities = new HashSet<>(auth.getAuthorities());
            String username = auth.getName();

            //refresh project authorities via helper
            if (projectAuthHelper != null) {
                //inject roles from ownership of projects
                projectAuthHelper
                    .findIdsByCreatedBy(username)
                    .forEach(p -> {
                        //derive a scoped ADMIN role
                        authorities.add(new SimpleGrantedAuthority(p + ":ROLE_ADMIN"));
                    });

                //inject roles from sharing of projects
                projectAuthHelper
                    .findIdsBySharedTo(username)
                    .forEach(p -> {
                        //derive a scoped USER role
                        //TODO make configurable?
                        authorities.add(new SimpleGrantedAuthority(p + ":ROLE_USER"));
                    });
            }

            //inflate credentials
            List<Credentials> credentials = providers
                .stream()
                .map(p -> p.process((AbstractAuthenticationToken) auth))
                .filter(c -> c != null)
                .collect(Collectors.toList());

            //create user auth with details
            UserAuthentication<?> user = new UserAuthentication<>(
                (AbstractAuthenticationToken) auth,
                username,
                authorities
            );

            user.setCredentials(credentials);
            return user;
        }

        throw new AuthenticationServiceException("invalid auth");
    }
}
