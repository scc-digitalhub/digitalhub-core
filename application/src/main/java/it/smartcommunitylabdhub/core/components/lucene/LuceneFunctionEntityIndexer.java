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

package it.smartcommunitylabdhub.core.components.lucene;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.commons.models.metadata.VersioningMetadata;
import it.smartcommunitylabdhub.core.functions.persistence.FunctionDTOBuilder;
import it.smartcommunitylabdhub.core.functions.persistence.FunctionEntity;
import it.smartcommunitylabdhub.core.indexers.EntityIndexer;
import it.smartcommunitylabdhub.core.indexers.IndexField;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "lucene", name = "index-path")
public class LuceneFunctionEntityIndexer
    extends LuceneBaseEntityIndexer<Function>
    implements EntityIndexer<FunctionEntity> {

    private static final String TYPE = EntityName.FUNCTION.getValue();

    private final FunctionDTOBuilder builder;

    public LuceneFunctionEntityIndexer(FunctionDTOBuilder builder) {
        Assert.notNull(builder, "builder can not be null");

        this.builder = builder;
    }

    @Override
    public List<IndexField> fields() {
        List<IndexField> fields = super.fields();
        //fields.add(new IndexField("metadata.version", "text_en", true, false, true, true));
        return fields;
    }

    @Override
    public void index(FunctionEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        if (lucene != null) {
            try {
                log.debug("index function {}", entity.getId());

                Document doc = parse(entity);
                lucene.indexDoc(doc);
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }
    }

    @Override
    public void indexAll(Collection<FunctionEntity> entities) {
        Assert.notNull(entities, "entities can not be null");
        log.debug("index {} functions", entities.size());

        if (lucene != null) {
            try {
                List<Document> docs = entities.stream().map(e -> parse(e)).collect(Collectors.toList());
                lucene.indexBounce(docs);
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }
    }

    @Override
    public void clearIndex() {
        log.debug("clear index for {}", TYPE);
        try {
            lucene.clearIndexByType(TYPE);
        } catch (StoreException e) {
            log.error("error with solr: {}", e.getMessage());
        }
    }

    private Document parse(FunctionEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        Function item = builder.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }

        log.debug("index function {}", item.getId());
        if (log.isTraceEnabled()) {
            log.trace("item: {}", item);
        }

        //base
        Document doc = parse(item, TYPE);

        //add versioning
        VersioningMetadata versioning = VersioningMetadata.from(item.getMetadata());
        Field field = new TextField("metadata.version", getStringValue(versioning.getVersion()), Field.Store.YES);
        doc.add(field);

        //TODO evaluate adding spec

        if (log.isTraceEnabled()) {
            log.trace("doc: {}", doc);
        }

        return doc;
    }

    @Override
    public void remove(FunctionEntity entity) {
        Assert.notNull(entity, "entity can not be null");
        if (lucene != null) {
            try {
                log.debug("remove index function {}", entity.getId());
                lucene.removeDoc(entity.getId());
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }
    }
}
