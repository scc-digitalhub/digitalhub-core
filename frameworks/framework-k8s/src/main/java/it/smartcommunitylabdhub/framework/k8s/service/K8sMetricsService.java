package it.smartcommunitylabdhub.framework.k8s.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.kubernetes.client.Metrics;
import io.kubernetes.client.custom.ContainerMetrics;
import io.kubernetes.client.custom.PodMetrics;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.custom.Quantity.Format;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import it.smartcommunitylabdhub.metrics.ResourceMetricsService;
import it.smartcommunitylabdhub.metrics.ResourceMetricsStore;
import jakarta.annotation.Nonnull;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.util.Assert;

@Slf4j
public class K8sMetricsService implements ResourceMetricsService, InitializingBean {

    public static final long CACHE_TIMEOUT = 30; //seconds

    protected final CoreV1Api coreV1Api;
    protected final Metrics metricsApi;

    protected ApplicationProperties applicationProperties;
    protected K8sLabelHelper k8sLabelHelper;
    protected Boolean collectMetrics;
    protected String namespace;

    private ResourceMetricsStore store;

    //loading cache for pod metrics
    LoadingCache<Pair<String, String>, List<PodMetrics>> podCache = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<Pair<String, String>, List<PodMetrics>>() {
                @Override
                public List<PodMetrics> load(@Nonnull Pair<String, String> key) throws Exception {
                    return fetchPodMetrics(key.getFirst(), key.getSecond());
                }
            }
        );

    //loading cache for pvc metrics
    LoadingCache<Pair<String, String>, List<V1PersistentVolumeClaim>> pvcCache = CacheBuilder.newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<Pair<String, String>, List<V1PersistentVolumeClaim>>() {
                @Override
                public List<V1PersistentVolumeClaim> load(@Nonnull Pair<String, String> key) throws Exception {
                    return fetchPvcs(key.getFirst(), key.getSecond());
                }
            }
        );

    public K8sMetricsService(ApiClient apiClient) {
        Assert.notNull(apiClient, "k8s api client is required");
        coreV1Api = new CoreV1Api(apiClient);
        metricsApi = new Metrics(apiClient);
    }

    @Autowired(required = false)
    public void setStore(ResourceMetricsStore store) {
        this.store = store;
    }

    @Autowired
    public void setK8sLabelHelper(K8sLabelHelper k8sLabelHelper) {
        this.k8sLabelHelper = k8sLabelHelper;
    }

    @Autowired
    public void setApplicationProperties(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Autowired
    public void setCollectMetrics(@Value("${kubernetes.metrics}") Boolean collectMetrics) {
        this.collectMetrics = collectMetrics;
    }

    @Autowired
    public void setNamespace(@Value("${kubernetes.namespace}") String namespace) {
        this.namespace = namespace;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(k8sLabelHelper, "k8s label helper is required");
        Assert.notNull(namespace, "k8s namespace required");
        Assert.notNull(applicationProperties, "application properties required");
    }

    private List<PodMetrics> fetchPodMetrics(@Nonnull String key, @Nonnull String value) throws StoreException {
        if (Boolean.TRUE != collectMetrics) {
            return List.of();
        }

        Entry<String, String> label = k8sLabelHelper.buildCoreLabel(key, value);
        try {
            //return metrics for all pods matching
            List<PodMetrics> podMetrics = metricsApi
                .getPodMetrics(namespace)
                .getItems()
                .stream()
                .filter(m -> m.getMetadata() != null && m.getMetadata().getLabels() != null)
                .filter(m -> {
                    Map<String, String> labels = k8sLabelHelper.extractCoreLabels(m.getMetadata().getLabels());
                    return (
                        labels != null &&
                        label != null &&
                        label.getValue() != null &&
                        label.getValue().equals(labels.get(key))
                    );
                })
                .toList();

            return podMetrics;
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            return List.of();
        }
    }

    private List<V1PersistentVolumeClaim> fetchPvcs(@Nonnull String key, @Nonnull String value) throws StoreException {
        if (Boolean.TRUE != collectMetrics) {
            return List.of();
        }

        Entry<String, String> label = k8sLabelHelper.buildCoreLabel(key, value);
        try {
            String labelSelector = label.getKey() + "=" + label.getValue();
            List<V1PersistentVolumeClaim> pvcs = coreV1Api
                .listNamespacedPersistentVolumeClaim(
                    namespace,
                    null,
                    null,
                    null,
                    null,
                    labelSelector,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
                )
                .getItems();

            return pvcs;
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            return List.of();
        }
    }

    /* API */
    public List<PodMetrics> listPodMetrics(String key, String value) throws StoreException {
        try {
            List<PodMetrics> mv = podCache.get(Pair.of(key, value));
            if (log.isTraceEnabled()) {
                log.trace("Metrics for {} {}: {}", key, value, mv);
            }

            return mv;
        } catch (Exception e) {
            throw new StoreException("Error getting metrics for " + key + " " + value, e);
        }
    }

    public List<V1PersistentVolumeClaim> listPvcs(String key, String value) throws StoreException {
        try {
            List<V1PersistentVolumeClaim> mv = pvcCache.get(Pair.of(key, value));
            if (log.isTraceEnabled()) {
                log.trace("PVCs for {} {}: {}", key, value, mv);
            }

            return mv;
        } catch (Exception e) {
            throw new StoreException("Error getting pvcs for " + key + " " + value, e);
        }
    }

    public ContainerMetrics getContainerMetrics(@Nonnull String key, @Nonnull String value) throws StoreException {
        if (Boolean.TRUE != collectMetrics) {
            return null;
        }

        Entry<String, String> label = k8sLabelHelper.buildCoreLabel(key, value);

        ContainerMetrics metrics = new ContainerMetrics();
        metrics.setName(key);
        metrics.setUsage(new HashMap<>());
        BigDecimal big1 = new BigDecimal(1);

        //aggregate metrics for all pods matching
        List<PodMetrics> podMetrics = listPodMetrics(key, value);

        podMetrics.forEach(pm -> {
            //count pods
            if (metrics.getUsage().containsKey("pods")) {
                Quantity existing = metrics.getUsage().get("pods");
                metrics.getUsage().put("pods", new Quantity(existing.getNumber().add(big1), Format.DECIMAL_SI));
            } else {
                metrics.getUsage().put("pods", new Quantity(big1, Format.DECIMAL_SI));
            }

            if (pm.getContainers() != null) {
                pm
                    .getContainers()
                    .forEach(cm -> {
                        //count containers
                        if (metrics.getUsage().containsKey("containers")) {
                            Quantity existing = metrics.getUsage().get("containers");
                            metrics
                                .getUsage()
                                .put("containers", new Quantity(existing.getNumber().add(big1), Format.DECIMAL_SI));
                        } else {
                            metrics.getUsage().put("containers", new Quantity(big1, Format.DECIMAL_SI));
                        }

                        if (cm.getUsage() != null) {
                            log.trace(
                                "Adding metrics for {} {} pod {} container {}: {}",
                                key,
                                value,
                                pm.getMetadata().getName(),
                                cm.getName(),
                                cm.getUsage()
                            );

                            cm
                                .getUsage()
                                .forEach((k, v) -> {
                                    if (metrics.getUsage().containsKey(k)) {
                                        //we expect format to match, or skip
                                        try {
                                            Quantity existing = metrics.getUsage().get(k);
                                            if (existing.getFormat().equals(v.getFormat())) {
                                                metrics
                                                    .getUsage()
                                                    .put(
                                                        k,
                                                        new Quantity(
                                                            existing.getNumber().add(v.getNumber()),
                                                            v.getFormat()
                                                        )
                                                    );
                                            }
                                        } catch (NumberFormatException e) {
                                            log.warn(
                                                "Error parsing metric for {} {} pod {} container {}: {}",
                                                key,
                                                value,
                                                pm.getMetadata().getName(),
                                                cm.getName(),
                                                e.getMessage()
                                            );
                                        }
                                    } else {
                                        metrics.getUsage().put(k, v);
                                    }
                                });
                        } else {
                            log.trace(
                                "No usage metrics for {} {} pod {} container {}",
                                key,
                                value,
                                pm.getMetadata().getName(),
                                cm.getName()
                            );
                        }
                    });
            }
        });

        //fetch pvc metrics for matching pods

        String labelSelector = label.getKey() + "=" + label.getValue();
        List<V1PersistentVolumeClaim> pvcs = listPvcs(key, value);

        pvcs.forEach(pvc -> {
            Optional.ofNullable(pvc.getStatus())
                .map(s -> s.getCapacity())
                .map(m -> m.get("storage"))
                .ifPresent(c -> {
                    log.trace("Adding PVC metrics for {} {} pvc {}: {}", key, value, pvc.getMetadata().getName(), c);

                    if (metrics.getUsage().containsKey("disk")) {
                        //we expect format to match, or skip
                        try {
                            Quantity existing = metrics.getUsage().get("disk");
                            if (existing.getFormat().equals(c.getFormat())) {
                                metrics
                                    .getUsage()
                                    .put("disk", new Quantity(existing.getNumber().add(c.getNumber()), c.getFormat()));
                            }
                        } catch (NumberFormatException e) {
                            log.warn(
                                "Error parsing disk metric for {} {} pvc {}: {}",
                                key,
                                value,
                                pvc.getMetadata().getName(),
                                e.getMessage()
                            );
                        }
                    } else {
                        metrics.getUsage().put("disk", c);
                    }
                });
        });

        return metrics;
    }

    @Override
    public ResourceMetrics getResourceMetricsByProject(@NotNull String project) throws SystemException {
        log.debug("fetch metrics from k8s for project {}: ", project);

        try {
            //fetch now from service
            ContainerMetrics metrics = getContainerMetrics("project", project);
            if (metrics == null) {
                return null;
            }

            Map<String, List<ResourceMetrics.Metric>> metricsMap = new HashMap<>();
            metrics
                .getUsage()
                .forEach((k, v) -> {
                    ResourceMetrics.Metric metric = new ResourceMetrics.Metric(
                        System.currentTimeMillis(),
                        v.getNumber().doubleValue()
                    );
                    metricsMap.put(k, List.of(metric));
                });

            //build
            List<ResourceMetrics.Metrics> metricsList = metricsMap
                .entrySet()
                .stream()
                .map(e -> new ResourceMetrics.Metrics(e.getKey(), null, e.getValue(), summarize(e.getValue())))
                .toList();

            return ResourceMetrics.builder().id("m_p-" + project).project(project).metrics(metricsList).build();
        } catch (StoreException e) {
            throw new SystemException("Error fetching metrics for project " + project, e);
        }
    }

    @Override
    public ResourceMetrics getResourceMetricsByUser(@NotNull String user) throws SystemException {
        log.debug("fetch metrics from k8s for user {}: ", user);

        try {
            //fetch now from service
            ContainerMetrics metrics = getContainerMetrics("user", user);
            if (metrics == null) {
                return null;
            }

            Map<String, List<ResourceMetrics.Metric>> metricsMap = new HashMap<>();
            metrics
                .getUsage()
                .forEach((k, v) -> {
                    ResourceMetrics.Metric metric = new ResourceMetrics.Metric(
                        System.currentTimeMillis(),
                        v.getNumber().doubleValue()
                    );
                    metricsMap.put(k, List.of(metric));
                });

            //build
            List<ResourceMetrics.Metrics> metricsList = metricsMap
                .entrySet()
                .stream()
                .map(e -> new ResourceMetrics.Metrics(e.getKey(), null, e.getValue(), summarize(e.getValue())))
                .toList();

            return ResourceMetrics.builder().id("m_u-" + user).user(user).metrics(metricsList).build();
        } catch (StoreException e) {
            throw new SystemException("Error fetching metrics for user " + user, e);
        }
    }

    @Override
    public ResourceMetrics getResourceMetricsByRun(@NotNull String project, @NotNull String runId)
        throws SystemException {
        log.debug("fetch metrics from k8s for run {} in project {}: ", runId, project);

        try {
            //we keep id consistent to enable lookups
            String id = "m_r-" + runId;
            Map<String, List<ResourceMetrics.Metric>> metricsMap = new HashMap<>();

            //fetch now from service
            ContainerMetrics metrics = getContainerMetrics("run", runId);
            if (metrics != null) {
                metrics
                    .getUsage()
                    .forEach((k, v) -> {
                        ResourceMetrics.Metric metric = new ResourceMetrics.Metric(
                            System.currentTimeMillis(),
                            v.getNumber().doubleValue()
                        );
                        metricsMap.put(k, List.of(metric));
                    });
            }

            //fetch from store as well, if available
            if (store != null) {
                ResourceMetrics storedMetrics = store.findResourceMetrics(id);
                if (storedMetrics != null && storedMetrics.getMetrics() != null) {
                    storedMetrics
                        .getMetrics()
                        .forEach(m -> {
                            //merge with existing metrics
                            if (metricsMap.containsKey(m.name())) {
                                List<ResourceMetrics.Metric> existing = metricsMap.get(m.name());
                                List<ResourceMetrics.Metric> merged = existing.stream().collect(Collectors.toList());
                                merged.addAll(m.metrics());
                                metricsMap.put(m.name(), merged);
                            } else {
                                metricsMap.put(m.name(), m.metrics());
                            }
                        });
                }
            }

            //make sure metrics lists are sorted by timestamp
            metricsMap.forEach((k, v) -> {
                List<ResourceMetrics.Metric> sorted = v
                    .stream()
                    .sorted((m1, m2) -> Long.compare(m1.timestamp(), m2.timestamp()))
                    .toList();
                metricsMap.put(k, sorted);
            });

            //build
            List<ResourceMetrics.Metrics> metricsList = metricsMap
                .entrySet()
                .stream()
                .map(e -> new ResourceMetrics.Metrics(e.getKey(), null, e.getValue(), summarize(e.getValue())))
                .toList();

            return ResourceMetrics.builder().id(id).run(runId).metrics(metricsList).build();
        } catch (StoreException e) {
            throw new SystemException("Error fetching metrics for run " + runId, e);
        }
    }

    @Override
    public List<ResourceMetrics> listResourceMetricsByProject(@NotNull String project) throws SystemException {
        log.debug("fetch metrics from k8s for project {}: ", project);

        try {
            //fetch all from service
            List<PodMetrics> podMetrics = listPodMetrics("project", project);
            if (podMetrics == null || podMetrics.isEmpty()) {
                return List.of();
            }

            //explode by pod and container
            List<ResourceMetrics> metricsList = podMetrics
                .stream()
                .flatMap(pm -> {
                    String podName = pm.getMetadata().getName();

                    return pm
                        .getContainers()
                        .stream()
                        .map(cm -> {
                            List<ResourceMetrics.Metrics> ml = new ArrayList<>();
                            cm
                                .getUsage()
                                .forEach((k, v) -> {
                                    ResourceMetrics.Metric metric = new ResourceMetrics.Metric(
                                        Instant.parse(pm.getTimestamp()).toEpochMilli(),
                                        v.getNumber().doubleValue()
                                    );
                                    ml.add(new ResourceMetrics.Metrics(k, null, List.of(metric), null));
                                });

                            return ResourceMetrics.builder()
                                .id("m_p-" + project + "-" + podName + "-" + cm.getName())
                                .project(project)
                                .metadata(Map.of("name", cm.getName(), "pod", podName, "container", cm.getName()))
                                .metrics(ml)
                                .build();
                        });
                })
                .toList();

            //for every metric check store and merge
            if (store != null) {
                metricsList.forEach(rm -> {
                    ResourceMetrics storedMetrics = store.findResourceMetrics(rm.getId());
                    if (storedMetrics != null && storedMetrics.getMetrics() != null) {
                        //merge with existing metrics
                        Map<String, ResourceMetrics.Metrics> existing =
                            rm.getMetrics() != null
                                ? rm
                                      .getMetrics()
                                      .stream()
                                      .collect(Collectors.toMap(ResourceMetrics.Metrics::name, e -> e))
                                : Map.of();

                        storedMetrics
                            .getMetrics()
                            .forEach(mm -> {
                                String k = mm.name();
                                List<ResourceMetrics.Metric> v = mm.metrics() != null ? mm.metrics() : List.of();
                                if (existing.containsKey(k)) {
                                    List<ResourceMetrics.Metric> existingMetrics = existing.get(k).metrics();
                                    List<ResourceMetrics.Metric> merged = existingMetrics
                                        .stream()
                                        .collect(Collectors.toList());
                                    merged.addAll(v);
                                    existing.put(k, new ResourceMetrics.Metrics(k, null, merged, null));
                                } else {
                                    existing.put(k, new ResourceMetrics.Metrics(k, null, v, null));
                                }
                            });

                        //make sure metrics lists are sorted by timestamp
                        existing.forEach((k, mm) -> {
                            List<ResourceMetrics.Metric> sorted = mm
                                .metrics()
                                .stream()
                                .sorted((m1, m2) -> Long.compare(m1.timestamp(), m2.timestamp()))
                                .toList();
                            existing.put(k, new ResourceMetrics.Metrics(k, null, sorted, null));
                        });

                        rm.setMetrics(new ArrayList<>(existing.values()));
                    }
                });
            }

            //build summaries
            metricsList.forEach(rm -> {
                if (rm.getMetrics() != null) {
                    rm.setMetrics(
                        rm
                            .getMetrics()
                            .stream()
                            .map(m ->
                                new ResourceMetrics.Metrics(m.name(), m.unit(), m.metrics(), summarize(m.metrics()))
                            )
                            .toList()
                    );
                }
            });

            return metricsList;
        } catch (StoreException e) {
            throw new SystemException("Error fetching metrics for project " + project, e);
        }
    }

    @Override
    public List<ResourceMetrics> listResourceMetricsByUser(@NotNull String user) throws SystemException {
        log.debug("fetch metrics from k8s for user {}: ", user);

        try {
            //fetch all from service
            List<PodMetrics> podMetrics = listPodMetrics("user", user);
            if (podMetrics == null || podMetrics.isEmpty()) {
                return List.of();
            }

            //explode by pod and container
            List<ResourceMetrics> metricsList = podMetrics
                .stream()
                .flatMap(pm -> {
                    String podName = pm.getMetadata().getName();

                    return pm
                        .getContainers()
                        .stream()
                        .map(cm -> {
                            List<ResourceMetrics.Metrics> ml = new ArrayList<>();
                            cm
                                .getUsage()
                                .forEach((k, v) -> {
                                    ResourceMetrics.Metric metric = new ResourceMetrics.Metric(
                                        Instant.parse(pm.getTimestamp()).toEpochMilli(),
                                        v.getNumber().doubleValue()
                                    );
                                    ml.add(new ResourceMetrics.Metrics(k, null, List.of(metric), null));
                                });

                            return ResourceMetrics.builder()
                                .id("m_u-" + user + "-" + podName + "-" + cm.getName())
                                .user(user)
                                .metadata(Map.of("name", cm.getName(), "pod", podName, "container", cm.getName()))
                                .metrics(ml)
                                .build();
                        });
                })
                .toList();

            //for every metric check store and merge
            if (store != null) {
                metricsList.forEach(rm -> {
                    ResourceMetrics storedMetrics = store.findResourceMetrics(rm.getId());
                    if (storedMetrics != null && storedMetrics.getMetrics() != null) {
                        //merge with existing metrics
                        Map<String, ResourceMetrics.Metrics> existing =
                            rm.getMetrics() != null
                                ? rm
                                      .getMetrics()
                                      .stream()
                                      .collect(Collectors.toMap(ResourceMetrics.Metrics::name, e -> e))
                                : Map.of();

                        storedMetrics
                            .getMetrics()
                            .forEach(mm -> {
                                String k = mm.name();
                                List<ResourceMetrics.Metric> v = mm.metrics() != null ? mm.metrics() : List.of();
                                if (existing.containsKey(k)) {
                                    List<ResourceMetrics.Metric> existingMetrics = existing.get(k).metrics();
                                    List<ResourceMetrics.Metric> merged = existingMetrics
                                        .stream()
                                        .collect(Collectors.toList());
                                    merged.addAll(v);
                                    existing.put(k, new ResourceMetrics.Metrics(k, null, merged, null));
                                } else {
                                    existing.put(k, new ResourceMetrics.Metrics(k, null, v, null));
                                }
                            });

                        //make sure metrics lists are sorted by timestamp
                        existing.forEach((k, mm) -> {
                            List<ResourceMetrics.Metric> sorted = mm
                                .metrics()
                                .stream()
                                .sorted((m1, m2) -> Long.compare(m1.timestamp(), m2.timestamp()))
                                .toList();
                            existing.put(k, new ResourceMetrics.Metrics(k, null, sorted, null));
                        });

                        rm.setMetrics(new ArrayList<>(existing.values()));
                    }
                });
            }

            //build summaries
            metricsList.forEach(rm -> {
                if (rm.getMetrics() != null) {
                    rm.setMetrics(
                        rm
                            .getMetrics()
                            .stream()
                            .map(m ->
                                new ResourceMetrics.Metrics(m.name(), m.unit(), m.metrics(), summarize(m.metrics()))
                            )
                            .toList()
                    );
                }
            });

            return metricsList;
        } catch (StoreException e) {
            throw new SystemException("Error fetching metrics for user " + user, e);
        }
    }

    @Override
    public List<ResourceMetrics> listResourceMetricsByRun(@NotNull String project, @NotNull String runId)
        throws SystemException {
        log.debug("fetch metrics from k8s for run {} in project {}: ", runId, project);

        try {
            List<ResourceMetrics> result = new ArrayList<>();
            //fetch all from service
            List<PodMetrics> podMetrics = listPodMetrics("run", runId);
            if (podMetrics != null && !podMetrics.isEmpty()) {
                //explode by pod and container
                List<ResourceMetrics> metricsList = podMetrics
                    .stream()
                    .flatMap(pm -> {
                        String podName = pm.getMetadata().getName();

                        return pm
                            .getContainers()
                            .stream()
                            .map(cm -> {
                                List<ResourceMetrics.Metrics> ml = new ArrayList<>();
                                cm
                                    .getUsage()
                                    .forEach((k, v) -> {
                                        ResourceMetrics.Metric metric = new ResourceMetrics.Metric(
                                            Instant.parse(pm.getTimestamp()).toEpochMilli(),
                                            v.getNumber().doubleValue()
                                        );
                                        ml.add(new ResourceMetrics.Metrics(k, null, List.of(metric), null));
                                    });

                                return ResourceMetrics.builder()
                                    .id("m_r-" + runId + "-" + podName + "-" + cm.getName())
                                    .run(runId)
                                    .metadata(Map.of("name", cm.getName(), "pod", podName, "container", cm.getName()))
                                    .metrics(ml)
                                    .build();
                            });
                    })
                    .toList();

                result.addAll(metricsList);
            }

            //also pick all historical metrics for the run from store, if available
            if (store != null) {
                List<ResourceMetrics> storedMetricsList = store.findResourceMetricsByRun(runId);

                //for every metric check store and merge
                storedMetricsList.forEach(rm -> {
                    ResourceMetrics storedMetrics = result
                        .stream()
                        .filter(sm -> sm.getId().equals(rm.getId()))
                        .findFirst()
                        .orElse(null);
                    if (storedMetrics != null && storedMetrics.getMetrics() != null) {
                        //merge with existing metrics
                        Map<String, ResourceMetrics.Metrics> existing =
                            rm.getMetrics() != null
                                ? rm
                                      .getMetrics()
                                      .stream()
                                      .collect(Collectors.toMap(ResourceMetrics.Metrics::name, e -> e))
                                : Map.of();

                        storedMetrics
                            .getMetrics()
                            .forEach(mm -> {
                                String k = mm.name();
                                List<ResourceMetrics.Metric> v = mm.metrics() != null ? mm.metrics() : List.of();
                                if (existing.containsKey(k)) {
                                    List<ResourceMetrics.Metric> existingMetrics = existing.get(k).metrics();
                                    List<ResourceMetrics.Metric> merged = existingMetrics
                                        .stream()
                                        .collect(Collectors.toList());
                                    merged.addAll(v);
                                    existing.put(k, new ResourceMetrics.Metrics(k, null, merged, null));
                                } else {
                                    existing.put(k, new ResourceMetrics.Metrics(k, null, v, null));
                                }
                            });

                        //make sure metrics lists are sorted by timestamp
                        existing.forEach((k, mm) -> {
                            List<ResourceMetrics.Metric> sorted = mm
                                .metrics()
                                .stream()
                                .sorted((m1, m2) -> Long.compare(m1.timestamp(), m2.timestamp()))
                                .toList();
                            existing.put(k, new ResourceMetrics.Metrics(k, null, sorted, null));
                        });

                        storedMetrics.setMetrics(new ArrayList<>(existing.values()));
                    } else {
                        //no existing metrics, just add the stored one
                        result.add(rm);
                    }
                });
            }

            //build summaries
            result.forEach(rm -> {
                if (rm.getMetrics() != null) {
                    rm.setMetrics(
                        rm
                            .getMetrics()
                            .stream()
                            .map(m ->
                                new ResourceMetrics.Metrics(m.name(), m.unit(), m.metrics(), summarize(m.metrics()))
                            )
                            .toList()
                    );
                }
            });

            return result;
        } catch (StoreException e) {
            throw new SystemException("Error fetching metrics for run " + runId, e);
        }
    }

    @Override
    public ResourceMetrics getResourceMetrics() throws SystemException {
        log.debug("fetch metrics from k8s for instance");

        try {
            //fetch now from service
            ContainerMetrics metrics = getContainerMetrics("instance", applicationProperties.getName());
            if (metrics == null) {
                return null;
            }

            Map<String, List<ResourceMetrics.Metric>> metricsMap = new HashMap<>();
            metrics
                .getUsage()
                .forEach((k, v) -> {
                    ResourceMetrics.Metric metric = new ResourceMetrics.Metric(
                        System.currentTimeMillis(),
                        v.getNumber().doubleValue()
                    );
                    metricsMap.put(k, List.of(metric));
                });

            //build
            List<ResourceMetrics.Metrics> metricsList = metricsMap
                .entrySet()
                .stream()
                .map(e -> new ResourceMetrics.Metrics(e.getKey(), null, e.getValue(), summarize(e.getValue())))
                .toList();

            return ResourceMetrics.builder().id("m_i-" + applicationProperties.getName()).metrics(metricsList).build();
        } catch (StoreException e) {
            throw new SystemException("Error fetching metrics for instance " + applicationProperties.getName(), e);
        }
    }

    @Override
    public List<ResourceMetrics> listResourceMetrics() throws SystemException {
        //return every metrics saved in store, if available
        if (store != null) {
            return store.findResourceMetrics();
        }

        return List.of();
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
