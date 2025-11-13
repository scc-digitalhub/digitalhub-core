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

package it.smartcommunitylabdhub.lucene.service;

import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import it.smartcommunitylabdhub.search.indexers.ItemResult;
import org.apache.lucene.document.Document;

public class LuceneDocParser {

    public static final String dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    public static ItemResult parse(Document doc) {
        ItemResult item = new ItemResult();
        item.setId(doc.getField("id").stringValue());
        item.setType(doc.getField("type").stringValue());
        item.setKind(doc.getField("kind").stringValue());
        item.setProject(doc.getField("project").stringValue());
        item.setName(doc.getField("name").stringValue());
        item.setStatus(doc.getField("status").stringValue());
        item.getMetadata().put("name", doc.getField("metadata.name").stringValue());
        item.getMetadata().put("description", doc.getField("metadata.description").stringValue());
        item.getMetadata().put("project", doc.getField("metadata.project").stringValue());
        item.getMetadata().put("version", doc.getField("metadata.version").stringValue());
        item.getMetadata().put("created", doc.getField("metadata.created").stringValue());
        item.getMetadata().put("updated", doc.getField("metadata.updated").stringValue());
        item.getMetadata().put("labels", doc.getValues("metadata.labels"));
        item.setKey(KeyUtils.buildKey(item.getProject(), item.getType(), item.getKind(), item.getName(), item.getId()));
        return item;
    }
}
