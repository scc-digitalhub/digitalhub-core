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

package it.smartcommunitylabdhub.core.repositories;

import java.util.Collection;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.AbstractCacheResolver;
import org.springframework.cache.interceptor.CacheOperationInvocationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;

@Slf4j
public class ResolvableTypeCacheResolver extends AbstractCacheResolver {

    public ResolvableTypeCacheResolver() {}

    public ResolvableTypeCacheResolver(CacheManager cacheManager) {
        super(cacheManager);
    }

    @Override
    protected Collection<String> getCacheNames(CacheOperationInvocationContext<?> context) {
        //check if repository class uses generics, and pick one of the generic types as cache name
        //if target implements ResolvableTypeProvider, use the resolvable type to get the generic type
        Object target = context.getTarget();
        if (target instanceof ResolvableTypeProvider resolvableTypeProvider) {
            ResolvableType resolvableType = resolvableTypeProvider.getResolvableType();
            if (resolvableType != null) {
                Class<?> clazz = resolvableType.resolve();
                if (clazz != null) {
                    //use simple name as cache prefix, and append all names passed by the context
                    String cacheName = clazz.getSimpleName();
                    if (log.isTraceEnabled()) {
                        log.trace("Resolved cache name {} for repository {}", cacheName, clazz.getName());
                    }

                    Set<String> names = context.getOperation().getCacheNames() != null
                        ? context.getOperation().getCacheNames()
                        : Set.of();
                    return names.stream().map(name -> cacheName + "." + name).toList();
                }
            }
        }

        return context.getOperation().getCacheNames();
    }
}
