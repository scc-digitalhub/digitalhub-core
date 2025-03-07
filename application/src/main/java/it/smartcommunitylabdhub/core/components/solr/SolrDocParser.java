package it.smartcommunitylabdhub.core.components.solr;

import java.util.Date;
import java.util.List;
import org.apache.solr.common.SolrDocument;

import it.smartcommunitylabdhub.commons.utils.KeyUtils;
import it.smartcommunitylabdhub.core.models.indexers.ItemResult;

public class SolrDocParser {

    @SuppressWarnings("unchecked")
    public static ItemResult parse(SolrDocument doc) {
        ItemResult item = new ItemResult();
        item.setId((String) doc.getFieldValue("id"));
        item.setType((String) doc.getFieldValue("type"));
        item.setKind((String) doc.getFieldValue("kind"));
        item.setProject((String) doc.getFieldValue("project"));
        item.setName((String) doc.getFieldValue("name"));
        item.setStatus((String) doc.getFieldValue("status"));
        item.getMetadata().put("name", (String) doc.getFieldValue("metadata.name"));
        item.getMetadata().put("description", (String) doc.getFieldValue("metadata.description"));
        item.getMetadata().put("project", (String) doc.getFieldValue("metadata.project"));
        item.getMetadata().put("version", (String) doc.getFieldValue("metadata.version"));
        item.getMetadata().put("created", (Date) doc.getFieldValue("metadata.created"));
        item.getMetadata().put("updated", (Date) doc.getFieldValue("metadata.updated"));
        item.getMetadata().put("labels", (List<String>) doc.getFieldValue("metadata.labels"));
        item.setKey(KeyUtils.buildKey(item.getProject(), item.getType(), item.getKind(), item.getName(), item.getId()));
        return item;
    }
}
