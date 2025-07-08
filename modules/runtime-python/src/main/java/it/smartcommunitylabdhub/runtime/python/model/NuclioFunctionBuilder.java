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

package it.smartcommunitylabdhub.runtime.python.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class NuclioFunctionBuilder {

    private static ObjectMapper mapper = JacksonMapper.YAML_OBJECT_MAPPER;

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};
    private static final String template = "spec:";

    public static HashMap<String, Serializable> build(NuclioFunctionSpec fn) {
        try {
            //read template
            HashMap<String, Serializable> spec = mapper.readValue(template, typeRef);

            //fill

            spec.put("runtime", fn.getRuntime());
            spec.put("handler", fn.getHandler());
            spec.put("minReplicas", fn.getMinReplicas() != null ? fn.getMinReplicas() : 1);
            spec.put("maxReplicas", fn.getMaxReplicas() != null ? fn.getMaxReplicas() : 1);

            HashMap<String, Serializable> triggers = fn.getTriggers() != null
                ? new HashMap<String, Serializable>(fn.getTriggers())
                : new HashMap<>();

            //build default trigger if empty
            if (triggers.isEmpty()) {
                HashMap<String, Serializable> attributes = new HashMap<>(Map.of("event", fn.getEvent()));
                HashMap<String, Serializable> job = new HashMap<>(Map.of("kind", "job", "attributes", attributes));
                triggers.put("job", job);
            }

            spec.put("triggers", triggers);

            HashMap<String, Serializable> map = new HashMap<>();
            map.put("apiVersion", "nuclio.io/v1beta1");
            map.put("kind", "NuclioFunction");
            map.put("spec", spec);

            return map;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    public static String write(NuclioFunctionSpec spec) {
        Serializable value = build(spec);
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
    }

    private NuclioFunctionBuilder() {}
}
