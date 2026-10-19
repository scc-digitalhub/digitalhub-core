/*
 * Copyright 2026 the original author or authors.
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
 */

package it.smartcommunitylabdhub.security.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.models.function.Function;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Predefined security analysis function definition, loaded from yaml.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class SecurityAnalyzer {

    private String name;
    private String description;

    //optional script resource (relative to the definition), injected as base64 into function.spec.source
    private String script;

    private EntityTemplate function;
    private EntityTemplate task;
    private EntityTemplate run;
    private Filter filter;

    public boolean matches(Function target) {
        return target != null && (filter == null || filter.matches(target));
    }

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Filter {

        @JsonProperty("spec_fields")
        private List<String> specFields = List.of();

        private List<String> kinds = List.of();

        public boolean matches(Function target) {
            if (kinds != null && !kinds.isEmpty() && !kinds.contains(target.getKind())) {
                return false;
            }
            return specFields == null || specFields.stream().allMatch(field -> {
                Object value = target.getSpec() != null ? target.getSpec().get(field) : null;
                if (value instanceof String text) {
                    return !text.isBlank();
                }
                if (value instanceof Map<?, ?> map) {
                    return !map.isEmpty();
                }
                if (value instanceof Collection<?> collection) {
                    return !collection.isEmpty();
                }
                return value != null;
            });
        }
    }

    //run spec field receiving the analysis context (target function, image, source, output path)
    @JsonProperty("parameters_field")
    private String parametersField = "parameters";

    @Getter
    @Setter
    @NoArgsConstructor
    @ToString
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EntityTemplate {

        private String kind;
        private HashMap<String, Serializable> spec = new HashMap<>();

        public Map<String, Serializable> getSpec() {
            return spec != null ? spec : new HashMap<>();
        }
    }
}
