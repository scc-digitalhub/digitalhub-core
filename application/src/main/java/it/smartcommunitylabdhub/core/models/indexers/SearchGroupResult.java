package it.smartcommunitylabdhub.core.models.indexers;

import java.util.ArrayList;
import java.util.List;

public class SearchGroupResult {

    String id;
    String keyGroup;
    long numFound;
    List<ItemResult> docs = new ArrayList<>();

    public String getKeyGroup() {
        return keyGroup;
    }

    public void setKeyGroup(String keyGroup) {
        this.keyGroup = keyGroup;
    }

    public long getNumFound() {
        return numFound;
    }

    public void setNumFound(long numFound) {
        this.numFound = numFound;
    }

    public List<ItemResult> getDocs() {
        return docs;
    }

    public void setDocs(List<ItemResult> docs) {
        this.docs = docs;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
