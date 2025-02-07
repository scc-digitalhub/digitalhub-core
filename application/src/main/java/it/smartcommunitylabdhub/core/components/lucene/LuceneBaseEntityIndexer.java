package it.smartcommunitylabdhub.core.components.lucene;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import it.smartcommunitylabdhub.commons.accessors.fields.StatusFieldAccessor;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.AuditMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.metadata.MetadataDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import it.smartcommunitylabdhub.core.models.indexers.IndexField;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class LuceneBaseEntityIndexer<D extends BaseDTO> implements InitializingBean {

    public static final int PAGE_MAX_SIZE = 100;

    protected SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"); 
    
    protected LuceneComponent lucene;

    @Autowired(required = false)
    public void setLucene(LuceneComponent lucene) {
    	this.lucene = lucene;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }

    public String buildKeyGroup(String kind, String project, String name) {
        return kind + "_" + project + "_" + name;
    }
    
    protected String getStringValue(String field) {
    	return StringUtils.hasLength(field) ? field : "";
    }

    protected Document parse(D item, String type) {
    	Assert.notNull(item, "dto can not be null");
    	Document doc = new Document();
    	
        String keyGroup = buildKeyGroup(item.getKind(), item.getProject(), item.getName());
        doc.add(new StringField("keyGroup", keyGroup, Field.Store.YES));
        doc.add(new StringField("type", type, Field.Store.YES));
        
        //base doc
        doc.add(new StringField("id", item.getId(), Field.Store.YES));
        doc.add(new StringField("kind", item.getKind(), Field.Store.YES));
        doc.add(new StringField("project", item.getProject(), Field.Store.YES));
        doc.add(new StringField("name", item.getName(), Field.Store.YES));
        doc.add(new StringField("user", getStringValue(item.getUser()), Field.Store.YES));
        
        //status
        if (item instanceof StatusDTO) {
            StatusFieldAccessor status = StatusFieldAccessor.with(((StatusDTO) item).getStatus());
            doc.add(new StringField("status", status.getState(), Field.Store.YES));            
        }
        
        //extract meta to index
        if (item instanceof MetadataDTO) {
            BaseMetadata metadata = BaseMetadata.from(((MetadataDTO) item).getMetadata());

            //metadata
            doc.add(new TextField("metadata.name", getStringValue(metadata.getName()), Field.Store.YES));
            doc.add(new TextField("metadata.description", getStringValue(metadata.getDescription()), Field.Store.YES));
            doc.add(new TextField("metadata.project", getStringValue(metadata.getProject()), Field.Store.YES));
            if(metadata.getLabels() != null) {
            	for(String label : metadata.getLabels()) {
            		doc.add(new StringField("metadata.labels", label, Field.Store.YES));
            	}
            }
            doc.add(new StringField("metadata.created", sdf.format(Date.from(metadata.getCreated().toInstant())), Field.Store.YES));
            doc.add(new StringField("metadata.updated", sdf.format(Date.from(metadata.getUpdated().toInstant())), Field.Store.YES));

            AuditMetadata auditing = AuditMetadata.from(((MetadataDTO) item).getMetadata());
            doc.add(new StringField("metadata.createdBy", auditing.getCreatedBy(), Field.Store.YES));
            doc.add(new StringField("metadata.updatedBy", auditing.getUpdatedBy(), Field.Store.YES));
        }
        
        return doc;
    }

    public List<IndexField> fields() {
        List<IndexField> fields = new LinkedList<>();
        return fields;
    }
}
