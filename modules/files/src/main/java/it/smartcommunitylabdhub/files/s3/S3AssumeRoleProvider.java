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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.StsClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.StsException;

@Slf4j
public class S3AssumeRoleProvider extends S3BaseProvider implements CredentialsProvider, InitializingBean {

    private static final int DEFAULT_DURATION = 24 * 3600; //24 hour
    private static final int MIN_DURATION = 300; //5 min

    private int duration = DEFAULT_DURATION;
    private int accessTokenDuration = JwtTokenService.DEFAULT_ACCESS_TOKEN_DURATION;

    // cache credentials for up to DURATION
    LoadingCache<Pair<String, S3PolicyMapping>, S3Credentials> cache;

    public S3AssumeRoleProvider(S3Properties properties) {
        super(properties);
    }

    @Autowired
    public void setAccessTokenDuration(@Value("${jwt.access-token.duration}") Integer accessTokenDuration) {
        if (accessTokenDuration != null) {
            this.accessTokenDuration = accessTokenDuration.intValue();
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //let's make sure duration is bigger than access token to avoid releasing soon to be expired credentials
        if (accessTokenDuration < (duration + MIN_DURATION)) {
            log.warn(
                "Configured STS credentials duration {} is not aligned with access token duration {}, adjusting to {}",
                duration,
                accessTokenDuration,
                accessTokenDuration + MIN_DURATION
            );
            duration = accessTokenDuration + MIN_DURATION;
        }

        //keep cache shorter than token duration to avoid releasing soon to be expired keys
        int cacheDuration = Math.max((duration - MIN_DURATION), MIN_DURATION);

        //initialize cache
        cache =
            CacheBuilder
                .newBuilder()
                .expireAfterWrite(cacheDuration, TimeUnit.SECONDS)
                .build(
                    new CacheLoader<Pair<String, S3PolicyMapping>, S3Credentials>() {
                        @Override
                        public S3Credentials load(@Nonnull Pair<String, S3PolicyMapping> key) throws Exception {
                            log.debug("load credentials for {}", key.getFirst());
                            return generate(key.getFirst(), key.getSecond());
                        }
                    }
                );
    }

    private S3Credentials generate(@NotNull String username, @NotNull S3PolicyMapping policy) throws StoreException {
        log.debug("generate credentials for user authentication {} via STS service", username);
        if (log.isTraceEnabled()) {
            log.trace("policy: {}", policy);
        }

        try {
            // Create AWS credentials from properties
            AwsBasicCredentials awsCredentials = AwsBasicCredentials.create(
                properties.getAccessKey(),
                properties.getSecretKey()
            );

            // Build STS client with custom endpoint
            StsClientBuilder stsBuilder = StsClient
                .builder()
                .credentialsProvider(StaticCredentialsProvider.create(awsCredentials));

            // Set region - default to us-east-1 if not provided (required by SDK)
            String region = properties.getRegion() != null && !properties.getRegion().isEmpty()
                ? properties.getRegion()
                : "us-east-1";
            stsBuilder.region(Region.of(region));

            // Set custom endpoint if provided
            if (properties.getEndpoint() != null && !properties.getEndpoint().isEmpty()) {
                stsBuilder.endpointOverride(URI.create(properties.getEndpoint()));
            }

            // Generate session name
            String sessionName = "s3-" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

            // Build assume role request
            //NOTE: roleArn is required by STS, we don't check because some third party
            // implementations allow to use policies without roleArn
            AssumeRoleRequest.Builder requestBuilder = AssumeRoleRequest
                .builder()
                .roleArn(policy.getRoleArn())
                .roleSessionName(sessionName)
                .durationSeconds(duration);

            // Add policy if provided
            if (policy.getPolicy() != null && !policy.getPolicy().isEmpty()) {
                requestBuilder.policy(policy.getPolicy());
            }

            AssumeRoleRequest assumeRoleRequest = requestBuilder.build();

            if (log.isTraceEnabled()) {
                log.trace(
                    "AssumeRole request - roleArn: {}, sessionName: {}, duration: {}",
                    policy.getRoleArn(),
                    sessionName,
                    duration
                );
            }

            // Call STS to assume role
            try (StsClient stsClient = stsBuilder.build()) {
                AssumeRoleResponse response = stsClient.assumeRole(assumeRoleRequest);
                software.amazon.awssdk.services.sts.model.Credentials credentials = response.credentials();

                // Convert expiration from Instant to ZonedDateTime
                ZonedDateTime exp = credentials.expiration() != null
                    ? ZonedDateTime.ofInstant(credentials.expiration(), ZoneId.systemDefault())
                    : ZonedDateTime.now().plus(Duration.ofSeconds(duration - MIN_DURATION));

                return S3Credentials
                    .builder()
                    .accessKey(credentials.accessKeyId())
                    .secretKey(credentials.secretAccessKey())
                    .sessionToken(credentials.sessionToken())
                    .expiration(exp)
                    .build();
            }
        } catch (StsException e) {
            //error, no recovery
            log.error(
                "STS AssumeRole failed - Status: {}, Error Code: {}, Message: {}",
                e.statusCode(),
                e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : "N/A",
                e.getMessage()
            );
            if (log.isDebugEnabled() && e.awsErrorDetails() != null) {
                log.debug("Full STS error details: {}", e.awsErrorDetails());
            }
            throw new StoreException(
                "Failed to assume role: " +
                e.getMessage() +
                (e.awsErrorDetails() != null ? " (" + e.awsErrorDetails().errorCode() + ")" : "")
            );
        } catch (Exception e) {
            //error, no recovery
            log.error("Error with STS provider: {}", e.getMessage(), e);
            throw new StoreException(e.getMessage());
        }
    }

    @Override
    public Credentials get(@NotNull UserAuthentication<?> auth) {
        if (properties.isAssumeRoleProviderEnabled() && cache != null) {
            //we expect a policy credentials in context
            S3PolicyMapping policy = Optional
                .ofNullable(auth.getCredentials())
                .map(creds ->
                    creds
                        .stream()
                        .filter(S3PolicyMapping.class::isInstance)
                        .map(c -> (S3PolicyMapping) c)
                        .findFirst()
                        .orElse(null)
                )
                .orElse(null);

            if (policy != null && auth.getName() != null) {
                String username = auth.getName();
                log.debug("get credentials for user authentication {} from cache", username);
                try {
                    Pair<String, S3PolicyMapping> key = Pair.of(username, policy);
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
            }
        }

        return null;
    }

    @Override
    public <T extends AbstractAuthenticationToken> Credentials process(@NotNull T token) {
        if (properties.isAssumeRoleProviderEnabled()) {
            //extract a policy from jwt tokens
            if (token instanceof JwtAuthenticationToken) {
                //check if role or policy is set
                String roleArnParam =
                    ((JwtAuthenticationToken) token).getToken().getClaimAsString(properties.getClaim() + "/roleArn");
                String policyParam =
                    ((JwtAuthenticationToken) token).getToken().getClaimAsString(properties.getClaim() + "/policy");

                String roleArn = StringUtils.hasText(roleArnParam) ? roleArnParam : properties.getRoleArn();
                String policy = StringUtils.hasText(policyParam) ? policyParam : properties.getPolicy();

                return S3PolicyMapping.builder().claim(properties.getClaim()).roleArn(roleArn).policy(policy).build();
            }

            //extract stored policy from bearer
            if (
                token instanceof BearerTokenAuthentication &&
                ((BearerTokenAuthentication) token).getTokenAttributes() != null
            ) {
                @SuppressWarnings("unchecked")
                List<Credentials> credentials = (List<
                        Credentials
                    >) ((BearerTokenAuthentication) token).getTokenAttributes().get("credentials");
                if (credentials != null) {
                    Optional<S3PolicyMapping> p = credentials
                        .stream()
                        .filter(c -> c instanceof S3PolicyMapping)
                        .findFirst()
                        .map(c -> (S3PolicyMapping) c);
                    if (p.isPresent()) {
                        return p.get();
                    }
                }
            }

            //fallback to default
            return S3PolicyMapping
                .builder()
                .claim(properties.getClaim())
                .policy(properties.getPolicy())
                .roleArn(properties.getRoleArn())
                .build();
        }

        return null;
    }
}
