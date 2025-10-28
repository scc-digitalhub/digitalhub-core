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

package it.smartcommunitylabdhub.files.provider;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.authorization.model.AbstractCredentials;
import java.time.ZonedDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class S3Credentials extends AbstractCredentials {

    //NOTE: align names with
    //ref https://docs.aws.amazon.com/sdkref/latest/guide/settings-reference.html#EVarSettings
    //
    @JsonProperty("aws_access_key_id")
    private String accessKey;

    @JsonProperty("aws_secret_access_key")
    private String secretKey;

    @JsonProperty("aws_session_token")
    private String sessionToken;

    @JsonProperty("aws_credentials_expiration")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH':'mm':'ss'Z'")
    private ZonedDateTime expiration;

    @Override
    public void eraseCredentials() {
        //clear credentials
        this.accessKey = null;
        this.secretKey = null;
        this.sessionToken = null;
    }
}
