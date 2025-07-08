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

package it.smartcommunitylabdhub.commons.utils;

import aj.org.objectweb.asm.Type;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.victools.jsonschema.generator.ConfigFunction;
import com.github.victools.jsonschema.generator.FieldScope;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.generator.TypeScope;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationModule;
import com.github.victools.jsonschema.module.jakarta.validation.JakartaValidationOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.jackson.definitions.SerializableDefinitionsModule;
import it.smartcommunitylabdhub.commons.jackson.introspect.JsonSchemaAnnotationIntrospector;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.jar.asm.AnnotationVisitor;
import net.bytebuddy.jar.asm.FieldVisitor;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.OpenedClassReader;

//TODO refactor into a factory
public final class SchemaUtils {

    public static final String FIELDS_PREFIX = "fields.";
    public static final String SPECS_PREFIX = "specs.";
    private static final List<Class<?>> SERIALIZABLE_TYPES = Arrays.asList(
        String.class,
        Number.class,
        Boolean.class,
        Integer.class
    );

    // public static final SchemaGenerator GENERATOR;
    public static final SchemaGeneratorConfigBuilder BUILDER;

    static {
        ObjectMapper schemaMapper = new ObjectMapper()
            .setAnnotationIntrospector(new JsonSchemaAnnotationIntrospector());

        JacksonModule jacksonModule = new JacksonModule(
            JacksonOption.IGNORE_TYPE_INFO_TRANSFORM,
            JacksonOption.RESPECT_JSONPROPERTY_ORDER
            // JacksonOption.INCLUDE_ONLY_JSONPROPERTY_ANNOTATED_METHODS
        );
        JakartaValidationModule jakartaModule = new JakartaValidationModule(
            JakartaValidationOption.NOT_NULLABLE_FIELD_IS_REQUIRED,
            JakartaValidationOption.INCLUDE_PATTERN_EXPRESSIONS
        );
        Swagger2Module swagger2Module = new Swagger2Module();
        SerializableDefinitionsModule serializableModule = new SerializableDefinitionsModule();

        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
            schemaMapper,
            SchemaVersion.DRAFT_2020_12,
            OptionPreset.PLAIN_JSON
        )
            .with(jacksonModule)
            .with(jakartaModule)
            .with(swagger2Module)
            .with(serializableModule)
            //options
            .with(Option.DEFINITIONS_FOR_ALL_OBJECTS)
            .with(Option.MAP_VALUES_AS_ADDITIONAL_PROPERTIES)
            .with(Option.PLAIN_DEFINITION_KEYS)
            .with(Option.ENUM_KEYWORD_FOR_SINGLE_VALUES)
            .with(Option.FLATTENED_ENUMS_FROM_TOSTRING)
            .with(Option.PUBLIC_NONSTATIC_FIELDS);
        //avoid fields without getters (ex. unwrapped fields)
        //DISABLED: breaks records
        // .without(Option.NONPUBLIC_NONSTATIC_FIELDS_WITHOUT_GETTERS);

        //auto-generate titles and descriptions via a fixed naming schema
        ConfigFunction<FieldScope, String> titleFromNameResolver = scope -> {
            if (scope.isFakeContainerItemScope()) {
                return FIELDS_PREFIX + scope.getDeclaredName() + ".item.title";
            }
            return FIELDS_PREFIX + scope.getDeclaredName() + ".title";
        };
        ConfigFunction<FieldScope, String> descriptionFromNameResolver = scope -> {
            if (scope.isFakeContainerItemScope()) {
                return FIELDS_PREFIX + scope.getDeclaredName() + ".item.description";
            }
            return FIELDS_PREFIX + scope.getDeclaredName() + ".description";
        };

        configBuilder
            .forFields()
            .withTitleResolver(titleFromNameResolver)
            .withDescriptionResolver(descriptionFromNameResolver)
            .withDefaultResolver(field -> {
                //set default for enums with single value
                if (Enum.class.isAssignableFrom(field.getType().getErasedType())) {
                    try {
                        @SuppressWarnings({ "rawtypes", "unchecked" })
                        Enum[] ee = ((Class<Enum>) field.getType().getErasedType()).getEnumConstants();
                        if (ee.length == 1) {
                            return ee[0];
                        }
                    } catch (ClassCastException e) {
                        //not really an enum?
                    }
                }

                return null;
            })
            .withIgnoreCheck(field -> {
                return field.getAnnotation(JsonSchemaIgnore.class) != null;
            });

        configBuilder
            .forTypesInGeneral()
            .withTitleResolver(specTypeResolver("title"))
            .withDescriptionResolver(specTypeResolver("description"));

        // GENERATOR = new SchemaGenerator(configBuilder.build());
        BUILDER = configBuilder;
    }

    // public static JsonNode schema(Class<? extends Spec> clazz) {
    //     return GENERATOR.generateSchema(clazz);
    // }

    public static SchemaGenerator generator() {
        //build new from config
        return new SchemaGenerator(BUILDER.build());
    }

    public static <T extends Spec> Class<? extends T> proxy(Class<T> clazz) {
        return new ByteBuddy()
            .redefine(clazz)
            .visit(
                new AsmVisitorWrapper.ForDeclaredFields()
                    .field(
                        //redefine fields marked with ignore
                        ElementMatchers.isAnnotatedWith(JsonSchemaIgnore.class),
                        (instrumentedType, fieldDescription, fieldVisitor) ->
                            new FieldVisitor(OpenedClassReader.ASM_API, fieldVisitor) {
                                @Override
                                public AnnotationVisitor visitAnnotation(String description, boolean visible) {
                                    //remove jsonUnwrapped to resolve issue with unwrapped fields skipping ignore
                                    if (Type.getDescriptor(JsonUnwrapped.class).equals(description)) {
                                        return null;
                                    }

                                    return super.visitAnnotation(description, visible);
                                }
                            }
                    )
            )
            .name(clazz.getName() + "Proxy")
            .make()
            .load(clazz.getClassLoader())
            .getLoaded();
    }

    private static ConfigFunction<TypeScope, String> specTypeResolver(String value) {
        return typeScope -> {
            return Optional
                .ofNullable(typeScope.getType().getErasedType().getAnnotation(SpecType.class))
                .map(spec -> spec.kind())
                .filter(s -> s != null)
                .map(s -> SPECS_PREFIX + s + "." + value)
                .orElse(null);
        };
    }

    private SchemaUtils() {}
}
