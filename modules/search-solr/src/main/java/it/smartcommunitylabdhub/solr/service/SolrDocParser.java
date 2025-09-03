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

package it.smartcommunitylabdhub.solr.service;

import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import it.smartcommunitylabdhub.search.indexers.ItemResult;
import java.util.Date;
import java.util.List;
import org.apache.solr.common.SolrDocument;

public class SolrDocParser {

    @SuppressWarnings("unchecked")
    public static ItemResult parse(SolrDocument doc) {
        ItemResult item = new ItemResult();
        item.setId((String) doc.getFieldValue("id"));
        item.setType((String) doc.getFieldValue("type"));
        item.setKind((String) doc.getFieldValue("kind"));
        item.setProject((String) doc.getFieldValue("project"));
        item.setName((String) doc.getFieldValue("name"));
        item.setStatus((String) doc.getFieldValue("status"));
        item.getMetadata().put("name", (String) doc.getFieldValue("metadata.name"));
        item.getMetadata().put("description", (String) doc.getFieldValue("metadata.description"));
        item.getMetadata().put("project", (String) doc.getFieldValue("metadata.project"));
        item.getMetadata().put("version", (String) doc.getFieldValue("metadata.version"));
        item.getMetadata().put("created", (Date) doc.getFieldValue("metadata.created"));
        item.getMetadata().put("updated", (Date) doc.getFieldValue("metadata.updated"));
        item.getMetadata().put("labels", (List<String>) doc.getFieldValue("metadata.labels"));
        item.setKey(KeyUtils.buildKey(item.getProject(), item.getType(), item.getKind(), item.getName(), item.getId()));
        return item;
    }
}
