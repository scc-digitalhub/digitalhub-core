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

package it.smartcommunitylabdhub.authorization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.web.util.UriComponentsBuilder;

@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AuthorizationResponse {

    private String issuer;
    private String code;
    private String state;
    private String redirectUrl;

    public String buildRedirectUri() {
        if (redirectUrl == null) {
            return null;
        }

        return UriComponentsBuilder
            .fromUriString(redirectUrl)
            .queryParam("code", code)
            .queryParam("state", state)
            .queryParam("issuer", issuer)
            .toUriString();
    }
}
