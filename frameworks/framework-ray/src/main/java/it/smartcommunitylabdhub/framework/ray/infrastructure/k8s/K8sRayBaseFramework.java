/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.framework.ray.infrastructure.k8s;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.CreateOptions;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import it.smartcommunitylabdhub.commons.exceptions.FrameworkException;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import it.smartcommunitylabdhub.framework.ray.exceptions.K8sRayFrameworkException;
import it.smartcommunitylabdhub.framework.ray.model.PodModel;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
public abstract class K8sRayBaseFramework<T extends K8sRunnable>
    extends K8sBaseFramework<T, DynamicKubernetesObject> {

    protected static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    protected final ApiClient apiClient;

    protected String apiGroup = "ray.io";
    protected String apiVersion = "v1";

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
    protected abstract Map<String, Serializable> getSpec(T runnable);

    /**
     * Whether the runnable wants a backing secret to be materialized.
     */
    protected abstract boolean isRequiresSecret(T runnable);

    /**
     * Persist the observed CR status onto the runnable.
     */
    protected abstract void setStatus(T runnable, Map<String, Serializable> status);

    @Override
    public T run(T runnable) throws FrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        Map<String, Object> results = new HashMap<>();

        //secrets
        V1Secret secret = buildRunSecret(runnable);
        if (secret != null) {
            storeRunSecret(secret);
            //clear data before storing
            results.put("secret", secret.stringData(Collections.emptyMap()).data(Collections.emptyMap()));
        }

        // TODO manage config map, pvcs, shared volumes

        DynamicKubernetesObject cr = build(runnable);
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
                        .collect(Collectors.toMap(Entry::getKey, e -> mapper.convertValue(e.getValue(), typeRef)))
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
        if (isRequiresSecret(runnable)) {
            cleanRunSecret(runnable);
        }

        try {
            runnable.setResults(Collections.emptyMap());
        } catch (IllegalArgumentException e) {
            log.error("error reading k8s results: {}", e.getMessage());
        }

        runnable.setState(K8sRunnableState.DELETED.name());
        runnable.setMessage(String.join(", ", messages));

        return runnable;
    }

    @Override
    protected V1Secret buildRunSecret(T runnable) {
        if (isRequiresSecret(runnable)) {
            return super.buildRunSecret(runnable);
        }
        return null;
    }

    /**
     * Build a {@link DynamicKubernetesObject} representation of the CR for this runnable.
     * Only metadata (name, labels, namespace) and spec are populated; status is left to the
     * Ray operator.
     */
    public DynamicKubernetesObject build(T runnable) {
        DynamicKubernetesObject obj = new DynamicKubernetesObject();

        String crName = StringUtils.hasText(runnable.getId())
            ? K8sBuilderHelper.sanitizeNames(runnable.getId())
            : runnable.getId();

        obj.setApiVersion(apiGroup + "/" + apiVersion);
        obj.setKind(getKind());

        Map<String, String> labels = buildLabels(runnable);

        V1ObjectMeta metadata = new V1ObjectMeta().name(crName).labels(labels).namespace(namespace);
        obj.setMetadata(metadata);

        Map<String, Serializable> spec = getSpec(runnable);
        if (spec != null) {
            obj.getRaw().add("spec", mapToJsonElement(spec));
        }

        return obj;
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
        return "ray.io/cluster=" + name + ",ray.io/originated-from-cr-name=" + name;
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

            //fallback: match by ray.io/cluster only (RayJob may rename underlying cluster)
            String fallback = "ray.io/originated-from-cr-name=" + object.getMetadata().getName();
            V1PodList retry = coreV1Api.listNamespacedPod(
                namespace,
                null,
                null,
                null,
                null,
                fallback,
                null,
                null,
                null,
                null,
                null,
                null
            );
            return retry.getItems();
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
     * @param command the command to run in the container
     * @param args the arguments to pass to the command
     * @return the constructed {@link V1PodSpec}
     * @throws K8sFrameworkException if there is an error converting the pod model
     */
    protected V1PodSpec convertPodModel(String runId, String containerName, PodModel podModel, List<String> command, List<String> args) throws K8sFrameworkException {
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
        
        T runnable = podModel.toK8sRunnable(runId, T.builder());
    
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
            .name(containerName)
            .image(runnable.getImage())
            .imagePullPolicy(imagePullPolicy)
            .command(command)
            .args(args)
            .resources(resources)
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

        return podSpec;
    }

}
