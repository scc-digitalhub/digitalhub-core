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

package it.smartcommunitylabdhub.commons.jackson;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.smartcommunitylabdhub.commons.jackson.mixins.ConcreteSpecMixin;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;

//TODO refactor into a factory ( create method to retrieve all objects mapper)
public class JacksonMapper {

    public static final ObjectMapper CUSTOM_OBJECT_MAPPER = new ObjectMapper();
    public static final TypeReference<HashMap<String, Object>> typeRef = new TypeReference<>() {};
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    public static final ObjectMapper CBOR_OBJECT_MAPPER = new ObjectMapper(new CBORFactory());
    public static final ObjectMapper YAML_OBJECT_MAPPER = new ObjectMapper(YamlMapperFactory.yamlFactory());

    static {
        OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    static {
        // Configure the ObjectMapper to not fail on unknown properties
        CUSTOM_OBJECT_MAPPER.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        CUSTOM_OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CUSTOM_OBJECT_MAPPER.setSerializationInclusion(Include.NON_EMPTY);
        CUSTOM_OBJECT_MAPPER.registerModule(new JavaTimeModule());
        CUSTOM_OBJECT_MAPPER.addMixIn(BaseSpec.class, ConcreteSpecMixin.class);
    }

    // Register mixin for cbor to map deserialization.
    static {
        CBOR_OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        CBOR_OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    static {
        YAML_OBJECT_MAPPER.registerModule(new JavaTimeModule());
        YAML_OBJECT_MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        YAML_OBJECT_MAPPER.setSerializationInclusion(Include.NON_EMPTY);
        YAML_OBJECT_MAPPER.configure(MapperFeature.USE_GETTERS_AS_SETTERS, false);
    }

    public static JavaType extractJavaType(Class<?> clazz) {
        // resolve generics type via subclass trick
        return CUSTOM_OBJECT_MAPPER.getTypeFactory().constructSimpleType(clazz, null);
    }

    public static ObjectWriter getCustomObjectMapperWrite() {
        return CUSTOM_OBJECT_MAPPER.writer();
    }

    public static ObjectReader getCustomObjectMapperReader() {
        return CUSTOM_OBJECT_MAPPER.reader();
    }

    public static <T extends Serializable> T deepClone(T object, Class<T> clazz) throws IOException {
        return CUSTOM_OBJECT_MAPPER.readValue(CUSTOM_OBJECT_MAPPER.writeValueAsBytes(object), clazz);
    }

    private JacksonMapper() {}
}
