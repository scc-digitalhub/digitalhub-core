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
import java.io.Serializable;
import java.util.HashMap;
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
