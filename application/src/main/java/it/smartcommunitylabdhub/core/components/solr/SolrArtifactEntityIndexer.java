package it.smartcommunitylabdhub.core.components.solr;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.solr.common.SolrInputDocument;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.metadata.VersioningMetadata;
import it.smartcommunitylabdhub.core.models.builders.artifact.ArtifactDTOBuilder;
import it.smartcommunitylabdhub.core.models.entities.ArtifactEntity;
import it.smartcommunitylabdhub.core.models.indexers.EntityIndexer;
import it.smartcommunitylabdhub.core.models.indexers.IndexField;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@ConditionalOnProperty(prefix = "solr", name = "url")
@Primary
public class SolrArtifactEntityIndexer extends SolrBaseEntityIndexer<Artifact> implements EntityIndexer<ArtifactEntity> {

    private static final String TYPE = EntityName.ARTIFACT.getValue();

    private final ArtifactDTOBuilder builder;

    public SolrArtifactEntityIndexer(ArtifactDTOBuilder builder) {
        Assert.notNull(builder, "builder can not be null");

        this.builder = builder;
    }

    @Override
    public List<IndexField> fields() {
        List<IndexField> fields = super.fields();

        fields.add(new IndexField("metadata.version", "text_en", true, false, true, true));
        return fields;
    }

    @Override
    public void index(ArtifactEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        if (solr != null) {
            try {
                log.debug("solr index artifact {}", entity.getId());

                SolrInputDocument doc = parse(entity);
                solr.indexDoc(doc);
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }        
    }

    @Override
    public void indexAll(Collection<ArtifactEntity> entities) {
        Assert.notNull(entities, "entities can not be null");
        log.debug("index {} artifacts", entities.size());

        if (solr != null) {
            try {
                List<SolrInputDocument> docs = entities.stream().map(e -> parse(e)).collect(Collectors.toList());
                solr.indexBounce(docs);
            } catch (StoreException e) {
                log.error("error with solr: {}", e.getMessage());
            }
        }
    }

    @Override
    public void clearIndex() {
        log.debug("clear index for {}", TYPE);
        try {
            solr.clearIndexByType(TYPE);
        } catch (StoreException e) {
            log.error("error with solr: {}", e.getMessage());
        }
    }

    private SolrInputDocument parse(ArtifactEntity entity) {
        Assert.notNull(entity, "entity can not be null");

        Artifact item = builder.convert(entity);
        if (item == null) {
            throw new IllegalArgumentException("invalid or null entity");
        }

        log.debug("parse artifact {}", item.getId());
        if (log.isTraceEnabled()) {
            log.trace("item: {}", item);
        }

        //base
        SolrInputDocument doc = parse(item, TYPE);

        //add versioning
        VersioningMetadata versioning = VersioningMetadata.from(item.getMetadata());
        doc.addField("metadata.version", versioning.getVersion());

        //TODO evaluate adding spec

        if (log.isTraceEnabled()) {
            log.trace("doc: {}", doc);
        }

        return doc;
    }    
}
