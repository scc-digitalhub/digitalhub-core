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

package it.smartcommunitylabdhub.s3;

import it.smartcommunitylabdhub.commons.infrastructure.ConfigurationProvider;
import it.smartcommunitylabdhub.files.service.FilesService;
import it.smartcommunitylabdhub.s3.base.S3BaseProvider;
import it.smartcommunitylabdhub.s3.config.S3Config;
import it.smartcommunitylabdhub.s3.config.S3Properties;
import it.smartcommunitylabdhub.s3.files.S3FilesStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public class S3Provider extends S3BaseProvider implements ConfigurationProvider, InitializingBean {

    private final FilesService filesService;

    public S3Provider(FilesService filesService, S3Properties s3Properties) {
        super(s3Properties);
        Assert.notNull(filesService, "files service is required");

        this.filesService = filesService;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("Build configuration for provider...");

        if (config != null) {
            //build a file store
            S3FilesStore store = new S3FilesStore(config);

            //register with service
            if (StringUtils.hasText(properties.getBucket())) {
                filesService.registerStore("s3://" + properties.getBucket(), store);
                filesService.registerStore("zip+s3://" + properties.getBucket(), store);
            } else {
                filesService.registerStore("s3://", store);
                filesService.registerStore("zip+s3://", store);
            }
        }
    }

    @Override
    public S3Config getConfig() {
        return config;
    }
}
