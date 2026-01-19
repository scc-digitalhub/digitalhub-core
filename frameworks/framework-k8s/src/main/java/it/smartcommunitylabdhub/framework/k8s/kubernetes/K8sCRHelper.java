package it.smartcommunitylabdhub.framework.k8s.kubernetes;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1OwnerReference;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.CreateOptions;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;

import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import jakarta.validation.constraints.NotNull;
import it.smartcommunitylabdhub.framework.k8s.objects.CustomResource;
@Slf4j
@Component
@ConditionalOnKubernetes 
public class K8sCRHelper implements InitializingBean {

    // whitelist of allowed api groups
    private Set<String> apiGroups = Collections.unmodifiableSet(Collections.emptySet());

    // K8s api client
    private ApiClient apiClient;

    // K8s namespace
    private String namespace;

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    protected static final ObjectMapper mapper = KubernetesMapper.OBJECT_MAPPER;

    @Autowired
    public void setApiGroups(@Value("${kubernetes.crds.api-groups}") String groups) {
        if (groups != null) {
            this.apiGroups = Collections.unmodifiableSet(StringUtils.commaDelimitedListToSet(groups));
        }
    }

    @Autowired
    public void setApiClient(ApiClient apiClient) {
        this.apiClient = apiClient;
    }

    @Autowired
    public void setNamespace(@Value("${kubernetes.namespace}") String namespace) {
        this.namespace = namespace;
    }


    @Override
    public void afterPropertiesSet() {
        Assert.notNull(apiClient, "ApiClient must be provided");
        Assert.notNull(namespace, "K8s namespace must be provided");
    }

    @Deprecated
    private DynamicKubernetesApi getDynamicKubernetesApi(CustomResource resource) {
        return new DynamicKubernetesApi(
            resource.getApiGroup(),
            resource.getApiVersion(),
            resource.getPlural(),
            apiClient
        );
    }

