package it.smartcommunitylabdhub.framework.k8s.service;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import io.kubernetes.client.Metrics;
import io.kubernetes.client.custom.ContainerMetrics;
import io.kubernetes.client.custom.PodMetrics;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import jakarta.annotation.Nonnull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.util.Assert;

@Slf4j
public class K8sMetricsService implements InitializingBean {

    public static final long CACHE_TIMEOUT = 30; //seconds

    protected final CoreV1Api coreV1Api;
    protected final Metrics metricsApi;

    protected ApplicationProperties applicationProperties;
    protected K8sLabelHelper k8sLabelHelper;
    protected Boolean collectMetrics;
    protected String namespace;

    //loading cache for project metrics
    LoadingCache<Pair<String, String>, ContainerMetrics> cache = CacheBuilder
        .newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.SECONDS)
        .build(
            new CacheLoader<Pair<String, String>, ContainerMetrics>() {
                @Override
                public ContainerMetrics load(@Nonnull Pair<String, String> key) throws Exception {
                    return metrics(key.getFirst(), key.getSecond());
                }
            }
        );

    public K8sMetricsService(ApiClient apiClient) {
        Assert.notNull(apiClient, "k8s api client is required");
        coreV1Api = new CoreV1Api(apiClient);
        metricsApi = new Metrics(apiClient);
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

    public ContainerMetrics getMetrics(String entity, String project) throws StoreException {
        try {
            return cache.get(Pair.of(entity, project));
        } catch (Exception e) {
            throw new StoreException("Error getting metrics for project " + project, e);
        }
    }

    private ContainerMetrics metrics(@Nonnull String key, @Nonnull String value) throws StoreException {
        if (Boolean.TRUE != collectMetrics) {
            return null;
        }

        try {
            ContainerMetrics metrics = new ContainerMetrics();
            metrics.setName(key);
            metrics.setUsage(new HashMap<>());

            //aggregate metrics for all pods matching
            List<PodMetrics> podMetrics = metricsApi
                .getPodMetrics(namespace)
                .getItems()
                .stream()
                .filter(m -> m.getMetadata() != null && m.getMetadata().getLabels() != null)
                .filter(m -> {
                    Map<String, String> labels = k8sLabelHelper.extractCoreLabels(m.getMetadata().getLabels());
                    return labels != null && value.equals(labels.get(key));
                })
                .toList();

            podMetrics.forEach(pm -> {
                if (pm.getContainers() != null) {
                    pm
                        .getContainers()
                        .forEach(cm -> {
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

            return metrics;
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            return null;
        }
    }
}
