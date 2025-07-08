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

package it.smartcommunitylabdhub.credentials.minio;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.minio.credentials.AssumeRoleProvider;
import io.minio.messages.ResponseDate;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.files.provider.S3Config;
import it.smartcommunitylabdhub.files.provider.S3Config.S3ConfigBuilder;
import it.smartcommunitylabdhub.files.provider.S3Credentials;
import it.smartcommunitylabdhub.files.s3.S3FilesStore;
import it.smartcommunitylabdhub.files.service.FilesService;
import jakarta.validation.constraints.NotNull;
import java.lang.reflect.Field;
import java.security.NoSuchAlgorithmException;
import java.security.ProviderException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.util.Pair;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(name = "credentials.provider.minio.enable", havingValue = "true", matchIfMissing = false)
@Slf4j
public class MinioProvider implements ConfigurationProvider, CredentialsProvider, InitializingBean {

    private static final int DEFAULT_DURATION = 24 * 3600; //24 hour
    private static final int MIN_DURATION = 300; //5 min

    @Value("${credentials.provider.minio.access-key}")
    private String accessKey;

    @Value("${credentials.provider.minio.secret-key}")
    private String secretKey;

    @Value("${credentials.provider.minio.claim}")
    private String claim;

    @Value("${credentials.provider.minio.policy}")
    private String defaultPolicy;

    @Value("${credentials.provider.minio.endpoint}")
    private String endpointUrl;

    @Value("${credentials.provider.minio.region}")
    private String region;

    @Value("${credentials.provider.minio.enable}")
    private Boolean enabled;

    private String bucket;

    private int duration = DEFAULT_DURATION;
    private int accessTokenDuration = JwtTokenService.DEFAULT_ACCESS_TOKEN_DURATION;

    private final FilesService filesService;
    private S3Config config;

    // cache credentials for up to DURATION
    LoadingCache<Pair<String, String>, S3Credentials> cache;

    public MinioProvider(FilesService filesService) {
        Assert.notNull(filesService, "files service is required");
        this.filesService = filesService;
        //TODO define a property bean
    }

    @Autowired(required = false)
    public void setBucket(@Value("${credentials.provider.minio.bucket}") String bucket) {
        //sanity check
        if (StringUtils.hasText(bucket)) {
            this.bucket = bucket;
        } else {
            this.bucket = null;
        }
    }

