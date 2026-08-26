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

package it.smartcommunitylabdhub.trinodb;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.infrastructure.AbstractConfiguration;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class TrinoDbConfiguration extends AbstractConfiguration {

    @JsonProperty("trino_host")
    private String host;

    @JsonProperty("trino_port")
    private Integer port;

    @JsonProperty("trino_scheme")
    private String scheme;

    @JsonProperty("trino_catalog")
    private String catalog;

    @JsonIgnore
    private String url;

    @JsonProperty("trino_url")
    public String getTrinoUrl() {
        if (StringUtils.hasText(url)) {
            return url;
        }

        if (!StringUtils.hasText(scheme) || !StringUtils.hasText(host)) {
            return null;
        }

        return port != null ? String.format("%s://%s:%d", scheme, host, port) : String.format("%s://%s", scheme, host);
    }
}
