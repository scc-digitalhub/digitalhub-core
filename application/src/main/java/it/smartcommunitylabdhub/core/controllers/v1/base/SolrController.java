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

package it.smartcommunitylabdhub.core.controllers.v1.base;

import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.search.indexers.ItemResult;
import it.smartcommunitylabdhub.search.indexers.SearchGroupResult;
import it.smartcommunitylabdhub.search.indexers.SearchPage;
import it.smartcommunitylabdhub.solr.SolrComponent;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @GetMapping(path = "/clear", produces = "application/json; charset=UTF-8")
    public ResponseEntity<Void> clearIndex() {
        if (solrComponent == null) {
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        }
        try {
            solrComponent.clearIndex();
            return ResponseEntity.ok(null);
        } catch (Exception e) {
            log.error("clearIndex:{}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(path = "/search/group", produces = "application/json; charset=UTF-8")
    public ResponseEntity<SearchPage<SearchGroupResult>> searchGroup(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) List<String> fq,
        Pageable pageRequest
    ) {
        if (solrComponent == null) {
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        }
        try {
            SearchPage<SearchGroupResult> page = solrComponent.groupSearch(q, fq, pageRequest);
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            log.error("searchGroup:{}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping(path = "/search/item", produces = "application/json; charset=UTF-8")
    public ResponseEntity<SearchPage<ItemResult>> search(
        @RequestParam(required = false) String q,
        @RequestParam(required = false) List<String> fq,
        Pageable pageRequest
    ) {
        if (solrComponent == null) {
            return new ResponseEntity<>(HttpStatus.NOT_IMPLEMENTED);
        }
        try {
            SearchPage<ItemResult> page = solrComponent.itemSearch(q, fq, pageRequest);
            return ResponseEntity.ok(page);
        } catch (Exception e) {
            log.error("search:{}", e.getMessage());
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