    /**
     * Create a Custom Resource in Kubernetes
     * @param resource spec to create
     * @param labels labels to apply
     * @param owner owner reference, can be null
     * @return the created spec as map
     * @throws K8sFrameworkException
     */
    public Map<String, Serializable> create(CustomResource resource, Map<String, String> labels, KubernetesObject owner) throws K8sFrameworkException {
        DynamicKubernetesObject cr = build(resource, labels);

        //permission check: api should be whitelisted
        if (cr.getApiVersion() == null || !apiGroups.contains(cr.getApiVersion())) {
            throw new IllegalArgumentException(
                "Invalid or unsupported api group or version " + String.valueOf(cr.getApiVersion())
            );
        }

        log.info("create CR for {}", String.valueOf(cr.getMetadata().getName()));
        if (owner != null) {
            cr.getMetadata().setOwnerReferences(
                List.of(createOwnerReference(owner))
            );
        }
        cr = create(cr, getDynamicKubernetesApi(resource));
        try {
            Map<String, Serializable> spec = jsonElementToSpec(cr.getRaw());
            return spec;
        } catch (IOException e) {
            log.error(e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Delete a Custom Resource in Kubernetes
     * @param resource spec to delete
     * @throws K8sFrameworkException
     */
    public void delete(CustomResource resource) throws K8sFrameworkException {
        if (log.isTraceEnabled()) {
            log.trace("custom resource: {}", resource);
        }

        DynamicKubernetesApi dynamicApi = getDynamicKubernetesApi(resource);
        DynamicKubernetesObject cr;
        try {
            cr = get(build(resource, Collections.emptyMap()), dynamicApi);
        } catch (K8sFrameworkException | IllegalArgumentException e) {
            log.error("error reading k8s CR: {}", e.getMessage());
            throw new K8sFrameworkException("error reading k8s CR", e);
        }

        if (cr != null) {
            log.info("delete CR for {}", String.valueOf(cr.getMetadata().getName()));
            delete(cr, dynamicApi);
        }
    }

    /**
     * Build a DynamicKubernetesObject from CustomResource
     * @param resource
     * @param labels
     * @return
     */
    public DynamicKubernetesObject build(CustomResource resource,  Map<String, String> labels) {
        DynamicKubernetesObject obj = new DynamicKubernetesObject();

        // String crName = k8sBuilderHelper.getCRName(runnable.getName(), runnable.getId());
        String crName = K8sBuilderHelper.sanitizeNames(resource.getName());

        String apiVersion = resource.getApiGroup() + "/" + resource.getApiVersion();
        obj.setApiVersion(apiVersion);
        obj.setKind(resource.getKind());

        Map<String, String> allLabels = new HashMap<>();
        if (resource.getLabels() != null) {
            allLabels.putAll(resource.getLabels());
        }
        if (labels != null) {
            allLabels.putAll(labels);
        }

        // Create the Deployment metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(crName).labels(allLabels).annotations(resource.getAnnotations()).namespace(namespace);
        obj.setMetadata(metadata);
        obj.getRaw().add("spec", specToJsonElement(resource.getSpec()));

        return obj;
    } 

    public DynamicKubernetesObject get(CustomResource resource) throws K8sFrameworkException {
        DynamicKubernetesApi dynamicApi = getDynamicKubernetesApi(resource);
        try {
            return get(build(resource, Collections.emptyMap()), dynamicApi);
        } catch (K8sFrameworkException | IllegalArgumentException e) {
            log.error("error reading k8s CR: {}", e.getMessage());
            throw new K8sFrameworkException("error reading k8s CR", e);
        }
    }

    public static JsonElement specToJsonElement(Map<String, Serializable> spec) {
        Gson gson = new Gson();
        String json = gson.toJson(spec);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        return jsonObject;
    }

    public static HashMap<String, Serializable> jsonElementToSpec(JsonElement jsonElement) throws IOException {
        Gson gson = new Gson();
        String json = gson.toJson(jsonElement);
        return mapper.readValue(json, typeRef);
    }

    private DynamicKubernetesObject create(DynamicKubernetesObject cr, DynamicKubernetesApi dynamicApi)
        throws K8sFrameworkException {
        Assert.notNull(cr.getMetadata(), "metadata can not be null");
        try {
            String crName = cr.getMetadata().getName();
            log.debug("create CR for {}", crName);

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
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getMessage());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getMessage());
        }
    }

    private void delete(DynamicKubernetesObject cr, DynamicKubernetesApi dynamicApi) throws K8sFrameworkException {
        Assert.notNull(cr.getMetadata(), "metadata can not be null");
        try {
            String crName = cr.getMetadata().getName();
            log.debug("delete CR for {}", crName);

            //delete with foreground propagation
            DeleteOptions options = new DeleteOptions();
            options.setPropagationPolicy("Foreground");

            dynamicApi.delete(namespace, crName, options);
        } catch (Exception e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getMessage());
            }
            throw new K8sFrameworkException(e.getMessage(), e.getMessage());
        }
    }

    //TODO replace return object, contains a GSON JsonObject which is *not* serializable by Jackson
    @Deprecated
    private DynamicKubernetesObject get(@NotNull DynamicKubernetesObject cr, DynamicKubernetesApi dynamicApi) throws K8sFrameworkException {
        Assert.notNull(cr.getMetadata(), "metadata can not be null");

        try {
            String crName = cr.getMetadata().getName();
            log.debug("get CR for {}", crName);

            return dynamicApi.get(namespace, crName, null).getObject();
        } catch (Exception e) {
            log.info("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getMessage());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getMessage());
        }
    }

    private V1OwnerReference createOwnerReference(KubernetesObject owner) {
        V1OwnerReference ownerReference = new V1OwnerReference();
        ownerReference.setApiVersion(owner.getApiVersion());
        ownerReference.setKind(owner.getKind());
        ownerReference.setName(owner.getMetadata().getName());
        ownerReference.setUid(owner.getMetadata().getUid());
        return ownerReference;
    }

}
