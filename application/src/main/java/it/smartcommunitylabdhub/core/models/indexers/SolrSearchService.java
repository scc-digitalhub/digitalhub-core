package it.smartcommunitylabdhub.core.models.indexers;

import java.util.List;

import org.springframework.data.domain.Pageable;

public interface SolrSearchService {
	
	public SolrPage<SearchGroupResult> groupSearch(String q, List<String> fq, Pageable pageRequest) throws IndexerException;
	
	public SolrPage<ItemResult> itemSearch(String q, List<String> fq, Pageable pageRequest) throws IndexerException;
}
