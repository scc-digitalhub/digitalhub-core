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

package it.smartcommunitylabdhub.framework.k8s.model;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ContextRef implements Serializable {

    private String destination;
    private String protocol;
    private String source;

    public String toCsv() {
        return StringUtils.hasText(destination)
            ? String.join(",", List.of(protocol, destination, source))
            : String.join(",", List.of(protocol, "", source));
    }

    public static ContextRef from(String source) {
        if (!StringUtils.hasText(source)) {
            return null;
        }

        try {
            //parse as URI to extract protocol
            UriComponents uri = UriComponentsBuilder.fromUriString(source).build();
            String scheme = uri.getScheme();

            return ContextRef.builder().protocol(scheme).source(source).build();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((destination == null) ? 0 : destination.hashCode());
        result = prime * result + ((protocol == null) ? 0 : protocol.hashCode());
        result = prime * result + ((source == null) ? 0 : source.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        ContextRef other = (ContextRef) obj;
        if (destination == null) {
            if (other.destination != null) return false;
        } else if (!destination.equals(other.destination)) return false;
        if (protocol == null) {
            if (other.protocol != null) return false;
        } else if (!protocol.equals(other.protocol)) return false;
        if (source == null) {
            if (other.source != null) return false;
        } else if (!source.equals(other.source)) return false;
        return true;
    }
}
