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

package it.smartcommunitylabdhub.core.repositories.converters;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("cborMapConverter")
@Converter(autoApply = false)
public class MapToCborAttributeConverter implements AttributeConverter<Map<String, Serializable>, byte[]> {

    private static final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;
    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    @Override
    public byte[] convertToDatabaseColumn(Map<String, Serializable> map) {
        byte[] value = null;
        if (map != null) {
            try {
                value = mapper.writeValueAsBytes(map);
            } catch (JsonProcessingException e) {
                log.error("error converting map: {}", e.getMessage());
            }
        }

        return value;
    }

    @Override
    public Map<String, Serializable> convertToEntityAttribute(byte[] source) {
        Map<String, Serializable> value = null;

        if (source != null) {
            try {
                value = mapper.readValue(source, typeRef);
            } catch (IOException e) {
                log.error("error reading map from bytes: {}", e.getMessage());
            }
        }
        return value;
    }
}
