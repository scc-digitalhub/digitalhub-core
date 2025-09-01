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

package it.smartcommunitylabdhub.core.triggers.service;

import ch.qos.logback.core.encoder.JsonEscapeUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.MustacheFactory;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.services.TemplateProcessor;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MustacheTemplateProcessor implements TemplateProcessor {

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    private final MustacheFactory factory;

    public MustacheTemplateProcessor() {
        NoEncodingMustacheFactory f = new NoEncodingMustacheFactory();
        f.setObjectHandler(new JsonObjectHandler());

        this.factory = f;
    }

    @Override
    public Map<String, Serializable> process(Map<String, Serializable> template, Map<String, Serializable> data)
        throws IOException {
        if (template == null || template.isEmpty() || data == null || data.isEmpty()) {
            return template;
        }

        //convert to json to build template
        String json = JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(template);

        //process template
        StringWriter writer = new StringWriter();
        Mustache mustache = factory.compile(new StringReader(json), "template");
        mustache.execute(writer, data);
        writer.flush();

        //convert back to map
        String result = writer.toString();
        Map<String, Serializable> map = JacksonMapper.CUSTOM_OBJECT_MAPPER.readValue(result, typeRef);

        return map;
    }

    private static class NoEncodingMustacheFactory extends DefaultMustacheFactory {

        @Override
        public void encode(String value, Writer writer) {
            try {
                //write string as is, no encoding
                //this is needed to avoid encoding of json strings
                writer.write(value);
            } catch (IOException e) {
                throw new MustacheException(e);
            }
        }
    }

    class JsonObjectHandler extends com.github.mustachejava.reflect.SimpleObjectHandler {

        // @Override
        // public Object read(String key, String value) {
        //     return JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(value, Object.class);
        // }
        @Override
        public String stringify(Object object) {
            if (object == null) {
                return null;
            }

            if (object instanceof BaseDTO || object instanceof Map || object instanceof Collection) {
                // convert to json
                try {
                    return JsonEscapeUtil.jsonEscapeString(
                        JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(object)
                    );
                } catch (IOException e) {
                    log.error("error serializing object: {}", e.getMessage());
                    return null;
                }
            }

            //return bare string representation
            return object.toString();
        }
    }
}
