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

package it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.generic.KubernetesApiResponse;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import io.kubernetes.client.util.generic.options.CreateOptions;
import io.kubernetes.client.util.generic.options.DeleteOptions;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.exceptions.FrameworkException;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@FrameworkComponent(framework = K8sCRFramework.FRAMEWORK)
public class K8sCRFramework extends K8sBaseFramework<K8sCRRunnable, DynamicKubernetesObject> {

    public static final String FRAMEWORK = "k8scr";

    private Set<String> apiGroups = Collections.unmodifiableSet(Collections.emptySet());

    private final ApiClient apiClient;

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    public K8sCRFramework(ApiClient apiClient) {
        super(apiClient);
        this.apiClient = apiClient;
    }

    @Autowired
    public void setApiGroups(@Value("${kubernetes.crds.api-groups}") String groups) {
        if (groups != null) {
            this.apiGroups = Collections.unmodifiableSet(StringUtils.commaDelimitedListToSet(groups));
        }
    }

    @Override
    public K8sCRRunnable run(K8sCRRunnable runnable) throws FrameworkException {
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

        DynamicKubernetesObject cr = build(runnable);

        //permission check: api should be whitelisted
        if (cr.getApiVersion() == null || !apiGroups.contains(cr.getApiVersion())) {
            throw new IllegalArgumentException(
                "Invalid or unsupported api group or version " + String.valueOf(cr.getApiVersion())
            );
        }

        log.info("create CR for {}", String.valueOf(cr.getMetadata().getName()));
        cr = create(cr, getDynamicKubernetesApi(runnable));
        try {
            Map<String, Serializable> spec = jsonElementToSpec(cr.getRaw());
            results.put(cr.getKind(), spec);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        //update state
        runnable.setState(K8sRunnableState.PENDING.name());

        runnable.setResults(
            results.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> mapper.convertValue(e, typeRef)))
        );

        if (cr != null) {
            runnable.setMessage(String.format("CR %s %s created", cr.getKind(), cr.getMetadata().getName()));
        }

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    protected V1Secret buildRunSecret(K8sCRRunnable runnable) {
        // check if CR-specific secret is required and if so, create it
        if (Boolean.TRUE.equals(runnable.getRequiresSecret())) {
            return super.buildRunSecret(runnable);
        }
        return null;
    }

    @Deprecated
    public DynamicKubernetesApi getDynamicKubernetesApi(K8sCRRunnable runnable) {
        return new DynamicKubernetesApi(
            runnable.getApiGroup(),
            runnable.getApiVersion(),
            runnable.getPlural(),
            apiClient
        );
    }

    @Override
    public K8sCRRunnable stop(K8sCRRunnable runnable) throws K8sFrameworkException {
        log.info("stop for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        //stop by deleting
        runnable = delete(runnable);

        //update state
        runnable.setState(K8sRunnableState.STOPPED.name());

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sCRRunnable delete(K8sCRRunnable runnable) throws K8sFrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        List<String> messages = new ArrayList<>();

        DynamicKubernetesApi dynamicApi = getDynamicKubernetesApi(runnable);
        DynamicKubernetesObject cr;
        try {
            cr = get(build(runnable), dynamicApi);
        } catch (K8sFrameworkException | IllegalArgumentException e) {
            runnable.setState(K8sRunnableState.DELETED.name());
            return runnable;
        }

        if (cr != null) {
            log.info("delete CR for {}", String.valueOf(cr.getMetadata().getName()));
            delete(cr, dynamicApi);
            messages.add(String.format("CR %s deleted", cr.getMetadata().getName()));
        }

        //secrets
        cleanRunSecret(runnable);

        try {
            runnable.setResults(Collections.emptyMap());
        } catch (IllegalArgumentException e) {
            log.error("error reading k8s results: {}", e.getMessage());
        }

        //update state
        runnable.setState(K8sRunnableState.DELETED.name());
        runnable.setMessage(String.join(", ", messages));

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    protected void cleanRunSecret(K8sCRRunnable runnable) {
        // check if CR-specific secret is required and if so, delete it
        if (Boolean.TRUE.equals(runnable.getRequiresSecret())) {
            super.cleanRunSecret(runnable);
        }
    }

    //TODO replace return object, contains a GSON JsonObject which is *not* serializable by Jackson
    @Deprecated
    public DynamicKubernetesObject get(@NotNull DynamicKubernetesObject cr, DynamicKubernetesApi dynamicApi)
        throws K8sFrameworkException {
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

        // String crName = k8sBuilderHelper.getCRName(runnable.getName(), runnable.getId());
        String crName = StringUtils.hasText(runnable.getName())
            ? K8sBuilderHelper.sanitizeNames(runnable.getName())
            : runnable.getId();

        String apiVersion = runnable.getApiGroup() + "/" + runnable.getApiVersion();
        obj.setApiVersion(apiVersion);
        obj.setKind(runnable.getKind());

        // Create labels for job
        Map<String, String> labels = buildLabels(runnable);

        // Create the Deployment metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(crName).labels(labels).namespace(namespace);
        obj.setMetadata(metadata);
        obj.getRaw().add("spec", specToJsonElement(runnable.getSpec()));

        return obj;
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

    @Override
    public DynamicKubernetesObject get(DynamicKubernetesObject obj) throws K8sFrameworkException {
        //not updatable without api.
        return obj;
    }

    @Override
    public List<V1Pod> pods(DynamicKubernetesObject object) throws K8sFrameworkException {
        if (object == null || object.getMetadata() == null) {
            return null;
        }

        //try super first
        List<V1Pod> items = super.pods(object);
        if (items != null && !items.isEmpty()) {
            return items;
        }

        //build labels to select pods
        //we expect a label matching the api kind = resource name
        //TODO make this extensible
        String name = object.getMetadata().getName();
        String label = object.getKind().toLowerCase();
        String labelSelector = label + "=" + name;
        try {
            log.debug("load pods for {}", labelSelector);
            V1PodList pods = coreV1Api.listNamespacedPod(
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
            );

            return pods.getItems();
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }
}
