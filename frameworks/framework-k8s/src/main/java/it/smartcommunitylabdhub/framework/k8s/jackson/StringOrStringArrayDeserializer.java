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

package it.smartcommunitylabdhub.framework.k8s.jackson;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;

/**
 * Deserializer that handles both String and String[] values.
 * If a String is provided, it will be wrapped in a single-element array.
 * If a String[] is provided, it will be used as-is.
 */
public class StringOrStringArrayDeserializer extends StdDeserializer<String[]> {

    public StringOrStringArrayDeserializer() {
        this(null);
    }

    public StringOrStringArrayDeserializer(Class<String[]> t) {
        super(String[].class);
    }

    @Override
    public String[] deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        Object value = p.readValueAs(Object.class);

        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            // Single string -> wrap in array
            return new String[] { (String) value };
        } else if (value instanceof String[]) {
            // Already an array
            return (String[]) value;
        } else if (value instanceof java.util.List) {
            // List -> convert to array
            java.util.List<?> list = (java.util.List<?>) value;
            return list.stream().map(Object::toString).toArray(String[]::new);
        } else {
            // Try to convert to string and wrap in array
            return new String[] { value.toString() };
        }
    }
}
