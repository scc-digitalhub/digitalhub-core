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

package it.smartcommunitylabdhub.files.models;

import io.micrometer.common.lang.Nullable;
import it.smartcommunitylabdhub.commons.accessors.Accessor;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import java.io.Serializable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Status field common accessor
 */
public interface FileInfoFieldAccessor extends Accessor<Serializable> {
    default @Nullable List<FileInfo> getFiles() {
        List<Map<String, Serializable>> raw = get("files");
        List<FileInfo> files = new LinkedList<>();
        if (raw != null) {
            raw.forEach(e -> {
                try {
                    FileInfo f = JacksonMapper.OBJECT_MAPPER.convertValue(e, FileInfo.class);
                    files.add(f);
                } catch (IllegalArgumentException ex) {
                    //skip
                }
            });
            return files;
        }
        return null;
    }

    static FileInfoFieldAccessor with(Map<String, Serializable> map) {
        return () -> map;
    }
}
