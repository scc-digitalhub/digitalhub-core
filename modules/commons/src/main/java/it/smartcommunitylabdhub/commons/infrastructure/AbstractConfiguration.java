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

package it.smartcommunitylabdhub.commons.infrastructure;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public abstract class AbstractConfiguration implements Configuration {

    @JsonIgnore
    protected static final ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }

    @JsonIgnore
    protected static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    @Override
    public Map<String, Serializable> toMap() {
        return mapper.convertValue(this, typeRef);
    }

    @Override
    public String toJson() {
        try {
            return mapper.writeValueAsString(toMap());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("error with serialization :" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<String, String> toStringMap() {
        try {
            Map<String, Serializable> map = toMap();
            Map<String, String> stringMap = new HashMap<>();
            for (Map.Entry<String, Serializable> entry : map.entrySet()) {
                //infer type manually because jackson can't due to type erasure
                //we assume every collection is a string array
                Serializable value = entry.getValue();
                String stringValue = null;
                if (value instanceof Collection && !((Collection<?>) value).isEmpty()) {
                    //check first element
                    Object first = ((Collection<?>) value).iterator().next();
                    if (first instanceof String) {
                        stringValue = StringUtils.collectionToCommaDelimitedString((Collection<String>) value);
                    } else {
                        //serialize as json
                        stringValue = mapper.writeValueAsString(value);
                    }
                } else if (value instanceof String) {
                    stringValue = (String) value;
                } else {
                    stringValue = mapper.writeValueAsString(value);
                }

                stringMap.put(entry.getKey(), stringValue);
            }
            return stringMap;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("error with serialization :" + e.getMessage());
        }
    }
}
