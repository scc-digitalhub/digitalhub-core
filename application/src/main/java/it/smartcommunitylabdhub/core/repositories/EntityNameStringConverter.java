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

package it.smartcommunitylabdhub.core.repositories;

import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class EntityNameStringConverter implements Converter<String, EntityName> {

    @Override
    @Nullable
    public EntityName convert(@NonNull String source) {
        //enum is uppercase
        String value = source.toUpperCase();

        //also handle pluralized values
        if (value.endsWith("S")) {
            value = value.substring(0, value.length() - 1);
        }

        return EntityName.valueOf(value);
    }
}
