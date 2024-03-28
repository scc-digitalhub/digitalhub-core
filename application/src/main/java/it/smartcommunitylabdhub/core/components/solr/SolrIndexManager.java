package it.smartcommunitylabdhub.core.components.solr;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

public class SolrIndexManager {

    private Http2SolrClient solrClient;

    private String solrCollection;

    public SolrIndexManager(Http2SolrClient client, String collection) {
        this.solrClient = client;
        this.solrCollection = collection;
    }

    public void close() {
        if (solrClient != null) {
            solrClient.close();
        }
    }

    @SuppressWarnings("unused")
    private String getTerm(String term) {
        return ClientUtils.escapeQueryChars(term.trim());
    }

    public SolrPage<SearchGroupResult> groupSearch(String q, List<String> fq, Pageable pageRequest) throws Exception {
        Map<String, List<String>> filters = new HashMap<>();
        QueryResponse response = prepareQuery(q, fq, pageRequest, filters, true);

        Map<String, Map<String, List<String>>> highlighting = response.getHighlighting();
        List<SearchGroupResult> result = new ArrayList<>();
        long total = 0;
        GroupResponse groupResponse = response.getGroupResponse();
        if (groupResponse.getValues().size() > 0) {
            GroupCommand groupCommand = groupResponse.getValues().get(0);
            total = groupCommand.getValues().size();
            for (Group group : groupCommand.getValues()) {
                SolrDocumentList documents = group.getResult();
                SearchGroupResult groupResult = new SearchGroupResult();
                groupResult.setId(group.getGroupValue());
                groupResult.setKeyGroup(group.getGroupValue());
                groupResult.setNumFound(documents.getNumFound());
                result.add(groupResult);
                for (SolrDocument doc : documents) {
                    ItemResult itemResult = SolrDocParser.parse(doc);
                    if (highlighting.containsKey(itemResult.getId())) {
                        itemResult.getHighlights().putAll(highlighting.get(itemResult.getId()));
                    }
                    groupResult.getDocs().add(itemResult);
                }
            }
        }

        return new SolrPageImpl<>(result, pageRequest, total, filters);
    }

    public void indexDoc(SolrInputDocument doc) throws Exception {
        solrClient.add(solrCollection, doc);
        solrClient.commit(solrCollection);
    }

    public void removeDoc(String id) throws Exception {
        solrClient.deleteById(solrCollection, id);
        solrClient.commit(solrCollection);
    }

    public void clearIndex() throws Exception {
        solrClient.deleteByQuery(solrCollection, "*:*");
        solrClient.commit(solrCollection);
    }

    public void indexBounce(List<SolrInputDocument> docs) throws Exception {
        for (SolrInputDocument doc : docs) {
            solrClient.add(solrCollection, doc);
        }
        solrClient.commit(solrCollection);
    }

    public SolrPage<ItemResult> itemSearch(String q, List<String> fq, Pageable pageRequest) throws Exception {
        Map<String, List<String>> filters = new HashMap<>();
        QueryResponse response = prepareQuery(q, fq, pageRequest, filters, false);

        Map<String, Map<String, List<String>>> highlighting = response.getHighlighting();
        List<ItemResult> result = new ArrayList<>();
        SolrDocumentList documents = response.getResults();
        for (SolrDocument doc : documents) {
            ItemResult itemResult = SolrDocParser.parse(doc);
            if (highlighting.containsKey(itemResult.getId())) {
                itemResult.getHighlights().putAll(highlighting.get(itemResult.getId()));
            }
            result.add(itemResult);
        }

        return new SolrPageImpl<>(result, pageRequest, documents.getNumFound(), filters);
    }

    private QueryResponse prepareQuery(
        String q,
        List<String> fq,
        Pageable pageRequest,
        Map<String, List<String>> filters,
        boolean grouped
    ) throws Exception {
        Map<String, String[]> queryParamMap = new HashMap<>();
        if (StringUtils.hasText(q)) {
            filters.put("q", Arrays.asList(q));
            String query = String.format(
                "metadata.name:%1$s OR metadata.description:%1$s OR metadata.project:%1$s" +
                " OR metadata.version:%1$s OR metadata.labels:%1$s",
                q.trim()
            );
            MultiMapSolrParams.addParam("q", query, queryParamMap);
        } else {
            MultiMapSolrParams.addParam("q", "*:*", queryParamMap);
        }
        if (fq != null) {
            filters.put("fq", fq);
            fq.forEach(filter -> {
                if (StringUtils.hasText(filter)) {
                    MultiMapSolrParams.addParam("fq", filter.trim(), queryParamMap);
                }
            });
        }

        if (grouped) {
            MultiMapSolrParams.addParam("group", "true", queryParamMap);
            MultiMapSolrParams.addParam("group.field", "keyGroup", queryParamMap);
            MultiMapSolrParams.addParam("group.limit", "10", queryParamMap);
        }

        MultiMapSolrParams.addParam("hl", "true", queryParamMap);
        MultiMapSolrParams.addParam(
            "hl.fl",
            "metadata.name,metadata.description,metadata.project,metadata.version,metadata.labels",
            queryParamMap
        );
        MultiMapSolrParams.addParam("hl.fragsize", "250", queryParamMap);

        MultiMapSolrParams.addParam("start", String.valueOf(pageRequest.getOffset()), queryParamMap);
        MultiMapSolrParams.addParam("rows", String.valueOf(pageRequest.getPageSize()), queryParamMap);
        if(pageRequest.getSort().isSorted()) {
        	pageRequest.getSort().forEach(order -> {
        		MultiMapSolrParams.addParam("sort", 
        				order.getProperty() + " " + order.getDirection().toString(), queryParamMap);
        	});
        }

        return solrClient.query(solrCollection, new MultiMapSolrParams(queryParamMap));
    }
}
