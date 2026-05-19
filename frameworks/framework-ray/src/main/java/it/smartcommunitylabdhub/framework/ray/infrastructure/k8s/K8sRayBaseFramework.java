/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.k8s;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.kubernetes.client.custom.ContainerMetrics;
import io.kubernetes.client.custom.PodMetrics;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1ContainerStatus;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.CreateOptions;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import it.smartcommunitylabdhub.commons.exceptions.FrameworkException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLog;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreMetric;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import it.smartcommunitylabdhub.framework.k8s.service.K8sMetricsService;
import it.smartcommunitylabdhub.framework.ray.model.ClusterModel;
import it.smartcommunitylabdhub.framework.ray.model.PodModel;
import it.smartcommunitylabdhub.framework.ray.model.WorkerGroupModel;
import it.smartcommunitylabdhub.framework.ray.model.ray.HeadGroupSpec;
import it.smartcommunitylabdhub.framework.ray.model.ray.RayClusterSpec;
import it.smartcommunitylabdhub.framework.ray.model.ray.WorkerGroupSpec;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayRunnable;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Base framework for managing Ray operator custom resources (RayCluster, RayJob, RayService).
 *
 * Concrete subclasses bind a single Ray CR kind/plural and the corresponding runnable type.
 * The CR spec is treated as an opaque map, so callers are responsible for producing a valid
 * Ray spec; this framework injects only metadata (name, labels, namespace).
 */
