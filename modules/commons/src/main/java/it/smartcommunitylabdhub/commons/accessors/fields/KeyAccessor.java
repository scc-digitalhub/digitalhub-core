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

package it.smartcommunitylabdhub.commons.accessors.fields;

import io.micrometer.common.lang.Nullable;
import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.Accessor;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Status field common accessor
 */
public interface KeyAccessor extends Accessor<String> {
    public static final String KEY_PATTERN = "store://([^/]+)/([^/]+)/([^/]+)/([^:]+):(.+)";
    public static final String KEY_PATTERN_NO_ID = "store://([^/]+)/([^/]+)/([^/]+)/([^:]+)";
    public static final String KEY_PATTERN_NO_NAME = "store://([^/]+)/([^/]+)/([^/]+)";
    public static final String KEY_PATTERN_NO_KIND = "store://([^/]+)/([^/]+)";
    public static final String KEY_PATTERN_NO_TYPE = "store://([^/]+)";

    default @NotNull String getProject() {
        return get(Fields.PROJECT);
    }
    default @Nullable String getType() {
        return get(Fields.TYPE);
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

    static KeyAccessor with(Map<String, String> map) {
        return () -> map;
    }

    static KeyAccessor with(String key) {
        if (key == null || key.isEmpty() || !key.startsWith(Keys.STORE_PREFIX)) {
            return KeyAccessor.with(Collections.emptyMap());
        }

        List<Pattern> patterns = List.of(
            Pattern.compile(KEY_PATTERN),
            Pattern.compile(KEY_PATTERN_NO_ID),
            Pattern.compile(KEY_PATTERN_NO_NAME),
            Pattern.compile(KEY_PATTERN_NO_KIND),
            Pattern.compile(KEY_PATTERN_NO_TYPE)
        );

        //match in order
        Matcher matcher = patterns
            .stream()
            .map(pattern -> pattern.matcher(key))
            .filter(Matcher::matches)
            .findFirst()
            .orElse(null);

        if (matcher != null) {
            String project = matcher.group(1);
            String type = matcher.groupCount() > 1 ? matcher.group(2) : null;
            String kind = matcher.groupCount() > 2 ? matcher.group(3) : null;
            String name = matcher.groupCount() > 3 ? matcher.group(4) : null;
            String id = matcher.groupCount() == 5 ? matcher.group(5) : null;

            Map<String, String> map = new HashMap<>();
            map.put(Fields.PROJECT, project);
            map.put(Fields.TYPE, type);
            map.put(Fields.KIND, kind);
            map.put(Fields.NAME, name);
            map.put(Fields.ID, id);

            return KeyAccessor.with(map);
        }
        throw new IllegalArgumentException("Cannot create accessor for the given task string.");
    }
}
