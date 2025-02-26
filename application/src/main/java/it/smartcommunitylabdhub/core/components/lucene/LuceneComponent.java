package it.smartcommunitylabdhub.core.components.lucene;

import java.util.List;

import javax.annotation.PreDestroy;

import org.apache.lucene.document.Document;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import it.smartcommunitylabdhub.core.models.indexers.IndexerException;
import it.smartcommunitylabdhub.core.models.indexers.ItemResult;
import it.smartcommunitylabdhub.core.models.indexers.SearchGroupResult;
import it.smartcommunitylabdhub.core.models.indexers.SolrPage;
import it.smartcommunitylabdhub.core.models.indexers.SolrSearchService;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "lucene", name = "index-path")
public class LuceneComponent implements SolrSearchService, InitializingBean {

    private LuceneManager indexManager;

    public LuceneComponent(LuceneProperties properties) {
        Assert.notNull(properties, "lucene properties are required");

        //build manager
        this.indexManager = new LuceneManager(properties);
    }

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(indexManager, "index manager missing");
        try {
            //init
            indexManager.init();
        } catch (IndexerException e) {
            log.error("error initializing solr: {}", e.getMessage());
            indexManager = null;
        }
    }

    @PreDestroy
    public void close() {
        try {
            if (indexManager != null) {
                indexManager.close();
            }
        } catch (IndexerException e) {
            log.error("error disconnecting solr: {}", e.getMessage());
        }
    }

    public void indexDoc(Document doc) throws IndexerException {
        Assert.notNull(doc, "doc can not be null");
        if (doc.getField("id") == null) {
            throw new IllegalArgumentException("missing or invalid id in doc");
        }

        if (doc.getField("type") == null) {
            throw new IllegalArgumentException("missing or invalid type in doc");
        }
        if (indexManager != null) {
            indexManager.indexDoc(doc);
        }
    }

    public void removeDoc(String id) throws IndexerException {
        Assert.notNull(id, "id can not be null");
        if (indexManager != null) {
            indexManager.removeDoc(id);
        }
    }

    public void indexBounce(Iterable<Document> docs) throws IndexerException {
        Assert.notNull(docs, "docs can not be null");
        if (indexManager != null) {
            indexManager.indexBounce(docs);
        }
    }

//    public void registerFields(Iterable<IndexField> fields) throws LuceneIndexerException {
//        Assert.notNull(fields, "fields can not be null");
//        if (indexManager != null) {
//            indexManager.initFields(fields);
//        }
//    }

    @Override
    public SolrPage<SearchGroupResult> groupSearch(String q, List<String> fq, Pageable pageRequest) throws IndexerException {
        if (indexManager == null) {
            throw new IndexerException("solr not available");
        }
        return indexManager.groupSearch(q, fq, pageRequest);
    }

    @Override
    public SolrPage<ItemResult> itemSearch(String q, List<String> fq, Pageable pageRequest) throws IndexerException {
        if (indexManager == null) {
            throw new IndexerException("solr not available");
        }
        return indexManager.itemSearch(q, fq, pageRequest);
    }

    public void clearIndex() throws IndexerException {
        if (indexManager != null) {
            indexManager.clearIndex();
        }
    }

    public void clearIndexByType(String type) throws IndexerException {
        if (indexManager != null) {
            indexManager.clearIndexByType(type);
        }
    }
}
