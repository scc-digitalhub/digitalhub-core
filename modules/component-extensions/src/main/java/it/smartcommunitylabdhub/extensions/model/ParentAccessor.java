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

package it.smartcommunitylabdhub.extensions.model;

import io.micrometer.common.lang.Nullable;
import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.accessors.Accessor;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Status field common accessor
 */
public interface ParentAccessor extends Accessor<String> {
    public static final String KEY_PATTERN = "([^/]+)://([^/]+)/([^:]+):(.+)";

    default @NotNull String getProject() {
        return get(Fields.PROJECT);
    }

    default @Nullable String getKind() {
        return get(Fields.KIND);
    }

    default @Nullable String getName() {
        return get(Fields.NAME);
    }

    default @Nullable String getId() {
        return get(Fields.ID);
    }

    static ParentAccessor with(Map<String, String> map) {
        return () -> map;
    }

    static ParentAccessor with(String key) {
        if (key == null || key.isEmpty()) {
            return ParentAccessor.with(Collections.emptyMap());
        }

        //match value
        String value = key.trim();
        Matcher matcher = Pattern.compile(KEY_PATTERN).matcher(value);

        if (matcher != null && matcher.matches()) {
            String kind = matcher.group(1);
            String project = matcher.group(2);
            String name = matcher.group(3);
            String id = matcher.group(4);

            Map<String, String> map = new HashMap<>();
            map.put(Fields.PROJECT, project);
            map.put(Fields.KIND, kind);
            map.put(Fields.NAME, name);
            map.put(Fields.ID, id);

            return ParentAccessor.with(map);
        }

        throw new IllegalArgumentException("Cannot create accessor for the given parent string.");
    }
}
