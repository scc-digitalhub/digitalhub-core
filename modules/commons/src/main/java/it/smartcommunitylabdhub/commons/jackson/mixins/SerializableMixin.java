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

package it.smartcommunitylabdhub.commons.jackson.mixins;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    anyOf = {
        SerializableMixin.StringField.class,
        SerializableMixin.NumberField.class,
        SerializableMixin.BooleanField.class,
        SerializableMixin.ObjectField.class,
        SerializableMixin.ArrayField.class,
    },
    nullable = true,
    title = "object"
)
public class SerializableMixin {

    @Schema(implementation = String.class, title = "string", defaultValue = "")
    public class StringField {}

    @Schema(implementation = Number.class, title = "number", defaultValue = "")
    public class NumberField {}

    @Schema(implementation = Integer.class, title = "integer", defaultValue = "")
    public class IntegerField {}

    @Schema(implementation = Boolean.class, title = "boolean", defaultValue = "")
    public class BooleanField {}

    @Schema(type = "array", title = "array")
    public class ArrayField {}

    @Schema(
        anyOf = {
            SerializableMixin.StringField.class,
            SerializableMixin.NumberField.class,
            // SerializableMixin.IntegerField.class,
            SerializableMixin.BooleanField.class,
            SerializableMixin.ObjectField.class,
            SerializableMixin.SerializableField[].class,
        },
        // nullable = true,
        title = "object"
    )
    public class SerializableField {}

    @Schema(
        type = "object",
        // nullable = true,
        title = "object"
    )
    public class ObjectField {}
}
