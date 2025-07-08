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

package it.smartcommunitylabdhub.core.utils;

import it.smartcommunitylabdhub.commons.accessors.fields.KeyAccessor;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import jakarta.annotation.Nullable;
import org.springframework.util.StringUtils;

public class RefUtils {

    public static @Nullable String getRefPath(BaseDTO dto) {
        KeyAccessor key = KeyAccessor.with(dto.getKey());

        if (!StringUtils.hasText(key.getId())) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("/-/");
        sb.append(key.getProject()).append("/");
        sb.append(key.getType()).append("s").append("/");
        sb.append(key.getId());

        return sb.toString();
    }

    private RefUtils() {}
}
