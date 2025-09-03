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

package it.smartcommunitylabdhub.solr.base;

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
import it.smartcommunitylabdhub.search.indexers.EntityIndexer;
import it.smartcommunitylabdhub.search.indexers.IndexField;
import it.smartcommunitylabdhub.solr.SolrComponent;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class SolrBaseEntityIndexer<D extends BaseDTO> implements EntityIndexer<D>, InitializingBean {

    public static final int PAGE_MAX_SIZE = 100;

    protected final EntityName type;

    protected SolrComponent solr;

    @SuppressWarnings("unchecked")
    public SolrBaseEntityIndexer() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = EntityUtils.getEntityName((Class<D>) t);
    }

    @SuppressWarnings("unchecked")
    public SolrBaseEntityIndexer(SolrComponent solr) {
        Assert.notNull(solr, "solr can not be null");
        this.solr = solr;
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = EntityUtils.getEntityName((Class<D>) t);
    }

    @Autowired
    public void setSolr(SolrComponent solr) {
        this.solr = solr;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(solr, "solr can not be null");

        //register fields
        log.debug("register fields to solr");
        solr.registerFields(fields());
    }

    protected String buildKeyGroup(String kind, String project, String name) {
        return kind + "_" + project + "_" + name;
    }

    protected SolrInputDocument parse(D item) {
        Assert.notNull(item, "dto can not be null");

        SolrInputDocument doc = new SolrInputDocument();
        String keyGroup = buildKeyGroup(item.getKind(), item.getProject(), item.getName());
        doc.addField("keyGroup", keyGroup);
        doc.addField("type", type.name());
        //base doc
        doc.addField("id", item.getId());
        doc.addField("kind", item.getKind());
        doc.addField("project", item.getProject());
        doc.addField("name", item.getName());
        doc.addField("user", item.getUser());

        //status
        if (item instanceof StatusDTO) {
            StatusFieldAccessor status = StatusFieldAccessor.with(((StatusDTO) item).getStatus());
            doc.addField("status", status.getState());
        }

        //extract meta to index
        if (item instanceof MetadataDTO) {
            //metadata
            BaseMetadata metadata = BaseMetadata.from(((MetadataDTO) item).getMetadata());
            doc.addField("metadata.name", metadata.getName());
            doc.addField("metadata.description", metadata.getDescription());
            doc.addField("metadata.project", metadata.getProject());
            doc.addField("metadata.labels", metadata.getLabels());
            doc.addField("metadata.created", Date.from(metadata.getCreated().toInstant()));
            doc.addField("metadata.updated", Date.from(metadata.getUpdated().toInstant()));

            //audit
            AuditMetadata auditing = AuditMetadata.from(((MetadataDTO) item).getMetadata());
            doc.addField("metadata.createdBy", auditing.getCreatedBy());
            doc.addField("metadata.updatedBy", auditing.getUpdatedBy());

            //add versioning
            VersioningMetadata versioning = VersioningMetadata.from(((MetadataDTO) item).getMetadata());
            if (StringUtils.hasText(versioning.getVersion())) {
                doc.addField("metadata.version", versioning.getVersion());
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
            log.debug("solr index {}: {}", type, item.getId());

            SolrInputDocument doc = parse(item);
            if (log.isTraceEnabled()) {
                log.trace("doc: {}", doc);
            }

            // index
            solr.indexDoc(doc);
        } catch (StoreException e) {
            log.error("error with solr: {}", e.getMessage());
        }
    }

    @Override
    public void indexAll(Collection<D> items) {
        Assert.notNull(items, "entities can not be null");
        log.debug("index {} {}", items.size(), type);

        if (solr != null) {
            try {
                List<SolrInputDocument> docs = items.stream().map(e -> parse(e)).collect(Collectors.toList());

                solr.indexBounce(docs);
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }
    }

    @Override
    public void clearIndex() {
        log.debug("clear index for {}", type);
        try {
            solr.clearIndexByType(type.name());
        } catch (StoreException e) {
            log.error("error with solr: {}", e.getMessage());
        }
    }

    @Override
    public void remove(D item) {
        Assert.notNull(item, "entity can not be null");
        try {
            log.debug("remove index {}: {}", type, item.getId());
            solr.removeDoc(item.getId());
        } catch (StoreException e) {
            log.error("error with solr: {}", e.getMessage());
        }
    }
}
