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

package it.smartcommunitylabdhub.authorization.providers;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.nimbusds.jwt.SignedJWT;
import it.smartcommunitylabdhub.authorization.model.AbstractCredentials;
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
public class AccessCredentials extends AbstractCredentials {

    @JsonProperty("dhcore_access_token")
    private SignedJWT accessToken;

    @JsonProperty("dhcore_id_token")
    private SignedJWT idToken;

    @JsonProperty("dhcore_refresh_token")
    private String refreshToken;

    @JsonProperty("dhcore_expires_in")
    private Integer expiration;

    @JsonProperty("dhcore_client_id")
    private String clientId;

    @JsonProperty("dhcore_issuer")
    private String issuer;

    @JsonGetter("dhcore_access_token")
    public String getAccessTokenAsString() {
        return accessToken != null ? accessToken.serialize() : null;
    }

    @JsonGetter("dhcore_id_token")
    public String getIdTokenAsString() {
        return idToken != null ? idToken.serialize() : null;
    }
}
