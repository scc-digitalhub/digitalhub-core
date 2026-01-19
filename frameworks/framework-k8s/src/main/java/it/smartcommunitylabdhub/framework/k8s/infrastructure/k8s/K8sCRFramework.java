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
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.exceptions.FrameworkException;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@FrameworkComponent(framework = K8sCRFramework.FRAMEWORK)
public class K8sCRFramework extends K8sBaseFramework<K8sCRRunnable, DynamicKubernetesObject> {

    public static final String FRAMEWORK = "k8scr";

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    public K8sCRFramework(ApiClient apiClient) {
        super(apiClient);
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

        // Create labels for CR
        Map<String, String> labels = buildLabels(runnable);

        Map<String, Serializable> spec = k8sCRHelper.create(runnable.getCustomResource(), labels, null);
        results.put(runnable.getCustomResource().getKind(), spec);

        //update state
        runnable.setState(K8sRunnableState.PENDING.name());

        runnable.setResults(
            results.entrySet().stream().collect(Collectors.toMap(Entry::getKey, e -> mapper.convertValue(e, typeRef)))
        );

        if (runnable.getCustomResource() != null) {
            runnable.setMessage(String.format("CR %s %s created", runnable.getCustomResource().getKind(), runnable.getCustomResource().getName()));
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

        try {
            k8sCRHelper.delete(runnable.getCustomResource());
            messages.add(String.format("CR %s deleted", runnable.getCustomResource().getName()));
        } catch (K8sFrameworkException | IllegalArgumentException e) {
            runnable.setState(K8sRunnableState.DELETED.name());
            return runnable;
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

    public DynamicKubernetesObject get(K8sCRRunnable runnable) throws K8sFrameworkException {
        return k8sCRHelper.get(runnable.getCustomResource());
    }


    @Override
    public DynamicKubernetesObject build(K8sCRRunnable runnable) {
        // Create labels for job
        Map<String, String> labels = buildLabels(runnable);
        return k8sCRHelper.build(runnable.getCustomResource(), labels);
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
