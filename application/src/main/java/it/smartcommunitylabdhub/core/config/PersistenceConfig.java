/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.core.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import it.smartcommunitylabdhub.core.lifecycle.JdbcDetachedRegistry;
import it.smartcommunitylabdhub.core.repositories.ResolvableTypeCacheResolver;
import it.smartcommunitylabdhub.lifecycle.detached.DetachedOperations;
import it.smartcommunitylabdhub.lifecycle.locks.EntityLockManager;
import it.smartcommunitylabdhub.lifecycle.locks.LocalEntityLockManager;
import java.time.Duration;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.CacheResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@Order(2)
public class PersistenceConfig {

    @Value("${spring.cache.caffeine.spec}")
    private String caffeineSpec;

    @Bean
    EntityLockManager entityLockManager() {
        //single replica: share the local turns with any component not wired via spring
        return LocalEntityLockManager.shared();
    }

    @Bean
    DetachedOperations detachedOperations(
        DataSource dataSource,
        @Value("${lifecycle.detached.timeout:12h}") Duration timeout,
        @Value("${lifecycle.detached.base-delay:1s}") Duration baseDelay,
        @Value("${lifecycle.detached.max-delay:1h}") Duration maxDelay,
        @Value("${lifecycle.detached.check-interval:1m}") Duration checkInterval
    ) {
        //persistent ledger: after a restart, operations left without execution move their object to ERROR
        DetachedOperations operations = new DetachedOperations(new JdbcDetachedRegistry(dataSource));
        operations.setTimeout(timeout);
        operations.setBaseDelay(baseDelay);
        operations.setMaxDelay(maxDelay);
        operations.setCheckInterval(checkInterval);
        return operations;
    }

    @Bean(name = "resolvableTypeCacheResolver")
    CacheResolver resolvableTypeCacheResolver(CacheManager cacheManager) {
        return new ResolvableTypeCacheResolver(cacheManager);
    }

    @Bean
    CacheManager cacheManager() {

        if (caffeineSpec == null || caffeineSpec.isBlank()) {
            return new ConcurrentMapCacheManager();
        }

        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.from(caffeineSpec));
        return manager;
    }
}
