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

package it.smartcommunitylabdhub.s3.config;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
@ConfigurationProperties(prefix = "credentials.provider.s3", ignoreUnknownFields = true)
public class S3Properties {

    private Boolean enable;

    private String endpoint;
    private String region = "us-east-1";
    private String bucket;
    private String signatureVersion = "s3v4";
    private Boolean pathStyleAccess;

    private String accessKey;
    private String secretKey;

    private String claim;
    private String policy;
    private String roleArn;

    public boolean isStaticProviderEnabled() {
        return (
            ((enable != null && enable.booleanValue()) &&
                (endpoint != null && !endpoint.isBlank()) &&
                (accessKey != null && !accessKey.isBlank()) &&
                (secretKey != null && !secretKey.isBlank())) &&
            !isAssumeRoleProviderEnabled()
        );
    }

    public boolean isAssumeRoleProviderEnabled() {
        return (
            (enable != null && enable.booleanValue()) &&
            (endpoint != null && !endpoint.isBlank()) &&
            (accessKey != null && !accessKey.isBlank()) &&
            (secretKey != null && !secretKey.isBlank()) &&
            // either static roleArn or static policy or claim mapping
            // must be provided to enable assume role provider
            ((roleArn != null && !roleArn.isBlank()) ||
                (policy != null && !policy.isBlank()) ||
                (claim != null && !claim.isBlank()))
        );
    }

    public String getBucket() {
        return bucket != null && !bucket.isBlank() ? bucket : null;
    }
}
