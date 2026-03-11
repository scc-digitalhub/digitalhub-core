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

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class S3BaseProvider {

    protected final S3Properties properties;
    protected final S3Config config;

    protected S3BaseProvider(S3Properties s3Properties) {
        Assert.notNull(s3Properties, "s3 properties are required");

        if (log.isTraceEnabled()) {
            log.trace("s3 properties: {}", s3Properties);
        }
        this.properties = s3Properties;

        //build config
        S3Config.S3ConfigBuilder builder = S3Config
            .builder()
            .endpoint(properties.getEndpoint())
            .bucket(properties.getBucket())
            .region(StringUtils.hasText(properties.getRegion()) ? properties.getRegion() : "us-east-1")
            .signatureVersion(
                StringUtils.hasText(properties.getSignatureVersion()) ? properties.getSignatureVersion() : "s3v4"
            )
            .pathStyle(properties.getPathStyleAccess() != null ? properties.getPathStyleAccess() : Boolean.FALSE);

        this.config = builder.build();

        if (log.isTraceEnabled()) {
            log.trace("config: {}", config.toJson());
        }
    }
}
