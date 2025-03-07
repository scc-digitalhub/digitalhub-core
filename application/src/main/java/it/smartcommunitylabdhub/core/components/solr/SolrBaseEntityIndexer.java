package it.smartcommunitylabdhub.core.components.solr;

import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.solr.common.SolrInputDocument;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.AuditMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.core.models.indexers.IndexField;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SolrBaseEntityIndexer<D extends BaseDTO> implements InitializingBean {

    public static final int PAGE_MAX_SIZE = 100;

    protected SolrComponent solr;
    
    @Autowired(required = false)
    public void setSolr(SolrComponent solr) {
        this.solr = solr;
    }
    
    @Override
    public void afterPropertiesSet() throws Exception {
        if (solr != null) {
            //register fields
            log.debug("register fields to solr");
            solr.registerFields(fields());
        }
    }

    public String buildKeyGroup(String kind, String project, String name) {
        return kind + "_" + project + "_" + name;
    }

    protected SolrInputDocument parse(D item, String type) {
        Assert.notNull(item, "dto can not be null");

        SolrInputDocument doc = new SolrInputDocument();
        String keyGroup = SolrBaseEntityParser.buildKeyGroup(item.getKind(), item.getProject(), item.getName());
        doc.addField("keyGroup", keyGroup);
        doc.addField("type", type);
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
            BaseMetadata metadata = BaseMetadata.from(((MetadataDTO) item).getMetadata());

            //metadata
            doc.addField("metadata.name", metadata.getName());
            doc.addField("metadata.description", metadata.getDescription());
            doc.addField("metadata.project", metadata.getProject());
            doc.addField("metadata.labels", metadata.getLabels());
            doc.addField("metadata.created", Date.from(metadata.getCreated().toInstant()));
            doc.addField("metadata.updated", Date.from(metadata.getUpdated().toInstant()));

            AuditMetadata auditing = AuditMetadata.from(((MetadataDTO) item).getMetadata());
            doc.addField("metadata.createdBy", auditing.getCreatedBy());
            doc.addField("metadata.updatedBy", auditing.getUpdatedBy());
        }

        return doc;
    }
    
    public List<IndexField> fields() {
        List<IndexField> fields = new LinkedList<>();

        fields.add(new IndexField("id", "string", true, false, true, true));
        fields.add(new IndexField("keyGroup", "string", true, false, true, true));
        fields.add(new IndexField("type", "string", true, false, true, true));

        fields.add(new IndexField("kind", "string", true, false, true, true));
        fields.add(new IndexField("project", "text_en", true, false, true, true));
        fields.add(new IndexField("name", "text_en", true, false, true, true));
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

        return fields;
    }
}
