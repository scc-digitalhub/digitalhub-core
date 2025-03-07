package it.smartcommunitylabdhub.core.components.lucene;

import org.apache.lucene.document.Document;

import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import it.smartcommunitylabdhub.core.models.indexers.ItemResult;

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
