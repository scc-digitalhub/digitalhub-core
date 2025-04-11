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

package it.smartcommunitylabdhub.files.provider;

import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.files.provider.S3Config.S3ConfigBuilder;
import it.smartcommunitylabdhub.files.s3.S3FilesStore;
import it.smartcommunitylabdhub.files.service.FilesService;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "credentials.provider.s3.enable", havingValue = "true", matchIfMissing = false)
@Slf4j
public class S3Provider implements ConfigurationProvider, CredentialsProvider, InitializingBean {

    @Value("${credentials.provider.s3.access-key}")
    private String accessKey;

    @Value("${credentials.provider.s3.secret-key}")
    private String secretKey;

    @Value("${credentials.provider.s3.endpoint}")
    private String endpoint;

    private String bucket;

    @Value("${credentials.provider.s3.signature-version}")
    private String signatureVersion;

    @Value("${credentials.provider.s3.region}")
    private String region;

    @Value("${credentials.provider.s3.path-style-access}")
    private Boolean pathStyleAccess;

    private final FilesService filesService;
    private S3Config config;

    public S3Provider(FilesService filesService) {
        Assert.notNull(filesService, "files service is required");
        this.filesService = filesService;
        //TODO define a property bean
    }

    @Autowired(required = false)
    public void setBucket(@Value("${credentials.provider.s3.bucket}") String bucket) {
        //sanity check
        if (StringUtils.hasText(bucket)) {
            this.bucket = bucket;
        } else {
            this.bucket = null;
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("Build configuration for provider...");

        //build config
        S3ConfigBuilder builder = S3Config
            .builder()
            .endpoint(endpoint)
            .bucket(bucket)
            .region(region)
            .signatureVersion(signatureVersion)
            .pathStyle(pathStyleAccess);

        this.config = builder.build();

        if (log.isTraceEnabled()) {
            log.trace("config: {}", config.toJson());
        }

        //build a file store
        S3FilesStore store = new S3FilesStore(config);

        //register with service
        if (StringUtils.hasText(bucket)) {
            filesService.registerStore("s3://" + bucket, store);
            filesService.registerStore("zip+s3://" + bucket, store);
        } else {
            filesService.registerStore("s3://", store);
            filesService.registerStore("zip+s3://", store);
        }
    }

    @Override
    public Credentials get(@NotNull UserAuthentication<?> auth) {
        if (config == null) {
            return null;
        }

        log.debug("generate credentials for user authentication {} via STS service", auth.getName());

        //static credentials shared
        return S3Credentials
            .builder()
            .accessKey(accessKey)
            .secretKey(secretKey)
            .endpoint(endpoint)
            .region(region)
            .bucket(bucket)
            .signatureVersion(signatureVersion)
            .pathStyle(pathStyleAccess)
            .build();
    }

    @Override
    public S3Config getConfig() {
        return config;
    }

    @Override
    public <T extends AbstractAuthenticationToken> Credentials process(@NotNull T token) {
        //nothing to do, this provider is static
        return null;
    }
}
