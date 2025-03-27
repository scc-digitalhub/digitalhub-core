package it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.Assert;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.exceptions.FrameworkException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FrameworkComponent(framework = K8sCRFramework.FRAMEWORK)
public class K8sCRFramework extends K8sBaseFramework<K8sCRRunnable, DynamicKubernetesObject> {

    public static final String FRAMEWORK = "k8scr";

    private final ApiClient apiClient;

    
    public K8sCRFramework(ApiClient apiClient) {
        super(apiClient);
        this.apiClient = apiClient;
    }

    @Override
    public K8sCRRunnable run(K8sCRRunnable runnable) throws FrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        Map<String, KubernetesObject> results = new HashMap<>();
        DynamicKubernetesObject cr = build(runnable);

        log.info("create CR for {}", String.valueOf(cr.getMetadata().getName()));
        cr = create(cr, getDynamicKubernetesApi(runnable));
        results.put(cr.getKind(), cr);
        //update state
        runnable.setState(State.RUNNING.name());

        if (cr != null) {
            runnable.setMessage(String.format("CR %s %s created", cr.getKind(), cr.getMetadata().getName()));
        }

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;

    }
     
    public DynamicKubernetesApi getDynamicKubernetesApi(K8sCRRunnable runnable) {
        return new DynamicKubernetesApi(runnable.getApiGroup(), runnable.getApiVersion(), runnable.getPlural(), apiClient);
    }
        
    @Override
    public K8sCRRunnable delete(K8sCRRunnable runnable) throws FrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        List<String> messages = new ArrayList<>();

        DynamicKubernetesApi dynamicApi = getDynamicKubernetesApi(runnable);
        DynamicKubernetesObject cr;
        try {
            cr = get(build(runnable), dynamicApi);
        } catch (K8sFrameworkException e) {
            runnable.setState(State.DELETED.name());
            return runnable;
        }

        log.info("delete CR for {}", String.valueOf(cr.getMetadata().getName()));
        delete(cr, dynamicApi);
        messages.add(String.format("CR %s deleted", cr.getMetadata().getName()));

        //update state
        runnable.setState(State.DELETED.name());
        runnable.setMessage(String.join(", ", messages));

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;    
    }

    public DynamicKubernetesObject get(@NotNull DynamicKubernetesObject cr, DynamicKubernetesApi dynamicApi) throws K8sFrameworkException {
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

    public DynamicKubernetesObject build(K8sCRRunnable runnable) {
        DynamicKubernetesObject obj = new DynamicKubernetesObject();

        // TODO
        String crName = k8sBuilderHelper.getCRName(
            runnable.getRuntime(),
            runnable.getTask(),
            runnable.getId()
        );

        String apiVersion = runnable.getApiGroup() + "/" + runnable.getApiVersion();
        obj.setApiVersion(apiVersion);
        obj.setKind(runnable.getKind());

        // Create labels for job
        Map<String, String> labels = buildLabels(runnable);

        // Create the Deployment metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(crName).labels(labels).namespace(namespace);
        obj.setMetadata(metadata);

        obj.getRaw().add("spec", convertSpec(runnable.getSpec()));
        return obj;
    }

    private JsonElement convertSpec(Map<String, Serializable> spec) {
        Gson gson = new Gson();
        String json = gson.toJson(spec);
        JsonObject jsonObject = JsonParser.parseString(json).getAsJsonObject();
        return jsonObject;
    }
    
    private DynamicKubernetesObject create(DynamicKubernetesObject cr, DynamicKubernetesApi dynamicApi) throws K8sFrameworkException {
        Assert.notNull(cr.getMetadata(), "metadata can not be null");
        try {
            String crName = cr.getMetadata().getName();
            log.debug("create CR for {}", crName);

            return dynamicApi.create(
                namespace,
                cr,
                null
            ).getObject();
            // .createNamespacedDeployment(namespace, deployment, null, null, null, null);
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

            dynamicApi.delete(namespace, crName);
        } catch (Exception e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getMessage());
            }
            throw new K8sFrameworkException(e.getMessage(), e.getMessage());
        }
    }

}
