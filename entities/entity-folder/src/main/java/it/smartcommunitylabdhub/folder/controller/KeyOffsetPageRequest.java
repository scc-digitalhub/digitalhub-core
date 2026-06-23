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

package it.smartcommunitylabdhub.folder.controller;

import java.io.Serializable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public class KeyOffsetPageRequest<T extends Serializable> extends PageRequest {

    public static final int DEFAULT_PAGE_SIZE = 100;
    private final T lastEntry;

    public KeyOffsetPageRequest(Integer page, Integer size, Sort sort, T lastEntry) {
        super(page != null ? page : 0, size != null ? size : DEFAULT_PAGE_SIZE, sort != null ? sort : Sort.unsorted());
        this.lastEntry = lastEntry;
    }

    public T getLastEntry() {
        return lastEntry;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((lastEntry == null) ? 0 : lastEntry.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        KeyOffsetPageRequest<?> other = (KeyOffsetPageRequest<?>) obj;
        if (lastEntry == null) {
            if (other.lastEntry != null) return false;
        } else if (!lastEntry.equals(other.lastEntry)) return false;
        return true;
    }
}
