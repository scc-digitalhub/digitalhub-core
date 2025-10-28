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

package it.smartcommunitylabdhub.credentials.db;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.smartcommunitylabdhub.authorization.model.UserAuthentication;
import it.smartcommunitylabdhub.authorization.services.CredentialsProvider;
import it.smartcommunitylabdhub.authorization.services.JwtTokenService;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.Configuration;
import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.commons.infrastructure.Credentials;
import it.smartcommunitylabdhub.credentials.db.DbConfig.DbConfigBuilder;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
@Slf4j
public class DbCredentialsProvider implements CredentialsProvider, ConfigurationProvider, InitializingBean {

    private static final int DEFAULT_DURATION = 24 * 3600; //24 hour
    private static final int MIN_DURATION = 300; //5 min
    private static final int SKEW_DURATION = 60; //1 min

    @Value("${credentials.provider.db.user}")
    private String user;

    @Value("${credentials.provider.db.password}")
    private String secret;

    @Value("${credentials.provider.db.platform}")
    private String platform;

    @Value("${credentials.provider.db.host}")
    private String host;

    @Value("${credentials.provider.db.port}")
    private Integer port;

    @Value("${credentials.provider.db.database}")
    private String database;

    @Value("${credentials.provider.db.claim}")
    private String claim;

    @Value("${credentials.provider.db.role}")
    private String defaultRole;

    private int duration = DEFAULT_DURATION;
    private int accessTokenDuration = JwtTokenService.DEFAULT_ACCESS_TOKEN_DURATION;

    @Value("${credentials.provider.db.endpoint}")
    private String endpointUrl;

    @Value("${credentials.provider.db.enable}")
    private Boolean enabled;

    private final RestTemplate restTemplate;

    private DbConfig config;

    // cache credentials for up to DURATION
    LoadingCache<Pair<String, String>, Pair<DbCredentials, Instant>> cache;

    public DbCredentialsProvider() {
        //TODO define a property bean
        restTemplate = new RestTemplate();

        //register message converter for form-encoded requests
        List<HttpMessageConverter<?>> converters = List.of(
            new MappingJackson2HttpMessageConverter(),
            new FormHttpMessageConverter()
        );
        restTemplate.setMessageConverters(converters);
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
            //check config
            enabled = StringUtils.hasText(endpointUrl);
        }

        DbConfigBuilder builder = DbConfig.builder().platform(platform).host(host).port(port).database(database);

        this.config = builder.build();

        if (log.isTraceEnabled()) {
            log.trace("config: {}", config.toJson());
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
                    new CacheLoader<Pair<String, String>, Pair<DbCredentials, Instant>>() {
                        @Override
                        public Pair<DbCredentials, Instant> load(@Nonnull Pair<String, String> key) throws Exception {
                            log.debug("load credentials for {} role {}", key.getFirst(), key.getSecond());
                            return generate(key.getFirst(), key.getSecond());
                        }
                    }
                );
    }

    private Pair<DbCredentials, Instant> generate(@NotNull String username, @NotNull String role)
        throws StoreException {
        log.debug("generate credentials for user authentication {} via STS service", username);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            if (StringUtils.hasText(user) && StringUtils.hasText(secret)) {
                //basic auth is required
                byte[] basicAuth = Base64
                    .getEncoder()
                    .encode((user + ":" + secret).getBytes(Charset.forName("US-ASCII")));
                headers.add("Authorization", "Basic " + new String(basicAuth));
            }

            //we need to convert request to MultiValueMap otherwise restTemplate won't handle a form-urlrequest...
            // TokenRequest request = TokenRequest
            // .builder()
            // .username(username)
            // .database(database)
            // .roles(Collections.singleton(role))
            // .duration(duration)
            // .build();
            MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
            map.add("username", username);
            map.add("database", database);
            map.add("roles", role);
            map.add("duration", String.valueOf(duration));

            if (log.isTraceEnabled()) {
                log.trace("request: {}", map);
            }

            //call web sts
            String url = endpointUrl + "/sts/web";

            log.debug("call STS endpoint for exchange {}", url);
            ResponseEntity<TokenResponse> response = restTemplate.postForEntity(
                url,
                new HttpEntity<>(map, headers),
                TokenResponse.class
            );

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                //error, no recovery
                log.error("Error with provider, status code {}", response.getStatusCode().toString());
                return null;
            }

            TokenResponse token = response.getBody();
            if (token == null) {
                log.error("Null or invalid token response");
                return null;
            }

            if (log.isTraceEnabled()) {
                log.trace("response: {}", token);
            }
            Instant now = Instant.now();
            Instant expiration = token.getExpiration() != null
                ? now.plusSeconds(token.getExpiration() - SKEW_DURATION)
                : now.plusSeconds(duration - SKEW_DURATION);
            if (expiration.toEpochMilli() - now.toEpochMilli() < MIN_DURATION * 1000) {
                //error, no recovery
                log.error("Error with provider, token duration is too short {}", token.getExpiration());
                return null;
            }

            return Pair.of(
                DbCredentials
                    .builder()
                    // .platform(token.getPlatform())
                    // .host(token.getHost())
                    // .port(token.getPort())
                    .username(token.getUsername())
                    .password(token.getPassword())
                    // .database(token.getDatabase())
                    .build(),
                expiration
            );
        } catch (RestClientException e) {
            //error, no recovery
            log.error("Error with provider {}", e);
            throw new StoreException(e.getMessage());
        }
    }

    @Override
    @Nullable
    public Configuration getConfig() {
        if (Boolean.TRUE.equals(enabled)) {
            return config;
        }

        return null;
    }

    @Override
    public Credentials get(@NotNull UserAuthentication<?> auth) {
        if (Boolean.TRUE.equals(enabled) && cache != null) {
            //we expect a role credentials in context
            DbRole role = Optional
                .ofNullable(auth.getCredentials())
                .map(creds ->
                    creds.stream().filter(DbRole.class::isInstance).map(c -> (DbRole) c).findFirst().orElse(null)
                )
                .orElse(null);

            if (role != null && StringUtils.hasText(role.getRole())) {
                //get from cache
                String username = auth.getName();
                log.debug("get credentials for user authentication {} via STS service", username);

                try {
                    Pair<String, String> k = Pair.of(username, role.getRole());
                    Pair<DbCredentials, Instant> p = cache.get(k);
                    if (p == null) {
                        return null;
                    }

                    //check expiration against access token and refresh if needed
                    if (Instant.now().plusSeconds(accessTokenDuration + MIN_DURATION).isAfter(p.getSecond())) {
                        //refresh
                        log.debug("refresh credentials for user authentication {} via STS service", username);
                        cache.invalidate(k);

                        return cache.get(k).getFirst();
                    } else {
                        return p.getFirst();
                    }
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
        if (Boolean.TRUE.equals(enabled)) {
            //extract a role from jwt tokens
            if (token instanceof JwtAuthenticationToken && StringUtils.hasText(claim)) {
                String role = ((JwtAuthenticationToken) token).getToken().getClaimAsString(claim);
                if (StringUtils.hasText(role)) {
                    return DbRole.builder().claim(claim).role(role).build();
                }
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
                    Optional<DbRole> p = credentials
                        .stream()
                        .filter(c -> c instanceof DbRole)
                        .findFirst()
                        .map(c -> (DbRole) c);
                    if (p.isPresent()) {
                        return p.get();
                    }
                }
            }

            //fallback to default
            if (StringUtils.hasText(defaultRole)) {
                return DbRole.builder().claim(claim).role(defaultRole).build();
            }
        }

        return null;
    }
}
