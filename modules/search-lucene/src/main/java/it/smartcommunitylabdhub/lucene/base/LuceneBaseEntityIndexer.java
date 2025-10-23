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

package it.smartcommunitylabdhub.lucene.base;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.AuditMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.metadata.VersioningMetadata;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.lucene.LuceneComponent;
import it.smartcommunitylabdhub.lucene.service.LuceneDocParser;
import it.smartcommunitylabdhub.search.indexers.EntityIndexer;
import it.smartcommunitylabdhub.search.indexers.IndexField;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.util.BytesRef;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class LuceneBaseEntityIndexer<D extends BaseDTO> implements EntityIndexer<D>, InitializingBean {

    public static final int PAGE_MAX_SIZE = 100;

    protected final EntityName type;

    protected LuceneComponent lucene;

    @SuppressWarnings("unchecked")
    public LuceneBaseEntityIndexer() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = EntityUtils.getEntityName((Class<D>) t);
    }

    @SuppressWarnings("unchecked")
    public LuceneBaseEntityIndexer(LuceneComponent lucene) {
        Assert.notNull(lucene, "lucene can not be null");
        this.lucene = lucene;
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = EntityUtils.getEntityName((Class<D>) t);
    }

    @Autowired
    public void setLucene(LuceneComponent lucene) {
        this.lucene = lucene;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(lucene, "lucene can not be null");
    }

    protected String buildKeyGroup(String kind, String project, String name) {
        return kind + "_" + project + "_" + name;
    }

    protected String getStringValue(String field) {
        return StringUtils.hasLength(field) ? field : "";
    }

    protected Document parse(D item) {
        Assert.notNull(item, "dto can not be null");
        Document doc = new Document();

        String keyGroup = buildKeyGroup(item.getKind(), item.getProject(), item.getName());
        doc.add(new StringField("keyGroup", keyGroup, Field.Store.YES));
        doc.add(new SortedDocValuesField("keyGroup", new BytesRef(doc.get("keyGroup"))));

        doc.add(new StringField("type", type.name().toLowerCase(), Field.Store.YES));
        doc.add(new SortedDocValuesField("type", new BytesRef(doc.get("type"))));

        //base doc
        doc.add(new StringField("id", item.getId(), Field.Store.YES));

        doc.add(new StringField("kind", item.getKind(), Field.Store.YES));
        doc.add(new SortedDocValuesField("kind", new BytesRef(doc.get("kind"))));

        doc.add(new StringField("project", item.getProject(), Field.Store.YES));
        doc.add(new SortedDocValuesField("project", new BytesRef(doc.get("project"))));

        doc.add(new StringField("name", item.getName(), Field.Store.YES));

        doc.add(new StringField("user", getStringValue(item.getUser()), Field.Store.YES));

        //status
        if (item instanceof StatusDTO) {
            StatusFieldAccessor status = StatusFieldAccessor.with(((StatusDTO) item).getStatus());
            doc.add(new StringField("status", getStringValue(status.getState()), Field.Store.YES));
        }

        //extract meta to index
        if (item instanceof MetadataDTO) {
            //metadata
            BaseMetadata metadata = BaseMetadata.from(((MetadataDTO) item).getMetadata());
            doc.add(new TextField("metadata.name", getStringValue(metadata.getName()), Field.Store.YES));
            doc.add(new SortedDocValuesField("metadata.name", new BytesRef(doc.get("metadata.name"))));

            doc.add(new TextField("metadata.description", getStringValue(metadata.getDescription()), Field.Store.YES));
            doc.add(new SortedDocValuesField("metadata.description", new BytesRef(doc.get("metadata.description"))));

            doc.add(new TextField("metadata.project", getStringValue(metadata.getProject()), Field.Store.YES));

            if (metadata.getLabels() != null) {
                for (String label : metadata.getLabels()) {
                    doc.add(new StringField("metadata.labels", label, Field.Store.YES));
                }
            }

            SimpleDateFormat sdf = new SimpleDateFormat(LuceneDocParser.dateFormat);
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            doc.add(
                new StringField(
                    "metadata.created",
                    sdf.format(Date.from(metadata.getCreated().toInstant())),
                    Field.Store.YES
                )
            );
            doc.add(
                new StringField(
                    "metadata.updated",
                    sdf.format(Date.from(metadata.getUpdated().toInstant())),
                    Field.Store.YES
                )
            );

            doc.add(
                new StringField(
                    "metadata.updatedLong",
                    String.valueOf(Date.from(metadata.getUpdated().toInstant()).getTime()),
                    Field.Store.YES
                )
            );
            doc.add(new SortedDocValuesField("metadata.updatedLong", new BytesRef(doc.get("metadata.updatedLong"))));

            //audit
            AuditMetadata auditing = AuditMetadata.from(((MetadataDTO) item).getMetadata());
            doc.add(new StringField("metadata.createdBy", getStringValue(auditing.getCreatedBy()), Field.Store.YES));
            doc.add(new StringField("metadata.updatedBy", getStringValue(auditing.getUpdatedBy()), Field.Store.YES));

            //add versioning
            VersioningMetadata versioning = VersioningMetadata.from(((MetadataDTO) item).getMetadata());
            if (StringUtils.hasText(versioning.getVersion())) {
                doc.add(new TextField("metadata.version", getStringValue(versioning.getVersion()), Field.Store.YES));
            }
        }

        return doc;
    }

    @Override
    public List<IndexField> fields() {
        List<IndexField> fields = new LinkedList<>();

        fields.add(new IndexField("id", "string", true, false, true, true));
        fields.add(new IndexField("keyGroup", "string", true, false, true, true));
        fields.add(new IndexField("type", "string", true, false, true, true));

        fields.add(new IndexField("kind", "string", true, false, true, true));
        fields.add(new IndexField("project", "string", true, false, true, true));
        fields.add(new IndexField("name", "string", true, false, true, true));
        fields.add(new IndexField("user", "string", true, false, true, true));

        fields.add(new IndexField("status", "string", true, false, true, true));

        fields.add(new IndexField("metadata.name", "text_en", true, false, true, true));
        fields.add(new IndexField("metadata.description", "text_en", true, false, true, true));
        fields.add(new IndexField("metadata.project", "text_en", true, false, true, true));

        fields.add(new IndexField("metadata.labels", "text_en", true, true, true, true));

        fields.add(new IndexField("metadata.created", "pdate", true, false, true, true));
        fields.add(new IndexField("metadata.updated", "pdate", true, false, true, true));
        fields.add(new IndexField("metadata.createdBy", "string", true, false, true, true));
        fields.add(new IndexField("metadata.updatedBy", "string", true, false, true, true));

        fields.add(new IndexField("metadata.version", "text_en", true, false, true, true));

        return fields;
    }

    @Override
    public void index(D item) {
        Assert.notNull(item, "entity can not be null");

        try {
            log.debug("lucene index {}: {}", type, item.getId());

            Document doc = parse(item);
            if (log.isTraceEnabled()) {
                log.trace("doc: {}", doc);
            }

            // index
            lucene.indexDoc(doc);
        } catch (StoreException e) {
            log.error("error with lucene: {}", e.getMessage());
        }
    }

    @Override
    public void indexAll(Collection<D> items) {
        Assert.notNull(items, "entities can not be null");
        log.debug("index {} {}", items.size(), type);

        try {
            List<Document> docs = items.stream().map(e -> parse(e)).collect(Collectors.toList());
            lucene.indexBounce(docs);
        } catch (StoreException e) {
            log.error("error with solr: {}", e.getMessage());
        }
    }

    @Override
    public void clearIndex() {
        log.debug("clear index for {}", type);
        try {
            lucene.clearIndexByType(type.name());
        } catch (StoreException e) {
            log.error("error with solr: {}", e.getMessage());
        }
    }

    @Override
    public void remove(D item) {
        Assert.notNull(item, "entity can not be null");
        try {
            log.debug("lucene remove index {}: {}", type, item.getId());
            lucene.removeDoc(item.getId());
        } catch (StoreException e) {
            log.error("error with lucene: {}", e.getMessage());
        }
    }
}
