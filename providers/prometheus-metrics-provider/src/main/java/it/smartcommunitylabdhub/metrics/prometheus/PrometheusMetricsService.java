package it.smartcommunitylabdhub.metrics.prometheus;

import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.repositories.EntityRepository;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import it.smartcommunitylabdhub.metrics.ResourceMetricsService;
import it.smartcommunitylabdhub.metrics.config.PrometheusProperties;
import it.smartcommunitylabdhub.metrics.prometheus.client.Matrix;
import it.smartcommunitylabdhub.metrics.prometheus.client.PrometheusClient;
import it.smartcommunitylabdhub.metrics.prometheus.client.PrometheusException;
import it.smartcommunitylabdhub.metrics.prometheus.client.QueryResult;
import it.smartcommunitylabdhub.runs.Run;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private static final String LAZY_MODIFIER = ".*"; //lazy filter modifier for regex matching
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
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getResourceMetrics'");
    }

    @Override
    public List<ResourceMetrics> listResourceMetrics() throws SystemException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'listResourceMetrics'");
    }

    @Override
    public ResourceMetrics getResourceMetricsByProject(@NotNull String project) throws SystemException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getResourceMetricsByProject'");
    }

    @Override
    public List<ResourceMetrics> listResourceMetricsByProject(@NotNull String project) throws SystemException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'listResourceMetricsByProject'");
    }

    @Override
    public ResourceMetrics getResourceMetricsByUser(@NotNull String user) throws SystemException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getResourceMetricsByUser'");
    }

    @Override
    public List<ResourceMetrics> listResourceMetricsByUser(@NotNull String user) throws SystemException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'listResourceMetricsByUser'");
    }

    @Override
    public ResourceMetrics getResourceMetricsByRun(@NotNull String project, @NotNull String runId)
        throws SystemException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getResourceMetricsByRun'");
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
                log.trace("logs: {}", metrics);
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
    private List<ResourceMetrics> fetch(
        @NotNull List<Pair<String, String>> filters,
        @Nullable Long start,
        @Nullable Long end
    ) {
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

        if (query.length() == 0) {
            throw new IllegalArgumentException("no valid filters provided");
        }

        //build all metrics as separate requests and join results in list
        List<ResourceMetrics> metrics = new ArrayList<>();

        if (properties.getMetrics() != null && !properties.getMetrics().isEmpty()) {
            for (Map.Entry<String, PrometheusProperties.MetricMapping> entry : properties.getMetrics().entrySet()) {
                String metricName = entry.getValue().name();
                String operation = entry.getValue().operation();
                String window = entry.getValue().window();

                if (!StringUtils.hasText(metricName)) {
                    continue;
                }

                String mq =
                    StringUtils.hasText(operation) && StringUtils.hasText(window)
                        ? String.format("%s(%s%s[%s])", operation, metricName, query.toString(), window)
                        : String.format("%s%s", metricName, query.toString());

                if (log.isTraceEnabled()) {
                    log.trace("prometheus metric query for {}: {}", metricName, mq);
                }

                try {
                    //query prometheus with default params
                    Long startEpoch = start != null ? start : null;
                    Long endEpoch = end != null ? end : null;
                    QueryResult result = client.queryRange(mq, startEpoch, endEpoch, null);

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
                                .map(e -> convert(entry, e.getKey(), e.getValue()))
                                .toList();

                            metrics.addAll(mres);
                        } else {
                            //no group by, convert all entries to a single ResourceMetrics
                            List<ResourceMetrics> mres = List.of(convert(entry, entry.getKey(), matrix.getResult()));
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
        List<Matrix.Metric> entries
    ) {
        ResourceMetrics rm = new ResourceMetrics();
        rm.setId(id + "-" + mapping.getKey());

        //base metadata
        BaseMetadata metadata = new BaseMetadata();
        metadata.setName(id + "-" + mapping.getKey());

        List<ResourceMetrics.Metrics> metrics = new ArrayList<>();
        PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper("{", "}");

        if (entries != null && !entries.isEmpty()) {
            //export labels as metadata from first entry, we assume all entries have same labels
            Matrix.Metric entry = entries.get(0);
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
                if (StringUtils.hasText(label) && e.getLabels() != null) {
                    //resolve placeholders like {container} with actual label values
                    name = helper.replacePlaceholders(label, key -> e.getLabels().getOrDefault(key, mapping.getKey()));
                }

                PrometheusProperties.MetricMapping value = mapping.getValue();
                ResourceMetrics.Metrics m = new ResourceMetrics.Metrics(
                    name,
                    value.unit(),
                    e
                        .values()
                        .stream()
                        .map(v -> new ResourceMetrics.Metric(v.timestamp().longValue(), Double.valueOf(v.value())))
                        .toList(),
                    null
                );
                metrics.add(m);
            });
        }

        rm.setMetadata(metadata.toMap());
        rm.setMetrics(metrics);
        return rm;
    }
}
