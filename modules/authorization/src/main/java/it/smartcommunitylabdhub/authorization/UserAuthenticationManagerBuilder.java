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

import it.smartcommunitylabdhub.authorization.services.AuthorizableAwareEntityService;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.commons.models.project.Project;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class UserAuthenticationManagerBuilder {

    private List<CredentialsProvider> credentialsProviders = Collections.emptyList();
    private AuthorizableAwareEntityService<Project> projectAuthHelper;
    private AuthenticationEventPublisher eventPublisher;
    private MessageSource messageSource;

    @Autowired
    public void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Autowired
    public void setEventPublisher(AuthenticationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setCredentialsProviders(List<CredentialsProvider> providers) {
        if (providers != null) {
            this.credentialsProviders = providers;
        }
    }

    @Autowired
    public void setProjectAuthHelper(AuthorizableAwareEntityService<Project> projectAuthHelper) {
        this.projectAuthHelper = projectAuthHelper;
    }

    public UserAuthenticationManager build(AuthenticationProvider... providers) {
        return build(Arrays.asList(providers));
    }

    public UserAuthenticationManager build(List<AuthenticationProvider> authProviders) {
        UserAuthenticationManager manager = new UserAuthenticationManager(authProviders);
        manager.setProviders(credentialsProviders);
        manager.setProjectAuthHelper(projectAuthHelper);

        manager.setAuthenticationEventPublisher(eventPublisher);
        manager.setMessageSource(messageSource);
        return manager;
    }
}
