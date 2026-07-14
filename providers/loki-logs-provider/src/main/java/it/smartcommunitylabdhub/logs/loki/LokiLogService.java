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

package it.smartcommunitylabdhub.logs.loki;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.exceptions.DuplicatedEntityException;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.queries.SearchFilter;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.logs.Log;
import it.smartcommunitylabdhub.logs.LogService;
import it.smartcommunitylabdhub.logs.loki.client.LogEntry;
import it.smartcommunitylabdhub.logs.loki.client.LokiClient;
import it.smartcommunitylabdhub.logs.loki.client.LokiException;
import it.smartcommunitylabdhub.logs.loki.client.QueryResult;
import it.smartcommunitylabdhub.logs.loki.client.Streams;
import it.smartcommunitylabdhub.logs.loki.config.LokiProperties;
import it.smartcommunitylabdhub.runs.Run;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.util.Pair;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;

@Slf4j
public class LokiLogService implements LogService {

    private static final long END_OFFSET = 300L; //5 minutes offset for end time if not available
    private final LokiProperties lokiProperties;
    private final LokiClient lokiClient;

    private EntityRepository<Run> runRepository;

    @Autowired
    ApplicationProperties applicationProperties;

    public LokiLogService(LokiProperties lokiProperties) {
        Assert.notNull(lokiProperties, "properties are required");
        this.lokiProperties = lokiProperties;

        //build client
        log.debug("Initializing LokiClient for {}", lokiProperties.getUrl());
        this.lokiClient = new LokiClient(lokiProperties);
    }

