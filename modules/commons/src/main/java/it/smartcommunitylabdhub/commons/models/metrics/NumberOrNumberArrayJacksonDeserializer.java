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

package it.smartcommunitylabdhub.commons.models.metrics;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class NumberOrNumberArrayJacksonDeserializer extends StdDeserializer<NumberOrNumberArray> {

    public NumberOrNumberArrayJacksonDeserializer() {
        super(NumberOrNumberArrayJacksonDeserializer.class);
    }

    @Override
    public NumberOrNumberArray deserialize(JsonParser jp, DeserializationContext ctxt)
        throws IOException, JacksonException {
        JsonNode node = jp.getCodec().readTree(jp);
        if (node.isArray()) {
            List<Double> values = new ArrayList<>();
            for (JsonNode valueNode : node) {
                values.add(valueNode.asDouble());
            }
            return new NumberOrNumberArray(values);
        }
        return new NumberOrNumberArray(node.asDouble());
    }
    /*private Number getValue(JsonNode valueNode) throws IOException {
		 if(valueNode.isDouble() || valueNode.isFloat()) {
				return Double.valueOf(valueNode.asDouble());
		} else if(valueNode.isLong()) {
			return Long.valueOf(valueNode.asLong());
		} else if(valueNode.isInt()) {
			return Long.valueOf(valueNode.asLong());
		} else
			throw new IOException("type not supported");
	}*/

}
