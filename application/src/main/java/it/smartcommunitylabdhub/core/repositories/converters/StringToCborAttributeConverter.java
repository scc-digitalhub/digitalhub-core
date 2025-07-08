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
import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("cborStringConverter")
@Converter(autoApply = false)
public class StringToCborAttributeConverter implements AttributeConverter<String, byte[]> {

    private static final ObjectMapper mapper = JacksonMapper.CBOR_OBJECT_MAPPER;

    @Override
    public byte[] convertToDatabaseColumn(String value) {
        byte[] bytes = null;
        if (value != null) {
            try {
                bytes = mapper.writeValueAsBytes(value);
            } catch (JsonProcessingException e) {
                log.error("error converting string: {}", e.getMessage());
            }
        }

        return bytes;
    }

    @Override
    public String convertToEntityAttribute(byte[] bytes) {
        String value = null;

        if (bytes != null) {
            try {
                value = mapper.readValue(bytes, String.class);
            } catch (IOException e) {
                log.error("error reading string from bytes: {}", e.getMessage());
            }
        }
        return value;
    }
}
