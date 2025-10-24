/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.lucene.service;

import it.smartcommunitylabdhub.lucene.config.LuceneProperties;
import it.smartcommunitylabdhub.search.base.SearchPageImpl;
import it.smartcommunitylabdhub.search.indexers.IndexerException;
import it.smartcommunitylabdhub.search.indexers.ItemResult;
import it.smartcommunitylabdhub.search.indexers.SearchGroupResult;
import it.smartcommunitylabdhub.search.indexers.SearchPage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.grouping.GroupDocs;
import org.apache.lucene.search.grouping.GroupingSearch;
import org.apache.lucene.search.grouping.TopGroups;
import org.apache.lucene.search.highlight.Fragmenter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.search.highlight.SimpleSpanFragmenter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.springframework.data.domain.Pageable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public class LuceneManager {

    private LuceneProperties properties;
    private Analyzer analyzer;
    private Directory directory;
    private IndexWriterConfig config;
    private DirectoryReader ireader;
    private IndexWriter iwriter;

    public LuceneManager(LuceneProperties properties) {
        Assert.notNull(properties, "lucene properties can not be null");
        this.properties = properties;
    }

    public synchronized void init() throws IndexerException {
        try {
            analyzer = new StandardAnalyzer();
            Path path = Paths.get(properties.getIndexPath());
            if (!Files.exists(path)) {
                Files.createDirectory(path);
            }
            directory = FSDirectory.open(path);
            config = new IndexWriterConfig(analyzer);
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);
            iwriter = new IndexWriter(directory, config);
            iwriter.commit();
            ireader = DirectoryReader.open(directory);
            log.info("Lucene index initialized");
        } catch (Exception e) {
            throw new IndexerException(e.getMessage());
        }
    }

    public synchronized void close() throws IndexerException {
        try {
            iwriter.close();
            ireader.close();
            directory.close();
            log.info("Lucene index closed");
        } catch (Exception e) {
            throw new IndexerException(e.getMessage());
        }
    }

    public synchronized DirectoryReader getReader() throws IOException {
        DirectoryReader newReader = DirectoryReader.openIfChanged(ireader);
        if (newReader != null) ireader = newReader;
        return ireader;
    }

    public void indexDoc(Document doc) throws IndexerException {
        log.debug("index doc");
        try {
            synchronized (iwriter) {
                Term term = new Term("id", doc.get("id"));
                iwriter.deleteDocuments(term);
                iwriter.addDocument(doc);
                iwriter.commit();
            }
        } catch (Exception e) {
            throw new IndexerException(e.getMessage());
        }
    }

    public void removeDoc(String id) throws IndexerException {
        log.debug("remove doc {}", String.valueOf(id));
        try {
            synchronized (iwriter) {
                Term term = new Term("id", id);
                iwriter.deleteDocuments(term);
                iwriter.commit();
            }
        } catch (Exception e) {
            throw new IndexerException(e.getMessage());
        }
    }

    public void indexBounce(Iterable<Document> docs) throws IndexerException {
        log.debug("index bounce docs");
        try {
            synchronized (iwriter) {
                for (Document doc : docs) {
                    Term term = new Term("id", doc.get("id"));
                    iwriter.deleteDocuments(term);
                    iwriter.addDocument(doc);
                }
                iwriter.commit();
            }
        } catch (Exception e) {
            throw new IndexerException(e.getMessage());
        }
    }

    public void clearIndex() throws IndexerException {
        log.debug("clear index");
        try {
            synchronized (iwriter) {
                iwriter.deleteAll();
                iwriter.commit();
            }
        } catch (Exception e) {
            throw new IndexerException(e.getMessage());
        }
    }

    public void clearIndexByType(String type) throws IndexerException {
        log.debug("clear index {}", type);
        try {
            synchronized (iwriter) {
                Term term = new Term("type", type);
                iwriter.deleteDocuments(term);
                iwriter.commit();
            }
        } catch (Exception e) {
            throw new IndexerException(e.getMessage());
        }
    }

    public SearchPage<ItemResult> itemSearch(String q, List<String> fq, Pageable pageRequest) throws IndexerException {
        log.debug("item search for {} {}", q, fq);

        try {
            IndexSearcher isearcher = new IndexSearcher(getReader());

            Map<String, List<String>> filters = new HashMap<>();
            QueryMapper queryMapper = prepareQuery(q, fq, pageRequest, filters, false);
            log.debug("query {}", queryMapper.getCompleteQuery().toString());

            GroupingSearch groupingSearch = new GroupingSearch("project");
            groupingSearch.setAllGroups(false);
            groupingSearch.setGroupDocsLimit(pageRequest.getPageSize());
            groupingSearch.setGroupDocsOffset((int) pageRequest.getOffset());

            if (pageRequest.getSort().isSorted()) {
                Sort sort = prepareSorting(pageRequest);
                groupingSearch.setSortWithinGroup(sort);
            }

            // Highlighter
            SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter("<em>", "</em>");
            QueryScorer queryScorer = new QueryScorer(queryMapper.getHighlightQuery());
            Highlighter highlighter = new Highlighter(htmlFormatter, queryScorer);
            Fragmenter fragmenter = new SimpleSpanFragmenter(queryScorer, 250);
            highlighter.setTextFragmenter(fragmenter);

            TopGroups<BytesRef> topGroups = groupingSearch.search(isearcher, queryMapper.getCompleteQuery(), 0, 1);
            StoredFields storedFields = getReader().storedFields();
            List<ItemResult> result = new ArrayList<>();
            long total = 0;
            for (GroupDocs<BytesRef> groupDocs : topGroups.groups) {
                total = groupDocs.totalHits.value;
                for (ScoreDoc hit : groupDocs.scoreDocs) {
                    Document doc = storedFields.document(hit.doc);
                    ItemResult itemResult = LuceneDocParser.parse(doc);
                    if (StringUtils.hasText(q)) {
                        highlightResult(doc, itemResult, highlighter);
                    }
                    result.add(itemResult);
                }
            }
            return new SearchPageImpl<ItemResult>(result, pageRequest, total, filters);
        } catch (Exception e) {
            throw new IndexerException(e.getMessage());
        }
    }

    public SearchPage<SearchGroupResult> groupSearch(String q, List<String> fq, Pageable pageRequest)
        throws IndexerException {
        log.debug("group search for {} {}", q, fq);

        try {
            IndexSearcher isearcher = new IndexSearcher(getReader());

            Map<String, List<String>> filters = new HashMap<>();
            QueryMapper queryMapper = prepareQuery(q, fq, pageRequest, filters, true);
            log.debug("group query {}", queryMapper.getCompleteQuery().toString());

            GroupingSearch groupingSearch = new GroupingSearch("keyGroup");
            groupingSearch.setAllGroups(true);
            groupingSearch.setGroupDocsLimit(10);

            if (pageRequest.getSort().isSorted()) {
                Sort sort = prepareSorting(pageRequest);
                groupingSearch.setSortWithinGroup(sort);
            }

            // Highlighter
            SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter("<em>", "</em>");
            QueryScorer queryScorer = new QueryScorer(queryMapper.getHighlightQuery());
            Highlighter highlighter = new Highlighter(htmlFormatter, queryScorer);
            Fragmenter fragmenter = new SimpleSpanFragmenter(queryScorer, 250);
            highlighter.setTextFragmenter(fragmenter);

            TopGroups<BytesRef> topGroups = groupingSearch.search(
                isearcher,
                queryMapper.getCompleteQuery(),
                (int) pageRequest.getOffset(),
                pageRequest.getPageSize()
            );
            StoredFields storedFields = getReader().storedFields();
            List<SearchGroupResult> result = new ArrayList<>();
            for (GroupDocs<BytesRef> groupDocs : topGroups.groups) {
                SearchGroupResult groupResult = new SearchGroupResult();
                groupResult.setId(groupDocs.groupValue.utf8ToString());
                groupResult.setKeyGroup(groupDocs.groupValue.utf8ToString());
                groupResult.setNumFound(groupDocs.totalHits.value);
                List<ItemResult> docs = new ArrayList<>();
                for (ScoreDoc hit : groupDocs.scoreDocs) {
                    Document doc = storedFields.document(hit.doc);
                    ItemResult itemResult = LuceneDocParser.parse(doc);
                    if (StringUtils.hasText(q)) {
                        highlightResult(doc, itemResult, highlighter);
                    }
                    docs.add(itemResult);
                }
                groupResult.setDocs(docs);
                result.add(groupResult);
            }
            return new SearchPageImpl<SearchGroupResult>(result, pageRequest, topGroups.totalGroupCount, filters);
        } catch (Exception e) {
            throw new IndexerException(e.getMessage());
        }
    }

    private void highlightResult(Document doc, ItemResult itemResult, Highlighter highlighter) throws Exception {
        String[] fields = new String[] {
            "metadata.name",
            "metadata.description",
            "metadata.project",
            "metadata.version",
        };
        for (String field : fields) {
            String[] bestFragments = highlighter.getBestFragments(analyzer, field, doc.get(field), 5);
            if (bestFragments.length > 0) {
                List<String> list = Arrays.asList(bestFragments);
                itemResult.getHighlights().put(field, list);
            }
        }
        String[] labels = doc.getValues("metadata.labels");
        if ((labels != null) && (labels.length > 0)) {
            String[] bestFragments = highlighter.getBestFragments(
                analyzer,
                "metadata.labels",
                String.join(" ", labels),
                5
            );
            if (bestFragments.length > 0) {
                List<String> list = Arrays.asList(bestFragments);
                itemResult.getHighlights().put("metadata.labels", list);
            }
        }
    }

    private QueryMapper prepareQuery(
        String q,
        List<String> fq,
        Pageable pageRequest,
        Map<String, List<String>> filters,
        boolean grouped
    ) throws Exception {
        QueryMapper result = new QueryMapper();
        Builder builder = new BooleanQuery.Builder();
        if (StringUtils.hasText(q)) {
            filters.put("q", Arrays.asList(q));
            String[] fields = new String[] {
                "metadata.name",
                "metadata.description",
                "metadata.project",
                "metadata.version",
                "metadata.labels",
            };
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);
            parser.setAllowLeadingWildcard(true);
            Query query = parser.parse(q.trim());
            builder.add(query, BooleanClause.Occur.MUST);
            result.setHighlightQuery(query);
        }
        if (fq != null) {
            filters.put("fq", fq);
            StandardQueryParser standardQueryParser = new StandardQueryParser(analyzer);
            for (String filter : fq) {
                if (StringUtils.hasText(filter)) {
                    String field = filter.substring(0, filter.indexOf(':'));
                    String value = filter.substring(filter.indexOf(':') + 1);
                    if (field.equals("metadata.updated")) {
                        SimpleDateFormat sdf = new SimpleDateFormat(LuceneDocParser.dateFormat);
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                        String s = StringUtils.deleteAny(StringUtils.deleteAny(value, "]"), "[");
                        String[] split = s.split(" TO ");
                        if (split.length == 2) {
                            if (!split[0].equals("*")) {
                                String from = StringUtils.replace(StringUtils.deleteAny(split[0], "\""), "Z", "+0000");
                                split[0] = String.valueOf(sdf.parse(from).getTime());
                            }
                            if (!split[1].equals("*")) {
                                String to = StringUtils.replace(StringUtils.deleteAny(split[1], "\""), "Z", "+0000");
                                split[1] = String.valueOf(sdf.parse(to).getTime());
                            }
                        }
                        Query query = standardQueryParser.parse(
                            String.format("[%s TO %s]", split[0], split[1]),
                            "metadata.updatedLong"
                        );
                        builder.add(query, BooleanClause.Occur.MUST);
                    } else if (field.equals("project") || field.equals("status")) {
                        Query query = new TermQuery(new Term(field, value));
                        builder.add(query, BooleanClause.Occur.MUST);
                    } else {
                        Query query = standardQueryParser.parse(value, field);
                        builder.add(query, BooleanClause.Occur.MUST);
                    }
                }
            }
        }
        result.setCompleteQuery(builder.build());
        return result;
    }

    private Sort prepareSorting(Pageable pageRequest) {
        List<SortField> sortFields = new ArrayList<>();
        pageRequest
            .getSort()
            .forEach(order -> {
                String field = order.getProperty().equals("metadata.updated")
                    ? "metadata.updatedLong"
                    : order.getProperty();
                SortField sf = new SortField(field, SortField.Type.STRING, order.getDirection().isDescending());
                sortFields.add(sf);
            });
        return new Sort(sortFields.toArray(new SortField[0]));
    }
}
