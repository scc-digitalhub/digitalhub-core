package it.smartcommunitylabdhub.metrics.prometheus;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import it.smartcommunitylabdhub.metrics.ResourceMetrics.Metric;
import it.smartcommunitylabdhub.metrics.ResourceMetricsService;
import it.smartcommunitylabdhub.metrics.config.PrometheusProperties;
import it.smartcommunitylabdhub.metrics.prometheus.client.Matrix;
import it.smartcommunitylabdhub.metrics.prometheus.client.PrometheusClient;
import it.smartcommunitylabdhub.metrics.prometheus.client.PrometheusException;
import it.smartcommunitylabdhub.metrics.prometheus.client.QueryResult;
import it.smartcommunitylabdhub.metrics.prometheus.client.Vector;
import it.smartcommunitylabdhub.runs.Run;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.util.Assert;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringUtils;

@Slf4j
public class PrometheusMetricsService implements ResourceMetricsService {

    private static final long END_OFFSET = 300L; //5 minutes offset for end time if not available
    private static final int DEFAULT_INTERVAL = 300; //default interval for current metrics
    private static final String LAZY_MODIFIER = ".*"; //lazy filter modifier for regex matching
    private static final PropertyPlaceholderHelper PLACEHOLDER_HELPER = new PropertyPlaceholderHelper("{", "}");
    private static final int MAX_NUMBER_POINTS = 1000; //max data points per series
    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_HOUR = 3600L;
    private static final long SECONDS_PER_DAY = 86400L;
    private static final long SECONDS_PER_WEEK = 604800L;
    private static final long SECONDS_PER_YEAR = 31536000L;
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d++)(ms|[smhdwy])");

    private final PrometheusProperties properties;
    private final PrometheusClient client;

    private EntityRepository<Run> runRepository;

    @Autowired
    ApplicationProperties applicationProperties;

    public PrometheusMetricsService(PrometheusProperties prometheusProperties) {
        Assert.notNull(prometheusProperties, "properties are required");
        Assert.hasText(prometheusProperties.getUrl(), "prometheus url is required");

        this.properties = prometheusProperties;
        this.client = new PrometheusClient(properties);
    }

    @Autowired(required = false)
    public void setRunRepository(EntityRepository<Run> runRepository) {
        this.runRepository = runRepository;
    }

    @Override
    public ResourceMetrics getResourceMetrics() throws SystemException {
        log.debug("get current metrics by instance {}", applicationProperties.getName());

        if (map("instance") == null) {
            //not supported, return empty list
            log.warn("instance label mapping is not configured, returning empty list");
            return new ResourceMetrics();
        }

        //default time interval
        Long end = Instant.now().getEpochSecond();
        Long start = end - DEFAULT_INTERVAL; //last 5 minutes

        //build promQL from filter
        List<Pair<String, String>> filters = new ArrayList<>();
        filters.add(
            Pair.of(
                "instance",
                properties.useLazyFilter()
                    ? LAZY_MODIFIER + applicationProperties.getName() + LAZY_MODIFIER
                    : applicationProperties.getName()
            )
        );

        List<ResourceMetrics> list = get(filters, start, end);
        //assemble a single result, results come as vectors from prometheus
        List<ResourceMetrics.Metrics> metrics = new ArrayList<>();

        ResourceMetrics rm = new ResourceMetrics();
        rm.setId("m_i-" + applicationProperties.getName());
        list.forEach(mm -> {
            //metric should have a single value, put into collector
            mm
                .getMetrics()
                .stream()
                .filter(m -> m.metrics() != null)
                .findFirst()
                .ifPresent(metric -> {
                    metrics.add(metric);
                });
        });

        rm.setMetrics(metrics);

        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", rm);
        }

        return rm;
    }

    @Override
    public List<ResourceMetrics> listResourceMetrics() throws SystemException {
        log.debug("list metrics by instance {}", applicationProperties.getName());

        if (map("instance") == null) {
            //not supported, return empty list
            log.warn("instance label mapping is not configured, returning empty list");
            return List.of();
        }

        //use default interval
        Long start = null;
        Long end = null;

        //build promQL from filter
        List<Pair<String, String>> filters = new ArrayList<>();
        filters.add(
            Pair.of(
                "instance",
                properties.useLazyFilter()
                    ? LAZY_MODIFIER + applicationProperties.getName() + LAZY_MODIFIER
                    : applicationProperties.getName()
            )
        );

        List<ResourceMetrics> metrics = fetch(filters, start, end);
        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", metrics);
        }

        return metrics;
    }

    @Override
    public ResourceMetrics getResourceMetricsByProject(@NotNull String project) throws SystemException {
        log.debug("get current metrics by project {}", project);

        if (!StringUtils.hasText(project)) {
            throw new IllegalArgumentException("project is required");
        }

        if (map("project") == null) {
            //not supported, return empty list
            log.warn("project label mapping is not configured, returning empty list");
            return new ResourceMetrics();
        }

        //default time interval
        Long end = Instant.now().getEpochSecond();
        Long start = end - DEFAULT_INTERVAL; //last 5 minutes

        //build promQL from filter
        List<Pair<String, String>> filters = new ArrayList<>();
        filters.add(Pair.of("project", properties.useLazyFilter() ? LAZY_MODIFIER + project + LAZY_MODIFIER : project));

        List<ResourceMetrics> list = get(filters, start, end);
        //assemble a single result, results come as vectors from prometheus
        List<ResourceMetrics.Metrics> metrics = new ArrayList<>();

        ResourceMetrics rm = new ResourceMetrics();
        rm.setId("m_p-" + project);
        rm.setProject(project);
        list.forEach(mm -> {
            //metric should have a single value, put into collector
            mm
                .getMetrics()
                .stream()
                .filter(m -> m.metrics() != null)
                .findFirst()
                .ifPresent(metric -> {
                    metrics.add(metric);
                });
        });

        rm.setMetrics(metrics);

        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", rm);
        }

        return rm;
    }

    @Override
    public List<ResourceMetrics> listResourceMetricsByProject(@NotNull String project) throws SystemException {
        log.debug("list metrics by project {}", project);

        if (!StringUtils.hasText(project)) {
            throw new IllegalArgumentException("project is required");
        }

        if (map("project") == null) {
            //not supported, return empty list
            log.warn("project label mapping is not configured, returning empty list");
            return List.of();
        }

        //use default interval
        Long start = null;
        Long end = null;

        //build promQL from filter
        List<Pair<String, String>> filters = new ArrayList<>();
        filters.add(Pair.of("project", properties.useLazyFilter() ? LAZY_MODIFIER + project + LAZY_MODIFIER : project));

        List<ResourceMetrics> metrics = fetch(filters, start, end);
        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", metrics);
        }

        return metrics;
    }

    @Override
    public ResourceMetrics getResourceMetricsByUser(@NotNull String user) throws SystemException {
        log.debug("get current metrics by user {}", user);

        if (!StringUtils.hasText(user)) {
            throw new IllegalArgumentException("user is required");
        }

        if (map("user") == null) {
            //not supported, return empty list
            log.warn("user label mapping is not configured, returning empty list");
            return new ResourceMetrics();
        }

        //default time interval
        Long end = Instant.now().getEpochSecond();
        Long start = end - DEFAULT_INTERVAL; //last 5 minutes

        //build promQL from filter
        List<Pair<String, String>> filters = new ArrayList<>();
        //exact match always for user, no lazy filter
        filters.add(Pair.of("user", user));

        List<ResourceMetrics> list = get(filters, start, end);
        //assemble a single result, results come as vectors from prometheus
        List<ResourceMetrics.Metrics> metrics = new ArrayList<>();

        ResourceMetrics rm = new ResourceMetrics();
        rm.setId("m_u-" + user);
        rm.setUser(user);
        list.forEach(mm -> {
            //metric should have a single value, put into collector
            mm
                .getMetrics()
                .stream()
                .filter(m -> m.metrics() != null)
                .findFirst()
                .ifPresent(metric -> {
                    metrics.add(metric);
                });
        });

        rm.setMetrics(metrics);

        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", rm);
        }

        return rm;
    }

    @Override
    public List<ResourceMetrics> listResourceMetricsByUser(@NotNull String user) throws SystemException {
        log.debug("list metrics by user {}", user);

        if (!StringUtils.hasText(user)) {
            throw new IllegalArgumentException("user is required");
        }

        if (map("user") == null) {
            //not supported, return empty list
            log.warn("user label mapping is not configured, returning empty list");
            return List.of();
        }

        //use default interval
        Long start = null;
        Long end = null;

        //build promQL from filter
        List<Pair<String, String>> filters = new ArrayList<>();
        //exact match always for user, no lazy filter
        filters.add(Pair.of("user", user));

        List<ResourceMetrics> metrics = fetch(filters, start, end);
        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", metrics);
        }

        return metrics;
    }

    @Override
    public ResourceMetrics getResourceMetricsByRun(@NotNull String project, @NotNull String runId)
        throws SystemException {
        log.debug("get current metrics by run {}", runId);

        //default time interval
        Long end = Instant.now().getEpochSecond();
        Long start = end - DEFAULT_INTERVAL; //last 5 minutes

        //build promQL from filter
        List<Pair<String, String>> filters = new ArrayList<>();
        filters.add(Pair.of("run", properties.useLazyFilter() ? LAZY_MODIFIER + runId + LAZY_MODIFIER : runId));

        List<ResourceMetrics> list = get(filters, start, end);
        //assemble a single result, results come as vectors from prometheus
        List<ResourceMetrics.Metrics> metrics = new ArrayList<>();

        ResourceMetrics rm = new ResourceMetrics();
        rm.setId("m_r-" + runId);
        rm.setRun(runId);
        list.forEach(mm -> {
            //metric should have a single value, put into collector
            mm
                .getMetrics()
                .stream()
                .filter(m -> m.metrics() != null)
                .findFirst()
                .ifPresent(metric -> {
                    metrics.add(metric);
                });
        });

        rm.setMetrics(metrics);

        if (log.isTraceEnabled()) {
            log.trace("metrics: {}", rm);
        }

        return rm;
    }

    @Override
    public List<ResourceMetrics> listResourceMetricsByRun(@NotNull String project, @NotNull String runId)
        throws SystemException {
        log.debug("list metrics by run {}", runId);

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

            //build promQL from filter
            List<Pair<String, String>> filters = new ArrayList<>();
            filters.add(Pair.of("run", properties.useLazyFilter() ? LAZY_MODIFIER + runId + LAZY_MODIFIER : runId));

            List<ResourceMetrics> metrics = fetch(filters, start, end);
            if (log.isTraceEnabled()) {
                log.trace("metrics: {}", metrics);
            }

            return metrics;
        } catch (StoreException se) {
            log.error("Error fetching run {}: {}", runId, se.getMessage());
            throw new SystemException(se.getMessage());
        }
    }

    /*
     * Helpers
     */
    private List<ResourceMetrics> get(
        @NotNull List<Pair<String, String>> filters,
        @Nullable Long start,
        @Nullable Long end
    ) {
        String filterQuery = buildFilterQuery(filters);

        //build all metrics as separate requests and join results in list
        List<ResourceMetrics> metrics = new ArrayList<>();

        if (properties.getMetrics() != null && !properties.getMetrics().isEmpty()) {
            for (Map.Entry<String, PrometheusProperties.MetricMapping> entry : properties.getMetrics().entrySet()) {
                if (!StringUtils.hasText(entry.getValue().name())) {
                    continue;
                }

                //get a single value for the metric by summing all series, if any
                String aggregation = StringUtils.hasText(entry.getValue().aggregation())
                    ? entry.getValue().aggregation()
                    : "sum";
                String mq = String.format(
                    "%s(%s)",
                    aggregation,
                    buildMetricQuery(filterQuery, entry.getValue(), start, end)
                );

                if (log.isTraceEnabled()) {
                    log.trace("prometheus metric query for {}: {}", entry.getValue().name(), mq);
                }

                try {
                    QueryResult result = client.query(mq, start, end, null);

                    // fetch and convert vector entries when available
                    if (
                        result.getData() != null &&
                        !result.getData().isEmpty() &&
                        result.getData() instanceof Vector vector
                    ) {
                        //we expect a single metric with a single value, convert to ResourceMetrics
                        List<ResourceMetrics> mres = List.of(
                            convert(
                                entry,
                                entry.getKey(),
                                vector
                                    .getResult()
                                    .stream()
                                    .map(m -> (QueryResult.Result) m)
                                    .toList()
                            )
                        );
                        metrics.addAll(mres);
                    }
                } catch (PrometheusException e) {
                    log.error("prometheus query failed: {} - {}", e.getStatusCode(), e.getMessage());
                    throw new SystemException("prometheus query failed: " + e.getMessage(), e);
                }
            }
        }

        return metrics;
    }

    private List<ResourceMetrics> fetch(
        @NotNull List<Pair<String, String>> filters,
        @Nullable Long start,
        @Nullable Long end
    ) {
        String filterQuery = buildFilterQuery(filters);

        //build all metrics as separate requests and join results in list
        List<ResourceMetrics> metrics = new ArrayList<>();

        if (properties.getMetrics() != null && !properties.getMetrics().isEmpty()) {
            for (Map.Entry<String, PrometheusProperties.MetricMapping> entry : properties.getMetrics().entrySet()) {
                if (!StringUtils.hasText(entry.getValue().name())) {
                    continue;
                }

                String mq = buildMetricQuery(filterQuery, entry.getValue(), start, end);

                if (log.isTraceEnabled()) {
                    log.trace("prometheus metric query for {}: {}", entry.getValue().name(), mq);
                }

                try {
                    //query prometheus with default params, evaluating step size to avoid overflowing prometheus
                    Duration step = PrometheusClient.DEFAULT_STEP;
                    if (start != null && end != null) {
                        long windowSeconds = step.getSeconds();
                        if (windowSeconds > 0) {
                            long intervalSeconds = end - start;
                            long points = intervalSeconds / windowSeconds;
                            if (points > MAX_NUMBER_POINTS) {
                                // Minimum step required to stay within the point limit (ceiling division)
                                long requiredStep = (intervalSeconds + MAX_NUMBER_POINTS - 1) / MAX_NUMBER_POINTS;

                                // Round up to the next multiple of 15 seconds
                                windowSeconds = ((requiredStep + 14) / 15) * 15;

                                // Never go below the default step
                                windowSeconds = Math.max(PrometheusClient.DEFAULT_STEP.getSeconds(), windowSeconds);

                                step = Duration.ofSeconds(windowSeconds);
                                log.debug("adjusted step to {}s, max points: {}", windowSeconds, MAX_NUMBER_POINTS);
                            }
                        }
                    }
                    QueryResult result = client.queryRange(mq, start, end, step);

                    // fetch and convert matrix entries when available
                    if (
                        result.getData() != null &&
                        !result.getData().isEmpty() &&
                        result.getData() instanceof Matrix matrix
                    ) {
                        if (entry.getValue().groupBy() != null) {
                            //group by group label container to a map of metrics, then convert to ResourceMetrics
                            Map<String, List<Matrix.Metric>> grouped = matrix
                                .getResult()
                                .stream()
                                .collect(
                                    Collectors.groupingBy(m ->
                                        Optional.ofNullable(m.getLabels().get(entry.getValue().groupBy())).orElse(
                                            "unknown"
                                        )
                                    )
                                );

                            List<ResourceMetrics> mres = grouped
                                .entrySet()
                                .stream()
                                .filter(e -> !("unknown".equals(e.getKey())))
                                .map(e ->
                                    convert(
                                        entry,
                                        e.getKey(),
                                        e
                                            .getValue()
                                            .stream()
                                            .map(m -> (QueryResult.Result) m)
                                            .toList()
                                    )
                                )
                                .toList();

                            metrics.addAll(mres);
                        } else {
                            //no group by, convert all entries to a single ResourceMetrics
                            List<ResourceMetrics> mres = List.of(
                                convert(
                                    entry,
                                    entry.getKey(),
                                    matrix
                                        .getResult()
                                        .stream()
                                        .map(m -> (QueryResult.Result) m)
                                        .toList()
                                )
                            );
                            metrics.addAll(mres);
                        }
                    }
                } catch (PrometheusException e) {
                    log.error("prometheus query failed: {} - {}", e.getStatusCode(), e.getMessage());
                    throw new SystemException("prometheus query failed: " + e.getMessage(), e);
                }
            }
        }

        return metrics;
    }

    private String buildFilterQuery(@NotNull List<Pair<String, String>> filters) {
        if (
            StringUtils.hasText(properties.getNamespace()) &&
            filters.stream().noneMatch(f -> "namespace".equals(f.getFirst()))
        ) {
            //inject namespace filter
            filters.add(Pair.of("namespace", properties.getNamespace()));
        }

        StringBuilder query = new StringBuilder();
        query.append("{");
        for (Pair<String, String> f : filters) {
            String k = f.getFirst();
            String l = Optional.ofNullable(map(k)).orElseThrow(() ->
                new IllegalArgumentException(k + " label mapping is required")
            );
            String qp = properties.useLazyFilter() ? "%s=~\"%s\"" : "%s=\"%s\"";

            if (query.length() > 1) {
                query.append(",");
            }
            query.append(String.format(qp, l, f.getSecond()));
        }
        query.append("}");

        if (log.isTraceEnabled()) {
            log.trace("prometheus query filters: {}", query.toString());
        }

        return query.toString();
    }

    private String buildMetricQuery(
        @NotNull String filterQuery,
        @NotNull PrometheusProperties.MetricMapping mapping,
        @Nullable Long start,
        @Nullable Long end
    ) {
        String metricName = mapping.name();
        String operation = mapping.operation();
        String window = mapping.window();

        if (StringUtils.hasText(operation) && StringUtils.hasText(window) && start != null && end != null) {
            long windowSeconds = parseDurationSeconds(window);
            if (windowSeconds > 0) {
                long intervalSeconds = end - start;
                long points = intervalSeconds / windowSeconds;
                if (points > MAX_NUMBER_POINTS) {
                    windowSeconds = Math.max(1L, intervalSeconds / MAX_NUMBER_POINTS);
                    window = windowSeconds + "s";
                    log.debug("adjusted window to {}s, max points: {}", windowSeconds, MAX_NUMBER_POINTS);
                }
            }
        }

        return StringUtils.hasText(operation) && StringUtils.hasText(window)
            ? String.format("%s(%s%s[%s])", operation, metricName, filterQuery, window)
            : String.format("%s%s", metricName, filterQuery);
    }

    private long parseDurationSeconds(@NotNull String duration) {
        long total = 0;
        Matcher m = DURATION_PATTERN.matcher(duration);
        while (m.find()) {
            long value = Long.parseLong(m.group(1));
            String unit = m.group(2);
            total += switch (unit) {
                case "ms" -> 0L; // sub-second, ignore
                case "s" -> value;
                case "m" -> value * SECONDS_PER_MINUTE;
                case "h" -> value * SECONDS_PER_HOUR;
                case "d" -> value * SECONDS_PER_DAY;
                case "w" -> value * SECONDS_PER_WEEK;
                case "y" -> value * SECONDS_PER_YEAR;
                default -> 0L;
            };
        }
        return total;
    }

    private String map(String label) {
        if (properties.getMapping() != null && properties.getMapping().containsKey(label)) {
            String v = properties.getMapping().get(label);
            if (!StringUtils.hasText(v)) {
                return null;
            }
            return v;
        }

        return null;
    }

    private ResourceMetrics convert(
        Map.Entry<String, PrometheusProperties.MetricMapping> mapping,
        String id,
        List<QueryResult.Result> entries
    ) {
        ResourceMetrics rm = new ResourceMetrics();
        rm.setId(id + "-" + mapping.getKey());

        //base metadata
        BaseMetadata metadata = new BaseMetadata();
        metadata.setName(id + "-" + mapping.getKey());

        List<ResourceMetrics.Metrics> metrics = new ArrayList<>();

        if (entries != null && !entries.isEmpty()) {
            //export labels as metadata from first entry, we assume all entries have same labels
            QueryResult.Result entry = entries.get(0);
            Set<String> labels = new HashSet<>();
            entry
                .getLabels()
                .forEach((k, v) -> {
                    //expose value as label, we lose key with current implementation
                    labels.add(k + ":" + v);
                });
            metadata.setLabels(labels);

            //map all entries to series
            entries.forEach(e -> {
                String name = mapping.getKey();
                String label = mapping.getValue().label();
                if (StringUtils.hasText(label) && e.getLabels() != null && !e.getLabels().isEmpty()) {
                    //resolve placeholders like {container} with actual label values
                    name = PLACEHOLDER_HELPER.replacePlaceholders(label, key ->
                        e.getLabels().getOrDefault(key, mapping.getKey())
                    );
                }

                PrometheusProperties.MetricMapping value = mapping.getValue();
                List<Metric> em = e
                    .getValues()
                    .stream()
                    .map(v -> new ResourceMetrics.Metric(v.timestamp().longValue(), Double.valueOf(v.value())))
                    .toList();
                ResourceMetrics.Metrics m = new ResourceMetrics.Metrics(
                    name,
                    value.unit(),
                    em,
                    summarize(em),
                    value.quota()
                );

                metrics.add(m);
            });
        }

        rm.setMetadata(metadata.toMap());
        rm.setMetrics(metrics);
        return rm;
    }

    private List<ResourceMetrics.Summary> summarize(List<ResourceMetrics.Metric> metrics) {
        if (metrics != null) {
            Double sum = metrics.stream().mapToDouble(ResourceMetrics.Metric::value).sum();
            Double avg = metrics.stream().mapToDouble(ResourceMetrics.Metric::value).average().orElse(0.0);
            Double max = metrics.stream().mapToDouble(ResourceMetrics.Metric::value).max().orElse(0.0);
            Double min = metrics.stream().mapToDouble(ResourceMetrics.Metric::value).min().orElse(0.0);

            return List.of(
                new ResourceMetrics.Summary("sum", sum),
                new ResourceMetrics.Summary("avg", avg),
                new ResourceMetrics.Summary("max", max),
                new ResourceMetrics.Summary("min", min)
            );
        }

        return List.of();
    }
}