    @Autowired
    public void setAccessTokenDuration(@Value("${jwt.access-token.duration}") Integer accessTokenDuration) {
        if (accessTokenDuration != null) {
            this.accessTokenDuration = accessTokenDuration.intValue();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (Boolean.TRUE.equals(enabled)) {
            //check config and override if needed
            //TODO raise an error
            enabled = StringUtils.hasText(endpointUrl);
        }

        //set duration to 2x access token duration to ensure we have valid credentials for a full cycle
        if (accessTokenDuration * 2 > duration || accessTokenDuration * 3 < duration) {
            duration = accessTokenDuration * 2;
        }

        //keep cache shorter than token duration to avoid releasing soon to be expired keys
        int cacheDuration = Math.max((duration - MIN_DURATION), MIN_DURATION);

        //initialize cache
        cache =
            CacheBuilder
                .newBuilder()
                .expireAfterWrite(cacheDuration, TimeUnit.SECONDS)
                .build(
                    new CacheLoader<Pair<String, String>, S3Credentials>() {
                        @Override
                        public S3Credentials load(@Nonnull Pair<String, String> key) throws Exception {
                            log.debug("load credentials for {} policy {}", key.getFirst(), key.getSecond());
                            return generate(key.getFirst(), key.getSecond());
                        }
                    }
                );

        //build config
        S3ConfigBuilder builder = S3Config
            .builder()
            .endpoint(endpointUrl)
            .bucket(bucket)
            .region(region)
            .signatureVersion("s3v4")
            .pathStyle(true);

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

    private S3Credentials generate(@NotNull String username, @NotNull String policy) throws StoreException {
        log.debug("generate credentials for user authentication {} policy {} via STS service", username, policy);

        try {
            //assume role as user
            //NOTE: roleArn or policy scoping does NOT work via assumeRole, only with external OIDC provider
            //credentials will receive the same set of privileges as the accessKey used to sign the request!
            AssumeRoleProvider provider = new AssumeRoleProvider(
                endpointUrl,
                accessKey,
                secretKey,
                duration,
                null,
                region,
                null,
                null,
                null,
                null
            );

            io.minio.credentials.Credentials response = provider.fetch();

            //extract private field because minio obj has no getter for expiration...
            ZonedDateTime exp = ZonedDateTime.now().plus(Duration.ofSeconds(duration - MIN_DURATION));
            try {
                Field field = io.minio.credentials.Credentials.class.getDeclaredField("expiration");
                field.setAccessible(true);

                ResponseDate rd = (ResponseDate) ReflectionUtils.getField(field, response);
                if (rd != null) {
                    exp = rd.zonedDateTime();
                }
            } catch (NoSuchFieldException | SecurityException e) {
                //no expiration, assume duration is valid
            }

            return S3Credentials
                .builder()
                .accessKey(response.accessKey())
                .secretKey(response.secretKey())
                .sessionToken(response.sessionToken())
                .expiration(exp)
                .endpoint(endpointUrl)
                .region(region)
                .bucket(bucket)
                .signatureVersion("s3v4")
                .build();
        } catch (NoSuchAlgorithmException | ProviderException e) {
            //error, no recovery
            log.error("Error with provider {}", e);
            throw new StoreException(e.getMessage());
        }
    }

    @Override
    public Credentials get(@NotNull UserAuthentication<?> auth) {
        if (Boolean.TRUE.equals(enabled) && cache != null) {
            //we expect a policy credentials in context
            MinioPolicy policy = Optional
                .ofNullable(auth.getCredentials())
                .map(creds ->
                    creds
                        .stream()
                        .filter(MinioPolicy.class::isInstance)
                        .map(c -> (MinioPolicy) c)
                        .findFirst()
                        .orElse(null)
                )
                .orElse(null);

            if (policy != null && StringUtils.hasText(policy.getPolicy())) {
                String username = auth.getName();
                log.debug("get credentials for user authentication {} from cache", username);
                try {
                    Pair<String, String> key = Pair.of(username, policy.getPolicy());
                    S3Credentials credentials = cache.get(key);
                    if (credentials == null) {
                        return null;
                    }

                    //check remaining duration against access token
                    if (
                        credentials.getExpiration() != null &&
                        ZonedDateTime
                            .now()
                            .plus(Duration.ofSeconds(accessTokenDuration + MIN_DURATION))
                            .isAfter(credentials.getExpiration())
                    ) {
                        //invalidate cache and re-fetch as new
                        log.debug("refresh credentials for user authentication {} via STS service", username);
                        cache.invalidate(key);

                        //direct cache load, if it fails we don't want to retry
                        return cache.get(key);
                    }

                    return credentials;
                } catch (ExecutionException e) {
                    //error, no recovery
                    log.error("Error with provider {}", e);
                }
                // //TODO cache on username+policy for EXPIRE-skew
                // log.debug("generate credentials for user authentication {} via STS service", auth.getName());

                // try {
                //     AssumeRoleProvider provider = new AssumeRoleProvider(
                //         endpointUrl,
                //         accessKey,
                //         secretKey,
                //         defaultDuration,
                //         policy.getPolicy(),
                //         region,
                //         null,
                //         null,
                //         null,
                //         null
                //     );

                //     io.minio.credentials.Credentials response = provider.fetch();

                //     return S3Credentials
                //         .builder()
                //         .accessKey(response.accessKey())
                //         .secretKey(response.secretKey())
                //         .sessionToken(response.sessionToken())
                //         .endpoint(endpointUrl)
                //         .region(region)
                //         .signatureVersion("s3v4")
                //         .build();
                // } catch (NoSuchAlgorithmException | ProviderException e) {
                //     //error, no recovery
                //     log.error("Error with provider {}", e);
                // }
            }
        }

        return null;
    }

    @Override
    public S3Config getConfig() {
        return config;
    }

    @Override
    public <T extends AbstractAuthenticationToken> Credentials process(@NotNull T token) {
        if (Boolean.TRUE.equals(enabled)) {
            //extract a policy from jwt tokens
            if (token instanceof JwtAuthenticationToken && StringUtils.hasText(claim)) {
                String policy = ((JwtAuthenticationToken) token).getToken().getClaimAsString(claim);
                if (StringUtils.hasText(policy)) {
                    return MinioPolicy.builder().claim(claim).policy(policy).build();
                }
            }

            //fallback to default
            if (StringUtils.hasText(defaultPolicy)) {
                return MinioPolicy.builder().claim(claim).policy(defaultPolicy).build();
            }
        }

        return null;
    }
}
