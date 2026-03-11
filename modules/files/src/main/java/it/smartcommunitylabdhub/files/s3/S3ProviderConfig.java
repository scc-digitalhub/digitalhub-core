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

import it.smartcommunitylabdhub.files.service.FilesService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@Configuration
@EnableConfigurationProperties({ S3Properties.class })
public class S3ProviderConfig {

    @Bean
    @ConditionalOnProperty(name = "credentials.provider.s3.enable", havingValue = "true", matchIfMissing = false)
    S3Provider s3Provider(FilesService filesService, S3Properties s3Properties) {
        return new S3Provider(filesService, s3Properties);
    }

    @Bean
    @ConditionalOnProperty(name = "credentials.provider.s3.enable", havingValue = "true", matchIfMissing = false)
    S3StaticProvider s3StaticProvider(S3Properties s3Properties) {
        return new S3StaticProvider(s3Properties);
    }

    @Bean
    @ConditionalOnProperty(name = "credentials.provider.s3.enable", havingValue = "true", matchIfMissing = false)
    S3AssumeRoleProvider s3AssumeRoleProvider(S3Properties s3Properties) {
        return new S3AssumeRoleProvider(s3Properties);
    }
}