@Slf4j
public abstract class K8sRayBaseFramework<T extends K8sRayRunnable<?>>
    extends K8sBaseFramework<T, DynamicKubernetesObject> {

    protected static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    protected final ApiClient apiClient;

    protected String apiGroup = "ray.io";
    protected String apiVersion = "v1";

    private boolean suspend = false;
    private CoreServiceType serviceType = CoreServiceType.ClusterIP;

    protected String initImage;
    protected List<String> initCommand = null;

    /**
     * Object mapper for serializing Ray CR specs.
     *
     * <p>Based on {@link JacksonMapper#CUSTOM_OBJECT_MAPPER} but with a custom
     * serializer for {@link Quantity} that writes the suffixed string form
     * (e.g. "1Gi", "500m") instead of the default structured object form.
     * The KubeRay operator does not accept the structured object form for
     * resource quantities.
     */
    protected static final ObjectMapper mapper = KubernetesMapper.OBJECT_MAPPER.copy()
        .registerModule(
            new SimpleModule().addSerializer(
                Quantity.class,
                new JsonSerializer<Quantity>() {
                    @Override
                    public void serialize(Quantity value, JsonGenerator gen, SerializerProvider serializers)
                        throws IOException {
                        gen.writeString(value.toSuffixedString());
                    }
                }
            )
        );

    protected K8sRayBaseFramework(ApiClient apiClient) {
        super(apiClient);
        this.apiClient = apiClient;
    }

    @Autowired
    public void setApiGroup(@Value("${ray.api-group:ray.io}") String apiGroup) {
        if (StringUtils.hasText(apiGroup)) {
            this.apiGroup = apiGroup;
        }
    }

    @Autowired
    public void setApiVersion(@Value("${ray.api-version:v1}") String apiVersion) {
        if (StringUtils.hasText(apiVersion)) {
            this.apiVersion = apiVersion;
        }
    }

    @Autowired
    public void setSuspend(@Value("${ray.job.suspend}") Boolean suspend) {
        if (suspend != null) {
            this.suspend = suspend.booleanValue();
        }
    }

    @Autowired
    public void setServiceType(@Value("${ray.head.service-type}") String serviceType) {
        if (StringUtils.hasText(serviceType)) {
            try {
                this.serviceType = CoreServiceType.valueOf(serviceType);
            } catch (IllegalArgumentException e) {
                log.error("Invalid service type: {}", serviceType);
                throw new IllegalArgumentException("Invalid service type: " + serviceType, e);
            }
        }
    }

    @Autowired
    public void setInitImage(@Value("${kubernetes.init.image}") String initImage) {
        this.initImage = initImage;
    }

    @Autowired
    public void setInitCommand(@Value("${kubernetes.init.command}") String initCommand) {
        if (StringUtils.hasText(initCommand)) {
            this.initCommand =
                new LinkedList<>(Arrays.asList(StringUtils.commaDelimitedListToStringArray(initCommand)));
        }
    }

    /**
     * @return CR kind, e.g. {@code RayCluster}
     */
    protected abstract String getKind();

    /**
     * @return CR plural, e.g. {@code rayclusters}
     */
    protected abstract String getPlural();

    /**
     * Extract the Ray spec from the runnable.
     */
    protected abstract Map<String, Serializable> getSpec(T runnable, RayClusterSpec clusterSpec) throws K8sFrameworkException;

    /**
     * Persist the observed CR status onto the runnable.
     */
    protected abstract void setStatus(T runnable, Map<String, Serializable> status);

    /**
     * Provide a fresh builder for the concrete runnable subclass {@code T}.
     *
     * <p>This indirection is required because Java erases the static {@code builder()} call
     * to the upper bound (i.e. {@code K8sRayRunnable.builder()}) when invoked through the
     * type variable {@code T}, which produces an instance of the wrong type and breaks the
     * cast inside {@link it.smartcommunitylabdhub.framework.ray.model.PodModel#toK8sRunnable}.
     */
    protected abstract K8sRunnable.K8sRunnableBuilder<?, ?> newRunnableBuilder();


    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        Assert.hasText(initImage, "init image should be set to a valid builder-tool image");
    }

    @Override
    public T run(T runnable) throws FrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        Map<String, Object> results = new HashMap<>();

        // create Ray CR from runnable spec
        DynamicKubernetesObject cr = build(runnable);

        //secrets
        V1Secret secret = buildRunSecret(runnable);
        if (secret != null) {
            storeRunSecret(secret);
            //clear data before storing
            results.put("secret", secret.stringData(Collections.emptyMap()).data(Collections.emptyMap()));
        }

        //configmap: needed for RayJob to setup entrypoint and workdir
        try {
            V1ConfigMap initConfigMap = buildInitConfigMap(runnable);
            if (initConfigMap != null) {
                log.info("create initConfigMap for {}", String.valueOf(initConfigMap.getMetadata().getName()));
                coreV1Api.createNamespacedConfigMap(namespace, initConfigMap, null, null, null, null);
                //clear data before storing
                results.put("configMap", initConfigMap.data(Collections.emptyMap()));
            }
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }

        //pvcs only for head for now, as workers are managed by the operator and can't be guaranteed to be created at runnable start time; if needed in the future we can add support for operator-managed volumes and let the operator create them based on a spec in the CR
        List<V1PersistentVolumeClaim> pvcs = buildPersistentVolumeClaims(runnable);
        if (pvcs != null) {
            List<V1PersistentVolumeClaim> pvcsFinal = new ArrayList<>();
            for (V1PersistentVolumeClaim pvc : pvcs) {
                log.info("create pvc for {}", String.valueOf(pvc.getMetadata().getName()));
                try {
                    V1PersistentVolumeClaim v = coreV1Api.createNamespacedPersistentVolumeClaim(
                        namespace,
                        pvc,
                        null,
                        null,
                        null,
                        null
                    );
                    pvcsFinal.add(v);
                } catch (ApiException e) {
                    log.error("Error with k8s: {}", e.getMessage());
                    if (log.isTraceEnabled()) {
                        log.trace("k8s api response: {}", e.getResponseBody());
                    }

                    throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
                }
            }

            //store
            results.put("pvcs", pvcs);
        }

        log.info("create Ray {} for {}", getKind(), String.valueOf(cr.getMetadata().getName()));
        DynamicKubernetesApi dynamicApi = getDynamicKubernetesApi();
        cr = create(cr, dynamicApi);

        try {
            Map<String, Serializable> spec = jsonElementToMap(cr.getRaw());
            results.put(cr.getKind(), spec);
        } catch (Exception e) {
            log.error("error converting Ray CR to map: {}", e.getMessage());
        }

        //update state
        runnable.setState(K8sRunnableState.PENDING.name());

        if (!"disable".equals(collectResults)) {
            try {
                runnable.setResults(
                    results
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Entry::getKey, e ->  mapper.convertValue(e, typeRef)))
                );
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }
        }

        runnable.setMessage(
            String.format("Ray %s %s created", getKind(), cr.getMetadata().getName())
        );

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public T stop(T runnable) throws K8sFrameworkException {
        log.info("stop for {}", runnable.getId());
        //stop by deleting the CR
        runnable = delete(runnable);
        runnable.setState(K8sRunnableState.STOPPED.name());
        return runnable;
    }

    @Override
    public T delete(T runnable) throws K8sFrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        List<String> messages = new ArrayList<>();

        DynamicKubernetesApi dynamicApi = getDynamicKubernetesApi();
        DynamicKubernetesObject cr;
        try {
            cr = get(build(runnable), dynamicApi);
        } catch (K8sFrameworkException | IllegalArgumentException e) {
            runnable.setState(K8sRunnableState.DELETED.name());
            return runnable;
        }

        if (cr != null) {
            log.info("delete Ray {} for {}", getKind(), String.valueOf(cr.getMetadata().getName()));
            delete(cr, dynamicApi);
            messages.add(String.format("Ray %s %s deleted", getKind(), cr.getMetadata().getName()));
        }

        //secrets
        cleanRunSecret(runnable);

        //init config map
        try {
            String configMapName = "init-config-map-" + runnable.getId();
            V1ConfigMap initConfigMap = coreV1Api.readNamespacedConfigMap(configMapName, namespace, null);
            if (initConfigMap != null) {
                coreV1Api.deleteNamespacedConfigMap(configMapName, namespace, null, null, null, null, null, null, null);
                messages.add(String.format("configMap %s deleted", configMapName));
            }
        } catch (ApiException | NullPointerException e) {
            //ignore, not existing or error
        }

        try {
            runnable.setResults(Collections.emptyMap());
        } catch (IllegalArgumentException e) {
            log.error("error reading k8s results: {}", e.getMessage());
        }

        List<V1PersistentVolumeClaim> pvcs = buildPersistentVolumeClaims(runnable);
        if (pvcs != null) {
            for (V1PersistentVolumeClaim pvc : pvcs) {
                String pvcName = pvc.getMetadata().getName();
                try {
                    V1PersistentVolumeClaim v = coreV1Api.readNamespacedPersistentVolumeClaim(pvcName, namespace, null);
                    if (v != null) {
                        log.info("delete pvc for {}", String.valueOf(pvcName));

                        coreV1Api.deleteNamespacedPersistentVolumeClaim(
                            pvcName,
                            namespace,
                            null,
                            null,
                            null,
                            null,
                            null,
                            "Background",
                            null
                        );
                        messages.add(String.format("pvc %s deleted", pvcName));
                    }
                } catch (ApiException e) {
                    log.error("Error with k8s: {}", e.getMessage());
                    if (log.isTraceEnabled()) {
                        log.trace("k8s api response: {}", e.getResponseBody());
                    }
                    //don't propagate
                    // throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
                }
            }
        }

        runnable.setState(K8sRunnableState.DELETED.name());
        runnable.setMessage(String.join(", ", messages));

        return runnable;
    }

    /**
     * Build a {@link DynamicKubernetesObject} representation of the CR for this runnable.
     * Only metadata (name, labels, namespace) and spec are populated; status is left to the
     * Ray operator.
     * @throws K8sFrameworkException 
     */
    public DynamicKubernetesObject build(T runnable) throws K8sFrameworkException {
        DynamicKubernetesObject obj = new DynamicKubernetesObject();

        String crName = getResourceName(runnable.getRuntime(), runnable.getTask(), runnable.getId());

        obj.setApiVersion(apiGroup + "/" + apiVersion);
        obj.setKind(getKind());

        // build labels
        Map<String, String> labels = buildLabels(runnable);

        // metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(crName).labels(labels).namespace(namespace);
        obj.setMetadata(metadata);

        ClusterModel cluster = runnable.getSpec().getCluster();
        RayClusterSpec.RayClusterSpecBuilder clusterSpec = RayClusterSpec.builder();
        //opt-int for suspend==true
        if (suspend) {
            clusterSpec.suspend(suspend);
        }
        clusterSpec.rayVersion(cluster.getVersion());
        

        Map<String, String> podLabels = Collections.singletonMap("ray.io/originated-from-cr-name", crName);
        podLabels = MapUtils.mergeMultipleMaps(podLabels, labels); //merge with runnable labels, which may contain useful info for selection and are not mutually exclusive with ray operator labels

        V1Service service = null;
        //head group: no context, but service and ports 
        V1PodSpec head = convertPodModel(runnable, "head", cluster.getHeadSpec(), false, true);
        if (cluster.getHeadServiceType() != null) {
            serviceType = cluster.getHeadServiceType();
        }   
        HeadGroupSpec headSpec = HeadGroupSpec
            .builder()
            .template(new V1PodTemplateSpec()
                .metadata(new V1ObjectMeta().labels(podLabels))
                .spec(head))
            .rayStartParams(cluster.getHeadSpec().getStartParams())
            .resources(cluster.getHeadSpec().getRayResources())
            .headService(service)
            .serviceType(serviceType.name())
            .labels(convertLabels(cluster.getHeadSpec().getLabels()))
            .build();    
        clusterSpec.headGroupSpec(headSpec);
        
        //worker groups
        List<WorkerGroupSpec> workerGroupSpecs = new LinkedList<>();
        for (WorkerGroupModel worker : cluster.getWorkerGroups()) {
            V1PodSpec workerPod = convertPodModel(runnable, "worker", worker.getWorkerSpec(), runnable.initAllPods(), true);

            WorkerGroupSpec workerGroupSpec = WorkerGroupSpec
                .builder()
                .template(new V1PodTemplateSpec()
                    .metadata(new V1ObjectMeta().labels(podLabels))
                    .spec(workerPod))
                .groupName(worker.getName())
                .replicas(worker.getReplicas())
                .maxReplicas(worker.getMaxReplicas())
                .minReplicas(worker.getMinReplicas())
                .rayStartParams(worker.getWorkerSpec().getStartParams())
                .resources(worker.getWorkerSpec().getRayResources())
                .labels(convertLabels(worker.getWorkerSpec().getLabels()))
                .build();
            workerGroupSpecs.add(workerGroupSpec);
        }
        clusterSpec.workerGroupSpecs(workerGroupSpecs);        

        Map<String, Serializable> spec = getSpec(runnable, clusterSpec.build());
        if (spec != null) {
            obj.getRaw().add("spec", mapToJsonElement(spec));
        }

        return obj;
    }

    public String getResourceName(String prefix, String task, String id) {
        return K8sBuilderHelper.sanitizeNames(prefix + "-" + task + "-" + id);
    }




    private Map<String, String> convertLabels(List<CoreLabel> labels) {
        if (labels == null) {
            return Collections.emptyMap();
        }
        return labels.stream().collect(Collectors.toMap(CoreLabel::name, CoreLabel::value));
    }

    public DynamicKubernetesObject get(@NotNull DynamicKubernetesObject cr, DynamicKubernetesApi dynamicApi)
        throws K8sFrameworkException {
        Assert.notNull(cr.getMetadata(), "metadata can not be null");
        try {
            String crName = cr.getMetadata().getName();
            log.debug("get Ray {} for {}", getKind(), crName);
            return dynamicApi.get(namespace, crName, null).getObject();
        } catch (Exception e) {
            log.info("Error with k8s: {}", e.getMessage());
            throw new K8sFrameworkException(e.getMessage(), e.getMessage());
        }
    }

    @Override
    public DynamicKubernetesObject get(DynamicKubernetesObject obj) throws K8sFrameworkException {
        if (obj == null || obj.getMetadata() == null) {
            return obj;
        }
        return get(obj, getDynamicKubernetesApi());
    }

    public DynamicKubernetesApi getDynamicKubernetesApi() {
        return new DynamicKubernetesApi(apiGroup, apiVersion, getPlural(), apiClient);
    }

    private DynamicKubernetesObject create(DynamicKubernetesObject cr, DynamicKubernetesApi dynamicApi)
        throws K8sFrameworkException {
        Assert.notNull(cr.getMetadata(), "metadata can not be null");
        try {
            String crName = cr.getMetadata().getName();
            log.debug("create Ray {} for {}", getKind(), crName);

            KubernetesApiResponse<DynamicKubernetesObject> result = dynamicApi.create(
                namespace,
                cr,
                new CreateOptions()
            );

            if (result.isSuccess()) {
                return result.getObject();
            }
            throw new RuntimeException(result.getStatus().getMessage());
        } catch (Exception e) {
            log.error("Error with k8s: {}", e.getMessage());
            throw new K8sFrameworkException(e.getMessage(), e.getMessage());
        }
    }

    private void delete(DynamicKubernetesObject cr, DynamicKubernetesApi dynamicApi) throws K8sFrameworkException {
        Assert.notNull(cr.getMetadata(), "metadata can not be null");
        try {
            String crName = cr.getMetadata().getName();
            log.debug("delete Ray {} for {}", getKind(), crName);

            DeleteOptions options = new DeleteOptions();
            options.setPropagationPolicy("Foreground");

            dynamicApi.delete(namespace, crName, options);
        } catch (Exception e) {
            log.error("Error with k8s: {}", e.getMessage());
            throw new K8sFrameworkException(e.getMessage(), e.getMessage());
        }
    }

    /**
     * Returns the label selector to match pods owned by this CR. The Ray operator labels every
     * pod it creates with {@code ray.io/cluster=<cluster-name>} (for RayCluster, RayJob, and
     * RayService all pods belong to a backing RayCluster). Subclasses may override to scope
     * differently.
     */
    protected String podLabelSelector(DynamicKubernetesObject object) {
        if (object == null || object.getMetadata() == null) {
            return null;
        }
        String name = object.getMetadata().getName();
        return "ray.io/originated-from-cr-name=" + name;
    }

    @Override
    public List<V1Pod> pods(DynamicKubernetesObject object) throws K8sFrameworkException {
        if (object == null || object.getMetadata() == null) {
            return null;
        }

        //try super first (label-based via runnable.getLabels())
        List<V1Pod> items = super.pods(object);
        if (items != null && !items.isEmpty()) {
            return items;
        }

        //fall back to ray operator-managed labels
        String selector = podLabelSelector(object);
        if (selector == null) {
            return null;
        }

        try {
            log.debug("load pods for selector {}", selector);
            //primary selector
            V1PodList pods = coreV1Api.listNamespacedPod(
                namespace,
                null,
                null,
                null,
                null,
                selector,
                null,
                null,
                null,
                null,
                null,
                null
            );
            if (pods.getItems() != null && !pods.getItems().isEmpty()) {
                return pods.getItems();
            }
            return Collections.emptyList();
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }
            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    /**
     * Convert a Java map (the runnable spec) to a Gson {@link JsonElement} suitable for the
     * dynamic kubernetes object payload.
     */
    public static JsonElement mapToJsonElement(Map<String, Serializable> map) {
        Gson gson = new Gson();
        String json = gson.toJson(map);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        return jsonObject;
    }

    /**
     * Convert a Gson {@link JsonElement} (e.g. the raw CR or a sub-tree of it) into a
     * serializable map.
     */
    public static HashMap<String, Serializable> jsonElementToMap(JsonElement jsonElement) throws IOException {
        Gson gson = new Gson();
        String json = gson.toJson(jsonElement);
        return mapper.readValue(json, typeRef);
    }

    /**
     * Helper used by monitors to extract a sub-element as a serializable map.
     */
    public static Map<String, Serializable> extractMap(DynamicKubernetesObject cr, String field) {
        if (cr == null || cr.getRaw() == null) {
            return null;
        }
        JsonElement el = cr.getRaw().get(field);
        if (el == null || el.isJsonNull() || !el.isJsonObject()) {
            return null;
        }
        try {
            return jsonElementToMap(el);
        } catch (IOException e) {
            log.error("error converting field {} to map: {}", field, e.getMessage());
            return null;
        }
    }

    //convenience for subclasses building runnable-specific labels via a transformer
    protected <X> Map<String, X> identityMap(Map<String, X> in, Function<X, X> fn) {
        if (in == null) {
            return Collections.emptyMap();
        }
        return in.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> fn.apply(e.getValue())));
    }

    /**
     * Convert a {@link PodModel} (which is framework-agnostic) into a Kubernetes {@link V1PodSpec}, applying
     * a template if specified. This is used by both RayService and RayJob to build the pod spec for the head and worker pods.
     * @param runId used for naming and labeling the pod, should be unique for each runnable execution
     * @param containerName the name of the container within the pod: e.g., the head container to be named "ray-head" and the worker container to be named "ray-worker"
     * @param podModel the pod model containing the specifications for the pod
     * @param withContext whether to include context information in the pod spec (e.g., for mounting config maps or secrets with context data); this is needed for RayJob to setup entrypoint and workdir, but not for RayService
     * @param withResources whether to include resource specifications in the pod spec (e.g., CPU and memory limits); this is needed for RayJob to setup resource constraints, but not for RayService
     * @return the constructed {@link V1PodSpec}
     * @throws K8sFrameworkException if there is an error converting the pod model
     */
    protected V1PodSpec convertPodModel(T parent, String name, PodModel podModel, boolean withContext, boolean withResources) throws K8sFrameworkException {
        if (podModel == null) {
            return null;
        }

        //check template
        K8sTemplate<T> template = null;
        if (StringUtils.hasText(podModel.getTemplate()) && templates.containsKey(podModel.getTemplate())) {
            //get template
            template = templates.get(podModel.getTemplate());
        } else if (templates.containsKey(DEFAULT_TEMPLATE)) {
            //use default template
            template = templates.get(DEFAULT_TEMPLATE);
        }
        
        T runnable = podModel.toK8sRunnable(parent, name, newRunnableBuilder(), withContext);
    
        // Prepare environment variables for the Kubernetes job
        List<V1EnvFromSource> envFrom = buildEnvFrom(runnable);
        List<V1EnvVar> env = buildEnv(runnable);

        // Volumes to attach to the pod based on the volume spec with the additional volume_type
        List<V1Volume> volumes = buildVolumes(runnable);
        List<V1VolumeMount> volumeMounts = buildVolumeMounts(runnable);

        // resources
        V1ResourceRequirements resources = buildResources(runnable);

        //image policy
        String imagePullPolicy = runnable.getImagePullPolicy() != null
            ? runnable.getImagePullPolicy().name()
            : defaultImagePullPolicy;

        // Build Container
        V1Container container = new V1Container()
            .name("ray-" + runnable.getId())
            .image(runnable.getImage())
            .imagePullPolicy(imagePullPolicy)
            .command(podModel.getCommand() != null ? List.of(podModel.getCommand()) : null)
            .args(podModel.getArgs() != null ? List.of(podModel.getArgs()) : null)
            .resources(withResources ? resources : null)
            .volumeMounts(volumeMounts)
            .envFrom(envFrom)
            .env(env)
            .securityContext(buildSecurityContext(runnable));


        V1PodSpec podSpec = Optional
            .ofNullable(template)
            .map(K8sTemplate::getJob)
            .map(V1Job::getSpec)
            .map(V1JobSpec::getTemplate)
            .map(V1PodTemplateSpec::getSpec)
            .orElse(new V1PodSpec());


        // Create a PodSpec for the container, leverage template if provided
        podSpec
            .containers(Collections.singletonList(container))
            .nodeSelector(buildNodeSelector(runnable))
            .affinity(buildAffinity(runnable))
            .tolerations(buildTolerations(runnable))
            .runtimeClassName(buildRuntimeClassName(runnable))
            .priorityClassName(buildPriorityClassName(runnable))
            .volumes(volumes)
            // .restartPolicy("Never")
            .imagePullSecrets(buildImagePullSecrets(runnable))
            .securityContext(buildPodSecurityContext(runnable));

        //check if context build is required
        if (
            (runnable.getContextRefs() != null && !runnable.getContextRefs().isEmpty()) ||
            (runnable.getContextSources() != null && !runnable.getContextSources().isEmpty())
        ) {
            // Add Init container to the PodTemplateSpec
            // Build the Init Container
            V1Container initContainer = new V1Container()
                .name("init-container-" + runnable.getId())
                .image(initImage)
                .volumeMounts(
                    volumeMounts
                        .stream()
                        .filter(v ->
                            k8sProperties.getSharedVolume().getMountPath().equals(v.getMountPath()) ||
                            "/init-config-map".equals(v.getMountPath())
                        )
                        .collect(Collectors.toList())
                )
                .resources(withResources ? resources : null)
                .env(env)
                .envFrom(envFrom)
                .securityContext(buildSecurityContext(runnable))
                .command(initCommand);

            podSpec.setInitContainers(Collections.singletonList(initContainer));
        }

        return podSpec;
    }

    /**
     * Extract logs for the given pods and runnable. By default, logs are extracted for all containers (including init containers) in the pod. 
     * Subclasses may override to filter specific containers or apply other custom logic.
     * @param pods
     * @param runnable
     * @return
     * @throws K8sFrameworkException
     */
    public List<CoreLog> logs(List<V1Pod> pods, T runnable) throws K8sFrameworkException {
        if (pods == null || pods.isEmpty()) {
            return null;
        }

        if (Boolean.TRUE != collectLogs) {
            return Collections.emptyList();
        }

        List<CoreLog> logs = new ArrayList<>();

        for (V1Pod p : pods) {
            if (p.getMetadata() != null && p.getStatus() != null) {
                String pod = p.getMetadata().getName();

                //read init-containers first
                if (p.getStatus().getInitContainerStatuses() != null) {
                    List<V1ContainerStatus> containers = p.getStatus().getInitContainerStatuses();

                    for (V1ContainerStatus c : containers) {
                        try {
                            String log = coreV1Api.readNamespacedPodLog(
                                pod,
                                namespace,
                                c.getName(),
                                Boolean.FALSE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                            );

                            String id = c.getContainerID() != null ? URI.create(c.getContainerID()).getHost() : null;

                            logs.add(new CoreLog(pod, log, c.getName(), namespace, id));
                        } catch (ApiException e) {
                            //catch and skip this container's logs
                            log.error("Error with k8s: {}", e.getMessage());
                            if (log.isTraceEnabled()) {
                                log.trace("k8s api response: {}", e.getResponseBody());
                            }
                        }
                    }
                }

                //read container
                if (p.getStatus().getContainerStatuses() != null) {
                    List<V1ContainerStatus> containers = p.getStatus().getContainerStatuses();
                    for (V1ContainerStatus c : containers) {
                        try {
                            String log = coreV1Api.readNamespacedPodLog(
                                pod,
                                namespace,
                                c.getName(),
                                Boolean.FALSE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                            );

                            String id = c.getContainerID() != null ? URI.create(c.getContainerID()).getHost() : null;

                            logs.add(new CoreLog(pod, log, c.getName(), namespace, id));
                        } catch (ApiException e) {
                            //catch and skip this container's logs
                            log.error("Error with k8s: {}", e.getMessage());
                            if (log.isTraceEnabled()) {
                                log.trace("k8s api response: {}", e.getResponseBody());
                            }
                        }
                    }
                }
            }
        }

        return logs;
    }

    /**
     * Extract metrics for the given pods and runnable. By default, metrics are aggregated at the pod level across all containers, mirroring the convention used in K8sMetricsService.
     * The first pod/container in the status pods is used to give the name of the metric
     * @param pods the list of pods to extract metrics from
     * @param runnable the runnable context
     * @return the list of extracted metrics
     * @throws K8sFrameworkException if an error occurs while extracting metrics
     */
    public List<CoreMetric> metrics(List<V1Pod> pods, T runnable) throws K8sFrameworkException {
        if (pods == null || pods.isEmpty()) {
            return null;
        }

        if (Boolean.TRUE != collectMetrics) {
            return Collections.emptyList();
        }

        List<V1Pod> statusPods = statusPods(pods, runnable);
        if (statusPods == null || statusPods.isEmpty()) {
            return Collections.emptyList();
        }

        String name = null, containerName = null;
        V1Pod firstStatusPod = statusPods.get(0);
        if (firstStatusPod.getMetadata() != null && firstStatusPod.getMetadata().getName() != null) {
            name = firstStatusPod.getMetadata().getName();
            containerName = firstStatusPod.getSpec() != null && firstStatusPod.getSpec().getContainers() != null && !firstStatusPod.getSpec().getContainers().isEmpty()
                ? firstStatusPod.getSpec().getContainers().get(0).getName()
                : null;
        }

        try {
            List<PodMetrics> filtered = new ArrayList<>();
            String latestTimestamp = null;
            String latestWindow = null;

            // pod names
            Set<String> podNames = pods.stream()
                .filter(p -> p.getMetadata() != null && p.getMetadata().getName() != null)
                .map(p -> p.getMetadata().getName()).collect(Collectors.toSet());

            // pod metrics for the identified pods    
            List<PodMetrics> podMetrics = metricsApi
                .getPodMetrics(namespace)
                .getItems().stream()
                .filter(m -> m.getMetadata() != null && podNames.contains(m.getMetadata().getName()))
                .collect(Collectors.toList());

            // aggregate usage across all identified pods/containers
            ContainerMetrics aggregated = new ContainerMetrics();
            aggregated.setUsage(new HashMap<>());
            aggregated.setName(containerName != null ? containerName : "aggregate");
            for (PodMetrics m : podMetrics) {
                // skip empty
                if (m.getContainers() == null) {
                    continue;
                }
                // pick first container
                if (podNames.contains(m.getMetadata().getName())) {
                    filtered.add(m);
                    podNames.remove(m.getMetadata().getName());
                }
            
                // latest timestamp and window across all pods, mirroring the convention used in K8sMetricsService
                if (m.getTimestamp() != null) {
                    if (latestTimestamp == null || m.getTimestamp().compareTo(latestTimestamp) > 0 && m.getWindow() != null) {
                        latestTimestamp = m.getTimestamp();
                        latestWindow = m.getWindow();
                    }
                }
            }
            K8sMetricsService.mergePodMetrics(aggregated, filtered);
            //track number of pods aggregated, mirroring the convention used in K8sMetricsService
            return Collections.singletonList(
                new CoreMetric(
                    name != null ? name : "aggregate",
                    Collections.singletonList(aggregated),
                    latestTimestamp,
                    latestWindow,
                    namespace
                )
            );
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    /**
     * Should return V1Pod list  with objects used as log and metrics containers
     * @param pods the list of pods to filter
     * @param runnable the runnable context
     * @return the filtered list of pods
     * @throws K8sFrameworkException
     */
    public List<V1Pod> statusPods(List<V1Pod> pods, T runnable) throws K8sFrameworkException {
        return pods;
    }
}
