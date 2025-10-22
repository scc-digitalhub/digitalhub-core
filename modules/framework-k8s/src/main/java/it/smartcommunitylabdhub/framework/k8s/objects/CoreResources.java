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

package it.smartcommunitylabdhub.framework.k8s.objects;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonSetter;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoreResources implements Serializable {

    private List<CoreResourceDefinition> requests;
    private List<CoreResourceDefinition> limits;

    @JsonGetter("requests")
    public Map<String, String> getRequestsAsMap() {
        return requests == null
            ? Map.of()
            : requests
                .stream()
                .collect(Collectors.toMap(CoreResourceDefinition::getKey, CoreResourceDefinition::getValue));
    }

    @JsonSetter("requests")
    public void setRequestsFromMap(Map<String, String> reqs) {
        if (reqs != null) {
            this.requests =
                reqs.entrySet().stream().map(e -> new CoreResourceDefinition(e.getKey(), e.getValue())).toList();
        }
    }

    @JsonGetter("limits")
    public Map<String, String> getLimitsAsMap() {
        return limits == null
            ? Map.of()
            : limits
                .stream()
                .collect(Collectors.toMap(CoreResourceDefinition::getKey, CoreResourceDefinition::getValue));
    }

    @JsonSetter("limits")
    public void setLimitsFromMap(Map<String, String> lims) {
        if (lims != null) {
            this.limits =
                lims.entrySet().stream().map(e -> new CoreResourceDefinition(e.getKey(), e.getValue())).toList();
        }
    }
}
