/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

// Copyright 2025 the original author or authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package it.smartcommunitylabdhub.credentials.minio;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.authorization.model.AbstractCredentials;
import java.util.HashMap;
import java.util.Map;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MinioPolicy extends AbstractCredentials {

    @JsonIgnore
    private String claim;

    @JsonIgnore
    private String policy;

    @JsonIgnore
    private String roleArn;

    @Override
    public Map<String, String> toMap() {
        //write claim under requested key
        String prefix = StringUtils.hasText(claim) ? claim : "";
        Map<String, String> map = new HashMap<>();

        if (StringUtils.hasText(policy)) {
            map.put(prefix + "/policy", policy);
        }

        if (StringUtils.hasText(roleArn)) {
            map.put(prefix + "/roleArn", roleArn);
        }

        return map;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((claim == null) ? 0 : claim.hashCode());
        result = prime * result + ((policy == null) ? 0 : policy.hashCode());
        result = prime * result + ((roleArn == null) ? 0 : roleArn.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        MinioPolicy other = (MinioPolicy) obj;

        if (toMap() == null) {
            return other.toMap() == null;
        }

        return toMap().equals(other.toMap());
    }
}
