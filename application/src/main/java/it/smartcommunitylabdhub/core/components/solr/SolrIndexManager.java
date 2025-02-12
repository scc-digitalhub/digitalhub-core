package it.smartcommunitylabdhub.core.components.solr;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.Http2SolrClient;
import org.apache.solr.client.solrj.impl.Http2SolrClient.Builder;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrException;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SolrIndexManager {

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<HashMap<String, Serializable>>() {};

    private final SolrProperties props;
    private final Http2SolrClient solrClient;
    private final RestTemplate restTemplate;

    private ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public SolrIndexManager(SolrProperties props) {
        Assert.notNull(props, "solr properties can not be null");
        Assert.hasText(props.getUrl(), "solr url can not be null or empty");
        Assert.hasText(props.getCollection(), "solr collection can not be null or empty");

        this.props = props;

        //build client
        log.debug("build solr client for {} collection {}", props.getUrl(), props.getCollection());
        Builder builder = new Http2SolrClient.Builder(props.getUrl())
            .withConnectionTimeout(props.getTimeout(), TimeUnit.MILLISECONDS);

        if (StringUtils.hasLength(props.getUser()) && StringUtils.hasLength(props.getPassword())) {
            //add basic auth
            builder.withBasicAuthCredentials(props.getUser(), props.getPassword());
        }
        solrClient = builder.build();
        restTemplate = new RestTemplate();
    }

    public void init() throws SolrIndexerException {
        log.debug("init solr collection {}", props.getCollection());
        try {
            //check if collection exists
            String solrUrl = props.getUrl();
            String baseUri = solrUrl.endsWith("/") ? solrUrl : solrUrl + "/";
            
            HttpHeaders headers = new HttpHeaders();
            if(StringUtils.hasLength(props.getAdminUser()) && StringUtils.hasLength(props.getAdminPassword())) {
            	String auth = props.getAdminUser() + ":" + props.getAdminPassword();
                String authHeader  = Base64.getEncoder().encodeToString(auth.getBytes());
                headers.setBasicAuth(authHeader);
                log.debug("init solr collection auth {}", authHeader);
            }
            
            try {
                String listUrl = baseUri + "admin/collections?action=LIST";
                ResponseEntity<String> listResponse = restTemplate.exchange(listUrl, HttpMethod.GET, new HttpEntity<String>(headers), String.class);

                if (listResponse.getStatusCode().isError()) {
                	 log.warn("can not talk to solr {}: {}",
                             listResponse.getStatusCode().toString(),
                             listResponse.getBody());
                }

                initCollection(headers);
            } catch (HttpClientErrorException e) {
                //fallback to core if 400
                //creation is NOT supported
                String listUrl = baseUri + "admin/cores?action=STATUS";
                ResponseEntity<String> listResponse = restTemplate.exchange(listUrl, HttpMethod.GET, new HttpEntity<String>(headers), String.class);

                if (listResponse.getStatusCode().isError()) {
                	log.warn("can not talk to solr {}: {}",
                         listResponse.getStatusCode().toString(),
                         listResponse.getBody());
                }

                Map<String, Serializable> map = JacksonMapper.OBJECT_MAPPER.readValue(listResponse.getBody(), typeRef);
                Map<String, Serializable> collections = (Map<String, Serializable>) map.get("status");
                if (collections == null || !collections.containsKey(props.getCollection())) {
                    throw new SolrIndexerException("core not available " + props.getCollection());
                }
            }
        } catch (SolrException | RestClientException | JsonProcessingException e) {
        	log.warn("can not initialize solr: {}", e.getMessage());
        }
    }

    /*
     * Public API
     */

    public void ping() throws SolrIndexerException {
        log.debug("ping solr collection {}", props.getCollection());
        try {
            solrClient.ping(props.getCollection());
        } catch (SolrServerException | SolrException | IOException e) {
            throw new SolrIndexerException(e.getMessage());
        }
    }

    public synchronized void initFields(Iterable<IndexField> fields) throws SolrIndexerException {
        log.debug("init fields");
        if (log.isTraceEnabled()) {
            log.trace("fields: {}", fields);
        }
        
        HttpHeaders headers = new HttpHeaders();
        if(StringUtils.hasLength(props.getUser()) && StringUtils.hasLength(props.getPassword())) {
        	String auth = props.getUser() + ":" + props.getPassword();
            String authHeader  = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.setBasicAuth(authHeader);
            log.debug("init solr fields auth {}", authHeader);
        }

        String solrUrl = props.getUrl();
        String baseUri = solrUrl.endsWith("/") ? solrUrl : solrUrl + "/";
        String fieldsUri = baseUri + props.getCollection() + "/schema/fields";
        //check existing fields
        ResponseEntity<String> responseEntity =restTemplate.exchange(fieldsUri, HttpMethod.GET, new HttpEntity<String>(headers), String.class);
        //ResponseEntity<String> responseEntity = restTemplate.getForEntity(fieldsUri, String.class);
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            String schemaUri = baseUri + props.getCollection() + "/schema";
            try {
                JsonNode wrapperNode = mapper.readTree(responseEntity.getBody());
                JsonNode fieldsNode = wrapperNode.path("fields");
                if (!fieldsNode.isMissingNode()) {
                    List<String> names = new ArrayList<>();
                    //collect fields names
                    fieldsNode.elements().forEachRemaining(node -> names.add(node.get("name").asText()));

                    //add missing fields
                    for (IndexField field : fields) {
                        if (!names.contains(field.getName())) {
                            addField(
                                field.getName(),
                                field.getType(),
                                field.isIndexed(),
                                field.isMultiValued(),
                                field.isStored(),
                                field.isUninvertible(),
                                schemaUri,
                                headers
                            );
                        }
                    }
                }
            } catch (IOException e) {
                log.error("initFields error {}", e.getMessage());
            }
        }
    }

    public void close() throws SolrIndexerException {
        if (solrClient != null) {
            solrClient.close();
        }
    }

    public SolrPage<SearchGroupResult> groupSearch(String q, List<String> fq, Pageable pageRequest)
        throws SolrIndexerException {
        log.debug("group search for {} {}", q, fq);

        try {
            Map<String, List<String>> filters = new HashMap<>();
            QueryResponse response = solrClient.query(
                props.getCollection(),
                prepareQuery(q, fq, pageRequest, filters, true)
            );

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
        } catch (SolrServerException | SolrException | IOException e) {
            throw new SolrIndexerException(e.getMessage());
        }
    }

    public SolrPage<ItemResult> itemSearch(String q, List<String> fq, Pageable pageRequest)
        throws SolrIndexerException {
        log.debug("item search for {} {}", q, fq);

        try {
            Map<String, List<String>> filters = new HashMap<>();
            QueryResponse response = solrClient.query(
                props.getCollection(),
                prepareQuery(q, fq, pageRequest, filters, false)
            );

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
        } catch (SolrServerException | SolrException | IOException e) {
            throw new SolrIndexerException(e.getMessage());
        }
    }

    public void indexDoc(SolrInputDocument doc) throws SolrIndexerException {
        log.debug("index doc");
        if (log.isTraceEnabled()) {
            log.trace("doc: {}", doc);
        }

        try {
            solrClient.add(props.getCollection(), doc);
            solrClient.commit(props.getCollection());
        } catch (SolrServerException | SolrException | IOException e) {
            throw new SolrIndexerException(e.getMessage());
        }
    }

    public void removeDoc(String id) throws SolrIndexerException {
        log.debug("remove doc {}", String.valueOf(id));
        try {
            solrClient.deleteById(props.getCollection(), id);
            solrClient.commit(props.getCollection());
        } catch (SolrServerException | SolrException | IOException e) {
            throw new SolrIndexerException(e.getMessage());
        }
    }

    public void clearIndex() throws SolrIndexerException {
        log.debug("clear index");
        try {
            solrClient.deleteByQuery(props.getCollection(), "*:*");
            solrClient.commit(props.getCollection());
        } catch (SolrServerException | SolrException | IOException e) {
            throw new SolrIndexerException(e.getMessage());
        }
    }

    public void clearIndexByType(String type) throws SolrIndexerException {
        log.debug("clear index for type {}", String.valueOf(type));
        try {
            solrClient.deleteByQuery(props.getCollection(), "type:" + type.trim());
            solrClient.commit(props.getCollection());
        } catch (SolrServerException | SolrException | IOException e) {
            throw new SolrIndexerException(e.getMessage());
        }
    }

    public void indexBounce(Iterable<SolrInputDocument> docs) throws SolrIndexerException {
        log.debug("index bounce docs");
        try {
            for (SolrInputDocument doc : docs) {
                solrClient.add(props.getCollection(), doc);
            }
            solrClient.commit(props.getCollection());
        } catch (SolrServerException | SolrException | IOException e) {
            throw new SolrIndexerException(e.getMessage());
        }
    }

    /*
     * Internal
     */

    private void addField(
        String name,
        String type,
        boolean indexed,
        boolean multiValued,
        boolean stored,
        boolean uninvertible,
        String uri,
        HttpHeaders headers
    ) throws SolrIndexerException {
        ObjectNode rootNode = mapper.createObjectNode();
        ObjectNode addNode = rootNode.putObject("add-field");
        addNode
            .put("name", name)
            .put("type", type)
            .put("multiValued", multiValued)
            .put("indexed", indexed)
            .put("stored", stored)
            .put("uninvertible", uninvertible);
        HttpEntity<ObjectNode> request = new HttpEntity<>(rootNode, headers);
        ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.POST, request, String.class);
        if (response.getStatusCode().isError()) {
            throw new SolrIndexerException(
                "SolrIndexManager schema error: " + String.valueOf(response.getStatusCode().value())
            );
        }
    }

    @SuppressWarnings("unused")
    private String getTerm(String term) {
        return ClientUtils.escapeQueryChars(term.trim());
    }

    private MultiMapSolrParams prepareQuery(
        String q,
        List<String> fq,
        Pageable pageRequest,
        Map<String, List<String>> filters,
        boolean grouped
    ) {
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
        if (pageRequest.getSort().isSorted()) {
            pageRequest
                .getSort()
                .forEach(order -> {
                    MultiMapSolrParams.addParam(
                        "sort",
                        order.getProperty() + " " + order.getDirection().toString(),
                        queryParamMap
                    );
                });
        }

        return new MultiMapSolrParams(queryParamMap);
    }

    private void initCollection(HttpHeaders headers) throws SolrIndexerException {
        try {
            //check if collection exists
            String solrUrl = props.getUrl();
            String baseUri = solrUrl.endsWith("/") ? solrUrl : solrUrl + "/";
            String listUrl = baseUri + "admin/collections?action=LIST";
            ResponseEntity<String> listResponse = restTemplate.exchange(listUrl, HttpMethod.GET, new HttpEntity<String>(headers), String.class);
            if (listResponse.getStatusCode().isError()) {
            	log.warn("can not talk to solr {}: {}",
                     listResponse.getStatusCode().toString(),
                     listResponse.getBody());
           	 	return;
            }

            Map<String, Serializable> map = JacksonMapper.OBJECT_MAPPER.readValue(listResponse.getBody(), typeRef);
            List<String> collections = (List<String>) map.get("collections");
            if (collections == null || !collections.contains(props.getCollection())) {
                //not found, create collection
                log.debug("create collection {} on {}", props.getCollection(), solrUrl);

                String createUrl =
                    baseUri +
                    "admin/collections?action=CREATE&name={collection}&numShards={numShards}&replicationFactor={replicationFactor}&maxShardsPerNode=1";
                ResponseEntity<String> createResponse = restTemplate.exchange(createUrl, HttpMethod.GET, new HttpEntity<String>(headers), String.class, 
                		props.getCollection(),
                		props.getShards(),
                		props.getReplicas()
                );
                
                if (createResponse.getStatusCode().isError()) {
                	log.warn("can not talk to solr {}: {}",
                            listResponse.getStatusCode().toString(),
                            listResponse.getBody());
                }
            }
        } catch (SolrException | IOException e) {
        	log.warn("can not initialize solr: {}", e.getMessage());
        }
    }
}
