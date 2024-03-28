package it.smartcommunitylabdhub.core.controllers.v1.base;

import it.smartcommunitylabdhub.commons.models.entities.artifact.Artifact;
import it.smartcommunitylabdhub.commons.models.entities.dataitem.DataItem;
import it.smartcommunitylabdhub.commons.models.entities.function.Function;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.components.solr.ItemResult;
import it.smartcommunitylabdhub.core.components.solr.SearchGroupResult;
import it.smartcommunitylabdhub.core.components.solr.SolrComponent;
import it.smartcommunitylabdhub.core.components.solr.SolrPage;
import it.smartcommunitylabdhub.core.models.builders.artifact.ArtifactDTOBuilder;
import it.smartcommunitylabdhub.core.models.builders.dataitem.DataItemDTOBuilder;
import it.smartcommunitylabdhub.core.models.builders.function.FunctionDTOBuilder;
import it.smartcommunitylabdhub.core.models.entities.artifact.ArtifactEntity;
import it.smartcommunitylabdhub.core.models.entities.dataitem.DataItemEntity;
import it.smartcommunitylabdhub.core.models.entities.function.FunctionEntity;
import it.smartcommunitylabdhub.core.repositories.ArtifactRepository;
import it.smartcommunitylabdhub.core.repositories.DataItemRepository;
import it.smartcommunitylabdhub.core.repositories.FunctionRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/solr")
@ApiVersion("v1")
@Slf4j
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class SolrController {

    @Autowired(required = false)
    SolrComponent solrComponent;

    @Autowired
    DataItemRepository dataItemRepository;

    @Autowired
    DataItemDTOBuilder dataItemDTOBuilder;

    @Autowired
    FunctionRepository functionRepository;

    @Autowired
    FunctionDTOBuilder functionDTOBuilder;

    @Autowired
    ArtifactRepository artifactRepository;

    @Autowired
    ArtifactDTOBuilder artifactDTOBuilder;

    @GetMapping(path = "/clear", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Void> clearIndex() {
        if (solrComponent == null) return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        solrComponent.clearIndex();
        return ResponseEntity.ok(null);
    }

    @GetMapping(path = "/init", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Void> initIndex() {
        if (solrComponent == null) return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        boolean finished = false;
        int pageNumber = 0;
        do {
            Page<DataItemEntity> page = dataItemRepository.findAll(PageRequest.of(pageNumber, 1000));
            if (page.getContent().size() == 0) {
                finished = true;
            } else {
                List<DataItem> items = new ArrayList<>();
                page.getContent().forEach(e -> items.add(dataItemDTOBuilder.build(e)));
                solrComponent.indexBounceDataItem(items);
            }
            pageNumber++;
        } while (!finished);
        finished = false;
        pageNumber = 0;
        do {
            Page<FunctionEntity> page = functionRepository.findAll(PageRequest.of(pageNumber, 1000));
            if (page.getContent().size() == 0) {
                finished = true;
            } else {
                List<Function> items = new ArrayList<>();
                page.getContent().forEach(e -> items.add(functionDTOBuilder.build(e)));
                solrComponent.indexBounceFunction(items);
            }
            pageNumber++;
        } while (!finished);
        finished = false;
        pageNumber = 0;
        do {
            Page<ArtifactEntity> page = artifactRepository.findAll(PageRequest.of(pageNumber, 1000));
            if (page.getContent().size() == 0) {
                finished = true;
            } else {
                List<Artifact> items = new ArrayList<>();
                page.getContent().forEach(e -> items.add(artifactDTOBuilder.build(e)));
                solrComponent.indexBounceArtifact(items);
            }
            pageNumber++;
        } while (!finished);
        //        finished = false;
        //        pageNumber = 0;
        //        do {
        //            Page<RunEntity> page = runRepository.findAll(PageRequest.of(pageNumber, 1000));
        //            if (page.getContent().size() == 0) {
        //                finished = true;
        //            } else {
        //                List<Run> items = new ArrayList<>();
        //                page.getContent().forEach(e -> items.add(runDTOBuilder.build(e)));
        //                solrComponent.indexBounceRun(items);
        //            }
        //            pageNumber++;
        //        } while (!finished);
        //        finished = false;
        //        pageNumber = 0;
        //        do {
        //            Page<SecretEntity> page = secretRepository.findAll(PageRequest.of(pageNumber, 1000));
        //            if (page.getContent().size() == 0) {
        //                finished = true;
        //            } else {
        //                List<Secret> items = new ArrayList<>();
        //                page.getContent().forEach(e -> items.add(secretDTOBuilder.build(e)));
        //                solrComponent.indexBounceSecret(items);
        //            }
        //            pageNumber++;
        //        } while (!finished);
        //        finished = false;
        //        pageNumber = 0;
        //        do {
        //            Page<WorkflowEntity> page = workflowRepository.findAll(PageRequest.of(pageNumber, 1000));
        //            if (page.getContent().size() == 0) {
        //                finished = true;
        //            } else {
        //                List<Workflow> items = new ArrayList<>();
        //                page.getContent().forEach(e -> items.add(workflowDTOBuilder.build(e)));
        //                solrComponent.indexBounceWorkflow(items);
        //            }
        //            pageNumber++;
        //        } while (!finished);

        return ResponseEntity.ok(null);
    }

    @GetMapping(path = "/search/group", produces = "application/json; charset=UTF-8")
    public ResponseEntity<SolrPage<SearchGroupResult>> searchGroup(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) List<String> fq,
        Pageable pageRequest
    ) {
        if (solrComponent == null) return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        try {
            SolrPage<SearchGroupResult> page = solrComponent.groupSearch(q, fq, pageRequest);
            return ResponseEntity.ok(page);
        } catch (Exception e) {
        	SolrController.log.error(String.format("searchGroup:", e.getMessage()));
            return ResponseEntity.ok(null);
        }
    }

    @GetMapping(path = "/search/item", produces = "application/json; charset=UTF-8")
    public ResponseEntity<SolrPage<ItemResult>> search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) List<String> fq,
        Pageable pageRequest
    ) {
        if (solrComponent == null) return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        try {
            SolrPage<ItemResult> page = solrComponent.itemSearch(q, fq, pageRequest);
            return ResponseEntity.ok(page);
        } catch (Exception e) {
        	SolrController.log.error(String.format("search:", e.getMessage()));
            return ResponseEntity.ok(null);
        }
    }
}
