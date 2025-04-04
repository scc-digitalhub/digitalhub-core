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
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.VersioningMetadata;
import it.smartcommunitylabdhub.commons.models.model.Model;
import it.smartcommunitylabdhub.core.models.builders.model.ModelDTOBuilder;
import it.smartcommunitylabdhub.core.models.entities.ModelEntity;
import it.smartcommunitylabdhub.core.models.indexers.EntityIndexer;
import it.smartcommunitylabdhub.core.models.indexers.IndexField;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "lucene", name = "index-path")
public class LuceneModelEntityIndexer extends LuceneBaseEntityIndexer<Model> implements EntityIndexer<ModelEntity> {

    private static final String TYPE = EntityName.MODEL.getValue();

    private final ModelDTOBuilder builder;

    public LuceneModelEntityIndexer(ModelDTOBuilder builder) {
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
    public void index(ModelEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        if (lucene != null) {
            try {
                log.debug("index model {}", entity.getId());

                Document doc = parse(entity);
                lucene.indexDoc(doc);
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }
    }

    @Override
    public void indexAll(Collection<ModelEntity> entities) {
        Assert.notNull(entities, "entities can not be null");
        log.debug("index {} models", entities.size());

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

    private Document parse(ModelEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        Model item = builder.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }

        log.debug("parse model {}", item.getId());
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
	public void remove(ModelEntity entity) {
        Assert.notNull(entity, "entity can not be null");
        if (lucene != null) {
            try {
                log.debug("remove index model {}", entity.getId());
                lucene.removeDoc(entity.getId());
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }
	}
}