    @Autowired(required = false)
    public void setRunRepository(EntityRepository<Run> runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public List<Log> listLogsByProject(@NotNull String project) throws SystemException {
        log.debug("list logs by project {}", project);

        //build queryQL from filter
        List<Pair<String, String>> filters = new ArrayList<>();
        filters.add(
            Pair.of(
                "project",
                lokiProperties.usePrefixForValues() ? applicationProperties.getName() + "-" + project : project
            )
        );

        List<Log> logs = fetch(filters, null, null);
        if (log.isTraceEnabled()) {
            log.trace("logs: {}", logs);
        }

        return logs;
    }

    @Override
    public Page<Log> listLogsByProject(@NotNull String project, @NonNull Pageable pageable) throws SystemException {
        //pagination is not supported
        List<Log> logs = listLogsByProject(project);
        return new PageImpl<>(logs, pageable, logs.size());
    }

    @Override
    public Page<Log> searchLogs(@NonNull Pageable pageable, @Nullable SearchFilter<Log> filter) throws SystemException {
        log.debug("search logs with {} page {}", String.valueOf(filter), pageable);

        //build queryQL from filter
        List<Pair<String, String>> filters = new ArrayList<>();

        //we support only run+user+project filtering by default
        filter
            .getCriteria()
            .stream()
            .filter(c -> c.getField().equals("run") || c.getField().equals("user") || c.getField().equals("project"))
            .map(c -> Pair.of(c.getField(), c.getValue()))
            .forEach(c -> {
                if (c.getFirst() != null && c.getSecond() != null) {
                    filters.add(
                        Pair.of(
                            c.getFirst(),
                            lokiProperties.usePrefixForValues()
                                ? applicationProperties.getName() + "-" + c.getSecond().toString()
                                : c.getSecond().toString()
                        )
                    );
                }
            });

        List<Log> logs = fetch(filters, null, null);
        if (log.isTraceEnabled()) {
            log.trace("logs: {}", logs);
        }
        return new PageImpl<>(logs, pageable, logs.size());
    }

    @Override
    public Page<Log> searchLogsByProject(
        @NotNull String project,
        @NonNull Pageable pageable,
        @Nullable SearchFilter<Log> filter
    ) throws SystemException {
        log.debug("search logs for project {} with {} page {}", project, String.valueOf(filter), pageable);
        try {
            //build queryQL from filter
            List<Pair<String, String>> filters = new ArrayList<>();

            //we support only run+user filtering by default
            filter
                .getCriteria()
                .stream()
                .filter(c -> c.getField().equals("run") || c.getField().equals("user"))
                .map(c -> Pair.of(c.getField(), c.getValue()))
                .forEach(c -> {
                    if (c.getFirst() != null && c.getSecond() != null) {
                        filters.add(
                            Pair.of(
                                c.getFirst(),
                                lokiProperties.usePrefixForValues()
                                    ? applicationProperties.getName() + "-" + c.getSecond().toString()
                                    : c.getSecond().toString()
                            )
                        );
                    }
                });

            //project is statically added to the filters
            filters.add(
                Pair.of(
                    "project",
                    lokiProperties.usePrefixForValues() ? applicationProperties.getName() + "-" + project : project
                )
            );

            Long start = null;
            Long end = null;
            String runId = filter
                .getCriteria()
                .stream()
                .filter(c -> c.getField().equals("run"))
                .findFirst()
                .map(c -> c.getValue().toString())
                .orElse(null);
            if (runRepository != null && StringUtils.hasText(runId)) {
                Run run = runRepository.find(runId);
                if (run != null) {
                    //use creation date for start
                    BaseMetadata metadata = BaseMetadata.from(run.getMetadata());
                    start = metadata.getCreated() != null ? metadata.getCreated().toEpochSecond() : null;
                    end = metadata.getUpdated() != null ? metadata.getUpdated().toEpochSecond() + END_OFFSET : null;
                }
            }

            List<Log> logs = fetch(filters, start, end);
            if (log.isTraceEnabled()) {
                log.trace("logs: {}", logs);
            }
            return new PageImpl<>(logs, pageable, logs.size());
        } catch (StoreException se) {
            log.error("Error fetching from store: {}", se.getMessage());
            throw new SystemException(se.getMessage());
        }
    }

    @Override
    public List<Log> getLogsByRunId(@NotNull String runId) throws SystemException {
        log.debug("list logs by run {}", runId);

        try {
            Long start = null;
            Long end = null;
            if (runRepository != null) {
                Run run = runRepository.find(runId);
                if (run == null) {
                    return List.of();
                }

                //use creation date for start
                BaseMetadata metadata = BaseMetadata.from(run.getMetadata());
                start = metadata.getCreated() != null ? metadata.getCreated().toEpochSecond() : null;
                end = metadata.getUpdated() != null ? metadata.getUpdated().toEpochSecond() + END_OFFSET : null;
            }

            //build queryQL from filter
            List<Pair<String, String>> filters = new ArrayList<>();
            filters.add(
                Pair.of(
                    "run",
                    lokiProperties.usePrefixForValues() ? applicationProperties.getName() + "-" + runId : runId
                )
            );

            List<Log> logs = fetch(filters, start, end);
            if (log.isTraceEnabled()) {
                log.trace("logs: {}", logs);
            }

            return logs;
        } catch (StoreException se) {
            log.error("Error fetching run {}: {}", runId, se.getMessage());
            throw new SystemException(se.getMessage());
        }
    }

    /*
     * Write operations are not supported.
     */
    @Override
    @Nullable
    public Log findLog(@NotNull String id) throws SystemException {
        //NOT SUPPORTED
        throw new UnsupportedOperationException("Unimplemented method 'findLog'");
    }

    @Override
    public Log getLog(@NotNull String id) throws NoSuchEntityException, SystemException {
        //NOT SUPPORTED
        throw new UnsupportedOperationException("Unimplemented method 'getLog'");
    }

    @Override
    public Log createLog(@NotNull Log logDTO)
        throws DuplicatedEntityException, BindException, IllegalArgumentException, SystemException {
        //NOT SUPPORTED
        throw new UnsupportedOperationException("Unimplemented method 'createLog'");
    }

    @Override
    public Log updateLog(@NotNull String id, @NotNull Log logDTO)
        throws NoSuchEntityException, BindException, IllegalArgumentException, SystemException {
        //NOT SUPPORTED
        throw new UnsupportedOperationException("Unimplemented method 'updateLog'");
    }

    @Override
    public void deleteLog(@NotNull String id) throws SystemException {
        //NOT SUPPORTED
    }

    @Override
    public void deleteLogsByProject(@NotNull String project) throws SystemException {
        //NOT SUPPORTED
    }

    @Override
    public void deleteLogsByRunId(@NotNull String runId) throws SystemException {
        // NOT SUPPORTED
    }

    /*
     * Helpers
     */
    private List<Log> fetch(@NotNull List<Pair<String, String>> filters, @Nullable Long start, @Nullable Long end) {
        if (
            StringUtils.hasText(lokiProperties.getNamespace()) &&
            filters.stream().noneMatch(f -> "namespace".equals(f.getFirst()))
        ) {
            //inject namespace filter
            filters.add(Pair.of("namespace", lokiProperties.getNamespace()));
        }

        StringBuilder query = new StringBuilder();
        query.append("{");
        for (Pair<String, String> f : filters) {
            String k = f.getFirst();
            String l = Optional.ofNullable(map(k)).orElseThrow(() ->
                new IllegalArgumentException(k + " label mapping is required")
            );

            if (query.length() > 1) {
                query.append(",");
            }
            query.append(String.format("%s=\"%s\"", l, f.getSecond()));
        }
        query.append("}");

        if (log.isTraceEnabled()) {
            log.trace("loki query: {}", query.toString());
        }

        if (query.length() == 0) {
            throw new IllegalArgumentException("no valid filters provided");
        }

        try {
            //query loki with default params
            //NOTE: we lack a proper logic for pagination
            long startEpoch = start != null ? start : (System.currentTimeMillis() / 1000) - (30L * 24 * 3600);
            long endEpoch = end != null ? end : System.currentTimeMillis() / 1000;
            QueryResult result = lokiClient.query(query.toString(), startEpoch, endEpoch, "forward");

            // fetch and convert log entries when available
            if (
                result.getData() != null && !result.getData().isEmpty() && result.getData() instanceof Streams streams
            ) {
                List<Log> logs = streams.getStreams().stream().map(this::convert).toList();
                return logs;
            }

            return List.of();
        } catch (LokiException e) {
            log.error("loki query failed: {} - {}", e.getStatusCode(), e.getMessage());
            throw new SystemException("loki query failed: " + e.getMessage(), e);
        }
    }

    private Log convert(Streams.Stream entry) {
        // Implement the logic to convert a stream of LogEntry object to a Log object
        // we expect labels to be aligned in the whole stream, so we can use the first entry to extract labels
        if (entry.getLabels() == null || entry.getLabels().isEmpty()) {
            throw new IllegalArgumentException("no labels found");
        }

        //fetch labels
        String pl = map("project");
        if (pl == null) {
            throw new IllegalArgumentException("project label mapping is required");
        }
        String project = lokiProperties.usePrefixForValues()
            ? entry.getLabels().get(pl).replace(applicationProperties.getName() + "-", "")
            : entry.getLabels().get(pl);

        String rl = map("run");
        if (rl == null) {
            throw new IllegalArgumentException("run label mapping is required");
        }
        String run = lokiProperties.usePrefixForValues()
            ? entry.getLabels().get(rl).replace(applicationProperties.getName() + "-", "")
            : entry.getLabels().get(rl);

        //custom fetch id from container if available, otherwise generate a random one
        String id = entry.getLabels().getOrDefault("container", java.util.UUID.randomUUID().toString());

        Log log = new Log();
        log.setId(id);
        log.setProject(project);
        log.setRun(run);

        String ul = map("user");
        if (ul != null) {
            String user = lokiProperties.usePrefixForValues()
                ? entry.getLabels().get(ul).replace(applicationProperties.getName() + "-", "")
                : entry.getLabels().get(ul);

            log.setUser(user);
        }

        //content is concatenated values with newlines
        StringBuilder content = new StringBuilder();
        for (LogEntry le : entry.getValues()) {
            if (!content.isEmpty()) {
                content.append("\n");
            }
            content.append(le.getLine());
        }

        log.setContent(content.toString());

        //base metadata
        BaseMetadata metadata = new BaseMetadata();
        // we can set created/updated from the first and last entry timestamps
        if (!entry.getValues().isEmpty()) {
            Long firstTimestamp = entry.getValues().get(0).getTimestamp();
            if (firstTimestamp != null) {
                metadata.setCreated(Instant.ofEpochMilli(firstTimestamp / 1_000_000).atOffset(ZoneOffset.UTC));
            }

            Long lastTimestamp = entry.getValues().get(entry.getValues().size() - 1).getTimestamp();
            if (lastTimestamp != null) {
                metadata.setUpdated(Instant.ofEpochMilli(lastTimestamp / 1_000_000).atOffset(ZoneOffset.UTC));
            }
        }
        //export labels as metadata
        Set<String> labels = new HashSet<>();
        entry
            .getLabels()
            .forEach((k, v) -> {
                if (!k.equals(pl) && !k.equals(rl) && !k.equals(ul)) {
                    //expose value as label, we lose key with current implementation
                    labels.add(v);
                }
            });
        metadata.setLabels(labels);
        log.setMetadata(metadata.toMap());

        return log;
    }

    private String map(String label) {
        if (lokiProperties.getMapping() != null && lokiProperties.getMapping().containsKey(label)) {
            String v = lokiProperties.getMapping().get(label);
            if (!StringUtils.hasText(v)) {
                return null;
            }
            return v;
        }

        return null;
    }
}
