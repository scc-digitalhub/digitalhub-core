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
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.Writer;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.DumperOptions.FlowStyle;
import org.yaml.snakeyaml.DumperOptions.ScalarStyle;

public class YamlMapperFactory {

    public static final int DEFAULT_INDENT_LEVEL = 4;

    public static ObjectMapper yamlObjectMapper() {
        //        YAMLFactory factory = new YAMLFactory()
        //                .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
        //                .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, true);

        YAMLFactory factory = yamlFactory();
        ObjectMapper yamlObjectMapper = new ObjectMapper(factory);
        yamlObjectMapper.registerModule(new JavaTimeModule());
        // yamlObjectMapper.registerModule(jsonMixinModule());
        yamlObjectMapper.setSerializationInclusion(Include.NON_EMPTY);
        yamlObjectMapper.configure(MapperFeature.USE_GETTERS_AS_SETTERS, false);
        return yamlObjectMapper;
    }

    //    @Bean
    public static YAMLFactory yamlFactory() {
        class CustomYAMLFactory extends YAMLFactory {

            @Override
            protected YAMLGenerator _createGenerator(Writer out, IOContext ctxt) throws IOException {
                int feats = _yamlGeneratorFeatures;
                return yamlGenerator(ctxt, _generatorFeatures, feats, _objectCodec, out, _version);
            }
        }

        return new CustomYAMLFactory()
            .configure(YAMLGenerator.Feature.WRITE_DOC_START_MARKER, false)
            .configure(YAMLGenerator.Feature.MINIMIZE_QUOTES, false)
            .configure(YAMLGenerator.Feature.LITERAL_BLOCK_STYLE, true)
            .configure(YAMLGenerator.Feature.USE_NATIVE_TYPE_ID, false);
    }

    private static YAMLGenerator yamlGenerator(
        IOContext ctxt,
        int jsonFeatures,
        int yamlFeatures,
        ObjectCodec codec,
        Writer out,
        org.yaml.snakeyaml.DumperOptions.Version version
    ) throws IOException {
        class MyYAMLGenerator extends YAMLGenerator {

            public MyYAMLGenerator(
                IOContext ctxt,
                int jsonFeatures,
                int yamlFeatures,
                ObjectCodec codec,
                Writer out,
                org.yaml.snakeyaml.DumperOptions.Version version
            ) throws IOException {
                super(ctxt, jsonFeatures, yamlFeatures, null, codec, out, version);
            }

            @Override
            protected DumperOptions buildDumperOptions(
                int jsonFeatures,
                int yamlFeatures,
                org.yaml.snakeyaml.DumperOptions.Version version
            ) {
                DumperOptions opt = super.buildDumperOptions(jsonFeatures, yamlFeatures, version);
                // override opts
                opt.setDefaultScalarStyle(ScalarStyle.LITERAL);
                opt.setDefaultFlowStyle(FlowStyle.BLOCK);
                opt.setIndicatorIndent(2);
                opt.setIndent(DEFAULT_INDENT_LEVEL);
                opt.setPrettyFlow(true);
                opt.setCanonical(false);
                return opt;
            }
        }

        return new MyYAMLGenerator(ctxt, jsonFeatures, yamlFeatures, codec, out, version);
    }

    private YamlMapperFactory() {}
}
