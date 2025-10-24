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

package it.smartcommunitylabdhub.core.controllers.v1.context;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.log.Log;
import it.smartcommunitylabdhub.commons.models.metrics.NumberOrNumberArray;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.commons.services.LogService;
import it.smartcommunitylabdhub.commons.services.MetricsService;
import it.smartcommunitylabdhub.commons.services.RunManager;
import it.smartcommunitylabdhub.core.ApplicationKeys;
import it.smartcommunitylabdhub.core.annotations.ApiVersion;
import it.smartcommunitylabdhub.core.components.proxy.ProxyService;
import it.smartcommunitylabdhub.core.runs.filters.RunEntityFilter;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceStatus;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.relationships.RelationshipDetail;
import it.smartcommunitylabdhub.relationships.RelationshipsAwareEntityService;
import it.smartcommunitylabdhub.runtimes.lifecycle.RunEvent;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort.Direction;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.SortDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.validation.BindException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@ApiVersion("v1")
@RequestMapping("/-/{project}/runs")
@PreAuthorize(
    "hasAuthority('ROLE_ADMIN') or (hasAuthority(#project+':ROLE_USER') or hasAuthority(#project+':ROLE_ADMIN'))"
)
@Validated
@Slf4j
@Tag(name = "Run context API", description = "Endpoints related to runs management for project")
public class RunContextController {

    @Autowired
    RunManager runManager;

    @Autowired
    LifecycleManager<Run> lifecycleManager;

    @Autowired
    LogService logService;

    @Autowired
    RelationshipsAwareEntityService<Run> relationshipsService;

    @Autowired
    MetricsService<Run> metricsService;

    @Autowired
    private ProxyService proxyService;

    @Operation(summary = "Create a run in a project context")
    @PostMapping(
        value = "",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Run createRun(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @Valid @NotNull @RequestBody Run dto
    ) throws DuplicatedEntityException, NoSuchEntityException, SystemException, BindException {
        //enforce project match
        dto.setProject(project);

        //create as new, will check for duplicated
        Run run = runManager.createRun(dto);

        //if !local then also build+run
        RunBaseSpec runBaseSpec = new RunBaseSpec();
        runBaseSpec.configure(run.getSpec());

        if (Boolean.FALSE.equals(runBaseSpec.getLocalExecution())) {
            run = lifecycleManager.perform(run, RunEvent.BUILD.name());
            run = lifecycleManager.perform(run, RunEvent.RUN.name());
        }

        return run;
    }

    @Operation(summary = "Retrieve all runs for the project, with optional filter")
    @GetMapping(path = "", produces = "application/json; charset=UTF-8")
    public Page<Run> searchRuns(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @ParameterObject @Valid @Nullable RunEntityFilter filter,
        @ParameterObject @PageableDefault(page = 0, size = ApplicationKeys.DEFAULT_PAGE_SIZE) @SortDefault.SortDefaults(
            { @SortDefault(sort = "created", direction = Direction.DESC) }
        ) Pageable pageable
    ) {
        SearchFilter<Run> sf = null;
        if (filter != null) {
            sf = filter.toSearchFilter();
        }

        return runManager.searchRunsByProject(project, pageable, sf);
    }

    @Operation(summary = "Retrieve a specific run given the run id")
    @GetMapping(path = "/{id}", produces = "application/json; charset=UTF-8")
    public Run getRunById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return run;
    }

