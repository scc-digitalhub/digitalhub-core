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
import io.kubernetes.client.custom.Quantity;
import java.io.IOException;
import java.util.Map;

public class QuantityJacksonDeserializer extends StdDeserializer<Quantity> {

    public QuantityJacksonDeserializer() {
        this(null);
    }

    public QuantityJacksonDeserializer(Class<Quantity> t) {
        super(Quantity.class);
    }

    @Override
    public Quantity deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JacksonException {
        Object value = p.readValueAs(Object.class); // Read the value as an Object (can be String or Map)

        if (value instanceof String) {
            return Quantity.fromString((String) value);
        } else if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Object number = map.get("number");
            Object format = map.get("format");

            if (number instanceof Number) {
                double num = ((Number) number).doubleValue();
                if ("DECIMAL_SI".equals(format)) {
                    return new Quantity((int) (num * 1000) + "m");
                }
                return new Quantity(String.valueOf(num));
            }
        }
        throw new IOException("Invalid Quantity format: " + value);
    }
}
