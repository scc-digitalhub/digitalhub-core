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

package it.smartcommunitylabdhub.commons.jackson.definitions;

import com.github.victools.jsonschema.generator.CustomDefinition;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.jackson.mixins.SerializableMixin;
import java.io.Serializable;
import org.springframework.util.StringUtils;

public class SerializableDefinitionsModule implements com.github.victools.jsonschema.generator.Module {

    @Override
    public void applyToConfigBuilder(SchemaGeneratorConfigBuilder builder) {
        builder
            .forTypesInGeneral()
            .withCustomDefinitionProvider((javaType, context) -> {
                //redefine Serializable via mixin with annotations, and inline
                return Serializable.class.equals(javaType.getErasedType())
                    ? new CustomDefinition(
                        context.createDefinition(context.getTypeContext().resolve(SerializableMixin.class)),
                        CustomDefinition.DefinitionType.STANDARD,
                        CustomDefinition.AttributeInclusion.YES
                    )
                    : null;
            })
            .withCustomDefinitionProvider((javaType, context) -> {
                Schema sa = javaType.getErasedType().getAnnotation(Schema.class);
                if (sa != null && sa.implementation() != Void.class) {
                    //override with implementation type, inline
                    return new CustomDefinition(
                        context.createDefinition(context.getTypeContext().resolve(sa.implementation())),
                        CustomDefinition.DefinitionType.STANDARD,
                        CustomDefinition.AttributeInclusion.YES
                    );
                }

                return null;
            })
            .withCustomDefinitionProvider((javaType, context) -> {
                SchemaGeneratorConfig config = context.getGeneratorConfig();
                //redefine ArrayField with items
                return SerializableMixin.ArrayField.class.equals(javaType.getErasedType())
                    ? new CustomDefinition(
                        config
                            .createObjectNode()
                            .put(
                                config.getKeyword(SchemaKeyword.TAG_TYPE),
                                config.getKeyword(SchemaKeyword.TAG_TYPE_ARRAY)
                            )
                            .set(
                                config.getKeyword(SchemaKeyword.TAG_ITEMS),
                                config
                                    .createObjectNode()
                                    .put(config.getKeyword(SchemaKeyword.TAG_REF), "#/$defs/Serializable")
                            ),
                        CustomDefinition.DefinitionType.STANDARD,
                        CustomDefinition.AttributeInclusion.YES
                    )
                    : null;
            })
            .withTypeAttributeOverride((node, scope, context) -> {
                //for custom defined overrides also inject props from schema, since those are skipper by other modules
                if (SerializableMixin.class.getPackage().equals(scope.getType().getErasedType().getPackage())) {
                    Schema sa = scope.getType().getErasedType().getAnnotation(Schema.class);
                    if (sa != null) {
                        if (StringUtils.hasText(sa.title())) {
                            node.put("title", sa.title());
                        }
                        if (StringUtils.hasText(sa.description())) {
                            node.put("description", sa.description());
                        }
                        if (StringUtils.hasText(sa.defaultValue())) {
                            node.put("defaultValue", sa.defaultValue());
                        }
                    }
                }
            });
    }
}