    @Operation(summary = "Update if exist a run in a project context")
    @PutMapping(
        value = "/{id}",
        consumes = { MediaType.APPLICATION_JSON_VALUE, "application/x-yaml" },
        produces = "application/json; charset=UTF-8"
    )
    public Run updateRunById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @RequestBody @Valid @NotNull Run runDTO
    ) throws NoSuchEntityException, IllegalArgumentException, SystemException, BindException {
        Run run = runManager.getRun(id);

        //check for project match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return runManager.updateRun(id, runDTO);
    }

    @Operation(summary = "Delete a specific run, with optional cascade")
    @DeleteMapping(path = "/{id}")
    public Run deleteRun(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project  match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        //delete via manager
        return lifecycleManager.perform(run, RunEvent.DELETE.name());
    }

    /*
     * Actions
     */
    @Operation(summary = "Perform action on a specific run")
    @PostMapping(path = "/{id}/{action}")
    public Run performOnRun(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String action
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project  match
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        // via manager
        return lifecycleManager.perform(run, action.toUpperCase());
    }

    /*
     * Logs
     */

    @Operation(summary = "List logs for a given run", description = "Return a list of logs defined for a specific run")
    @GetMapping(path = "/{id}/logs", produces = "application/json; charset=UTF-8")
    public List<Log> getLogsByRunId(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run run = runManager.getRun(id);

        //check for project
        if (!run.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return logService.getLogsByRunId(id);
    }

    @Operation(summary = "Get relationships info for a given entity, if available")
    @GetMapping(path = "/{id}/relationships", produces = "application/json; charset=UTF-8")
    public List<RelationshipDetail> getRelationshipsById(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws NoSuchEntityException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return relationshipsService.getRelationships(id);
    }

    @Operation(summary = "Get metrics info for a given entity, if available")
    @GetMapping(path = "/{id}/metrics", produces = "application/json; charset=UTF-8")
    public Map<String, NumberOrNumberArray> getMetrics(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id
    ) throws StoreException, SystemException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return metricsService.getMetrics(id);
    }

    @Operation(summary = "Get metrics info for a given entity and metric, if available")
    @GetMapping(path = "/{id}/metrics/{name}", produces = "application/json; charset=UTF-8")
    public NumberOrNumberArray getMetricsByName(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable String name
    ) throws StoreException, SystemException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        return metricsService.getMetrics(id, name);
    }

    @Operation(summary = "Store metrics info for a given entity")
    @PutMapping(path = "/{id}/metrics/{name}", produces = "application/json; charset=UTF-8")
    public void storeMetrics(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        @PathVariable String name,
        @RequestBody NumberOrNumberArray data
    ) throws StoreException, SystemException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if ((entity != null) && !entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        metricsService.saveMetrics(id, name, data);
    }

    @RequestMapping(value = "/{id}/proxy")
    public ResponseEntity<String> proxyRequest(
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String project,
        @PathVariable @Valid @NotNull @Pattern(regexp = Keys.SLUG_PATTERN) String id,
        HttpServletRequest request
    ) throws IOException {
        Run entity = runManager.getRun(id);

        //check for project and name match
        if (!entity.getProject().equals(project)) {
            throw new IllegalArgumentException("invalid project");
        }

        //custom header for request url forwarding
        String requestUrl = request.getHeader("X-Proxy-URL");
        if (requestUrl == null || requestUrl.isEmpty()) {
            throw new IllegalArgumentException("missing proxy url");
        }

        if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://")) {
            requestUrl = "http://" + requestUrl;
        }

        String requestMethod = request.getHeader("X-Proxy-Method");
        if (requestMethod == null || requestMethod.isEmpty()) {
            requestMethod = "GET";
        }

        //check that run contains the same url
        if (entity.getStatus() == null) {
            throw new IllegalArgumentException("invalid run status");
        }

        K8sServiceStatus k8sService = K8sServiceStatus.with(entity.getStatus());
        if (k8sService == null || k8sService.getService() == null || k8sService.getService().getUrl() == null) {
            throw new IllegalArgumentException("invalid run service");
        }
        String serviceUrl = k8sService.getService().getUrl();
        if (serviceUrl == null || serviceUrl.isEmpty()) {
            throw new IllegalArgumentException("invalid run service url");
        }

        if (!serviceUrl.startsWith("http://") && !serviceUrl.startsWith("https://")) {
            serviceUrl = "http://" + serviceUrl;
        }

        //check for host match
        String requestHost = UriComponentsBuilder.fromUriString(requestUrl).build().getHost();
        String serviceHost = UriComponentsBuilder.fromUriString(serviceUrl).build().getHost();

        if (!requestHost.startsWith(serviceHost)) {
            throw new IllegalArgumentException("invalid destination host");
        }

        log.info("Receive {} for url {}", request.getMethod(), requestUrl);

        ResponseEntity<String> response = proxyService.proxyRequest(requestUrl, requestMethod, request);

        //build response
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        if (response.getHeaders() != null) {
            //keep content type
            MediaType contentType = response.getHeaders().getContentType() != null
                ? response.getHeaders().getContentType()
                : MediaType.TEXT_PLAIN;
            headers.add(HttpHeaders.CONTENT_TYPE, contentType.toString());

            //copy everything else as X-Proxy response
            response
                .getHeaders()
                .entrySet()
                .forEach(e -> {
                    String h = "X-Proxy-" + e.getKey();
                    headers.put(h, e.getValue());
                    //make sure all X-Proxy headers are exposed
                    headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, h);
                });

            //append status code
            if (response.getStatusCode() != null) {
                headers.add("X-Proxy-Status", response.getStatusCode().toString());
                //make sure all X-Proxy headers are exposed
                headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "X-Proxy-Status");
            }
        }
        return new ResponseEntity<>(response.getBody(), headers, HttpStatus.OK);
    }
}
