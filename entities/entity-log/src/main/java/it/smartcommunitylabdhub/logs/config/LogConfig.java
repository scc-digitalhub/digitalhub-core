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

package it.smartcommunitylabdhub.logs.config;

import it.smartcommunitylabdhub.core.repositories.BaseEntityRepositoryImpl;
import it.smartcommunitylabdhub.core.repositories.SearchableEntityRepository;
import it.smartcommunitylabdhub.logs.Log;
import it.smartcommunitylabdhub.logs.persistence.LogEntity;
import it.smartcommunitylabdhub.logs.persistence.LogRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;

@Configuration
public class LogConfig {

    @Bean
    SearchableEntityRepository<LogEntity, Log> logSearchableEntityRepository(
        LogRepository repository,
        Converter<Log, LogEntity> entityBuilder,
        Converter<LogEntity, Log> dtoBuilder
    ) {
        return new BaseEntityRepositoryImpl<>(repository, entityBuilder, dtoBuilder) {};
    }
}
