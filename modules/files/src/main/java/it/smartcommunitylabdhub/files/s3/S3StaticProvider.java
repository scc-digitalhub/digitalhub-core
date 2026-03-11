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

package it.smartcommunitylabdhub.files.s3;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AbstractAuthenticationToken;

@Slf4j
public class S3StaticProvider extends S3BaseProvider implements CredentialsProvider {

    public S3StaticProvider(S3Properties s3Properties) {
        super(s3Properties);
    }

    @Override
    public Credentials get(@NotNull UserAuthentication<?> auth) {
        if (config == null) {
            return null;
        }

        if (!properties.isStaticProviderEnabled()) {
            return null;
        }

        log.debug("use shared credentials for user authentication {} via static provider", auth.getName());

        //static credentials shared
        return S3Credentials
            .builder()
            .accessKey(properties.getAccessKey())
            .secretKey(properties.getSecretKey())
            .build();
    }

    @Override
    public <T extends AbstractAuthenticationToken> Credentials process(@NotNull T token) {
        //nothing to do, this provider is static
        return null;
    }
}
