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

package it.smartcommunitylabdhub.files.config;

import it.smartcommunitylabdhub.commons.config.YamlPropertySourceFactory;
import it.smartcommunitylabdhub.files.service.FilesInfoService;
import it.smartcommunitylabdhub.files.service.FilesInfoServiceImpl;
import it.smartcommunitylabdhub.files.service.FilesService;
import it.smartcommunitylabdhub.files.service.ProjectPathResolver;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@PropertySource(value = "classpath:/component-files.yml", factory = YamlPropertySourceFactory.class)
@EnableConfigurationProperties({ FilesProperties.class })
public class FilesConfig {

    @Bean
    FilesService filesService(FilesProperties filesProperties) {
        return new FilesService(filesProperties);
    }

    /**
     * Default project base path: default store + "/<project>/". Project aware resolvers override it
     * as @Primary.
     */
    @Bean
    ProjectPathResolver defaultProjectPathResolver(FilesService filesService) {
        return project -> filesService.getDefaultStore(null) + "/" + project + "/";
    }

    @Bean
    FilesInfoService filesInfoSeervice(FilesProperties filesProperties) {
        return new FilesInfoServiceImpl(filesProperties);
    }
}
