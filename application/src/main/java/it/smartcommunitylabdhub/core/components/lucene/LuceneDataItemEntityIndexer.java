package it.smartcommunitylabdhub.core.components.lucene;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.VersioningMetadata;
import it.smartcommunitylabdhub.core.models.builders.dataitem.DataItemDTOBuilder;
import it.smartcommunitylabdhub.core.models.entities.DataItemEntity;
import it.smartcommunitylabdhub.core.models.indexers.EntityIndexer;
import it.smartcommunitylabdhub.core.models.indexers.IndexField;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "lucene", name = "index-path")
public class LuceneDataItemEntityIndexer
    extends LuceneBaseEntityIndexer<DataItem>
    implements EntityIndexer<DataItemEntity> {

    private static final String TYPE = EntityName.DATAITEM.getValue();

    private final DataItemDTOBuilder builder;

    public LuceneDataItemEntityIndexer(DataItemDTOBuilder builder) {
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
    public void index(DataItemEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        if (lucene != null) {
            try {
                log.debug("index dataItem {}", entity.getId());

                Document doc = parse(entity);
                lucene.indexDoc(doc);
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }
    }

    @Override
    public void indexAll(Collection<DataItemEntity> entities) {
        Assert.notNull(entities, "entities can not be null");
        log.debug("index {} dataItems", entities.size());

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

    private Document parse(DataItemEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        DataItem item = builder.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }

        log.debug("parse dataItem {}", item.getId());
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
	public void remove(DataItemEntity entity) {
        Assert.notNull(entity, "entity can not be null");
        if (lucene != null) {
            try {
                log.debug("remove index dataItem {}", entity.getId());
                lucene.removeDoc(entity.getId());
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }
	}
}
