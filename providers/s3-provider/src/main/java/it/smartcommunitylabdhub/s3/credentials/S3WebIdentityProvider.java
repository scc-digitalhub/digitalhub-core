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

package it.smartcommunitylabdhub.s3.credentials;

import com.nimbusds.jwt.SignedJWT;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.s3.base.S3BaseProvider;
import it.smartcommunitylabdhub.s3.config.S3Properties;
import it.smartcommunitylabdhub.s3.config.S3STSConfig;
import it.smartcommunitylabdhub.s3.controllers.S3STSEndpoint;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;

@Slf4j
public class S3WebIdentityProvider
    extends S3BaseProvider
    implements S3CredentialsProvider<S3WebIdentityCredentials>, ConfigurationProvider, InitializingBean
{

    private static final String ROLE_NAME = "core-s3-sts";

    private static final int DEFAULT_DURATION = 24 * 3600 * 30; //30 days
    private static final int MIN_DURATION = 300; //5 min

    private int duration = DEFAULT_DURATION;

    private JwtTokenService jwtTokenService;
    private ApplicationProperties applicationProperties;

    private String secretsMountPath = null;

    private S3STSConfig stsConfig;

    public S3WebIdentityProvider(S3Properties properties) {
        super(properties);
    }

    @Autowired(required = true)
    public void setApplicationProperties(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Autowired(required = false)
    public void setJwtTokenService(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    @Autowired(required = false)
    public void setSecretsMountPath(@Value("${kubernetes.secrets.mount-path}") String secretsMountPath) {
        this.secretsMountPath = secretsMountPath;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (
            properties.getWebIdentityTokenDuration() != null && properties.getWebIdentityTokenDuration() > MIN_DURATION
        ) {
            log.debug("Set web identity token duration to {}", properties.getWebIdentityTokenDuration());
            this.duration = properties.getWebIdentityTokenDuration();

            this.stsConfig = S3STSConfig.builder()
                .stsEndpoint(applicationProperties.getEndpoint() + S3STSEndpoint.STS_URL)
                .build();
        }
    }

    @Override
    public S3WebIdentityCredentials get(@NotNull UserAuthentication<?> auth) {
        if (jwtTokenService == null || !properties.isWebIdentityProviderEnabled()) {
            return null;
        }

        log.debug("generate credentials for user authentication {} for web identity", auth.getName());

        //generate access token with custom duration and no refresh token
        SignedJWT accessToken = jwtTokenService.generateAccessToken(
            auth,
            List.of(jwtTokenService.getAudience(), jwtTokenService.getAudience() + "/" + S3STSEndpoint.AUDIENCE),
            duration
        );

        S3WebIdentityCredentials credentials = S3WebIdentityCredentials.builder()
            .webIdentityToken(accessToken.serialize())
            .webIdentityTokenFile(secretsMountPath != null ? secretsMountPath + "/AWS_WEB_IDENTITY_TOKEN" : null)
            .roleArn(roleArn(auth.getName(), null))
            .build();

        if (log.isTraceEnabled()) {
            log.trace("credentials: {}", credentials);
        }

        return credentials;
    }

    public static String roleArn(String username, @Nullable String roleName) {
        return "arn:aws:iam::" + username + ":role/" + (roleName != null ? roleName : ROLE_NAME);
    }

    @Override
    public Configuration getConfig() {
        return stsConfig;
    }
}
