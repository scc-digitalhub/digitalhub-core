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

package it.smartcommunitylabdhub.core.indexers;

import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

public class SolrPageImpl<T> extends PageImpl<T> implements SolrPage<T> {

    private static final long serialVersionUID = 8269910872034647723L;

    private final Map<String, List<String>> filters;

    public SolrPageImpl(List<T> content, Pageable pageable, long total, Map<String, List<String>> filters) {
        super(content, pageable, total);
        this.filters = filters;
    }

    @Override
    public Map<String, List<String>> getFilters() {
        return this.filters;
    }
}
