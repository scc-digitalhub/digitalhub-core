package it.smartcommunitylabdhub.core.controllers.v1.context;

import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.components.solr.SolrComponent;
import it.smartcommunitylabdhub.core.models.indexers.ItemResult;
import it.smartcommunitylabdhub.core.models.indexers.SearchGroupResult;
import it.smartcommunitylabdhub.core.models.indexers.SolrPage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/solr")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Solr search context API", description = "Endpoints related to Solr search")
public class SolrContextController {

    @Autowired(required = false)
    SolrComponent solrComponent;

    @GetMapping(path = "/search/group", produces = "application/json; charset=UTF-8")
    public ResponseEntity<SolrPage<SearchGroupResult>> searchGroup(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) List<String> fq,
        Pageable pageRequest
    ) {
        if (solrComponent == null) {
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        }
        try {
            SolrPage<SearchGroupResult> page = solrComponent.groupSearch(q, setProject(fq, project), pageRequest);
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            log.error(String.format("searchGroup:", e.getMessage()));
            return ResponseEntity.ok(null);
        }
    }

    @GetMapping(path = "/search/item", produces = "application/json; charset=UTF-8")
    public ResponseEntity<SolrPage<ItemResult>> search(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @RequestParam(required = false) String q,
        @RequestParam(required = false) List<String> fq,
        Pageable pageRequest
    ) {
        if (solrComponent == null) {
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        }

        try {
            SolrPage<ItemResult> page = solrComponent.itemSearch(q, setProject(fq, project), pageRequest);
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            log.error(String.format("search:", e.getMessage()));
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private List<String> setProject(List<String> fq, String project) {
        List<String> result = fq.stream().filter(e -> !e.startsWith("project:")).collect(Collectors.toList());
        result.add("project:" + project);
        return result;
    }
}
