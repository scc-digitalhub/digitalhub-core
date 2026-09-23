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

package it.smartcommunitylabdhub.framework.k8s.controller;

import io.kubernetes.client.custom.ContainerMetrics;
import io.kubernetes.client.openapi.models.V1Pod;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResources;
import it.smartcommunitylabdhub.framework.k8s.provider.K8sConfigProvider;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.k8s.service.K8sMetricsService;
import it.smartcommunitylabdhub.metrics.ResourceMetrics;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnKubernetes
public class ProfilesEndpoint implements InitializingBean {

    @Autowired
    private K8sConfigProvider k8sConfigProvider;

    @Autowired(required = false)
    private K8sMetricsService k8sMetricsService;

    @Value("${jwt.cache-control}")
    private String cacheControl;

    //cache, we don't expect config to be mutable!
    private List<K8sProfile> config = new ArrayList<>();

    @GetMapping(value = { "/.well-known/k8s-profiles" })
    public ResponseEntity<List<K8sProfile>> getConfiguration() {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, cacheControl).body(config);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Optional.ofNullable(k8sConfigProvider.getTemplates()).ifPresent(templates -> {
            config.clear();
            templates.forEach(template -> config.add(K8sProfile.fromTemplate(template)));
        });
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping(value = { "/api/v1/k8s-profiles/{id}/usage" })
    public ResourceMetrics getUsage(@PathVariable String id) throws SystemException {
        if (k8sMetricsService == null) {
            return null;
        }

        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("id must not be empty");
        }

        try {
            //fetch now from service
            //NOTE: profiles are translated from templates!
            List<V1Pod> pods = k8sMetricsService.listPods("template", id);

            Map<String, List<ResourceMetrics.Metric>> metricsMap = new HashMap<>();
            if (pods != null) {
                //we merely count pods here
                ResourceMetrics.Metric metric = new ResourceMetrics.Metric(
                    System.currentTimeMillis(),
                    (double) pods.size()
                );
                metricsMap.put("pods", List.of(metric));
            }

            //build
            List<ResourceMetrics.Metrics> metricsList = metricsMap
                .entrySet()
                .stream()
                .map(e ->
                    new ResourceMetrics.Metrics(
                        e.getKey(),
                        K8sMetricsService.deriveUnit(e.getKey()),
                        e.getValue(),
                        K8sMetricsService.summarize(e.getValue()),
                        null
                    )
                )
                .toList();

            return ResourceMetrics.builder().id("u_profile-" + id).metrics(metricsList).build();
        } catch (StoreException e) {
            throw new SystemException("Error fetching metrics for profile " + id, e);
        }
    }

    @PreAuthorize("hasAuthority('ROLE_USER')")
    @GetMapping(value = { "/api/v1/k8s-profiles/{id}/metrics" })
    public ResourceMetrics getResourceMetrics(@PathVariable String id) throws SystemException {
        if (k8sMetricsService == null) {
            return null;
        }

        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("id must not be empty");
        }

        try {
            //fetch now from service
            //NOTE: profiles are translated from templates!
            ContainerMetrics metrics = k8sMetricsService.getContainerMetrics("template", id);

            Map<String, List<ResourceMetrics.Metric>> metricsMap = new HashMap<>();
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

            //build
            List<ResourceMetrics.Metrics> metricsList = metricsMap
                .entrySet()
                .stream()
                .map(e ->
                    new ResourceMetrics.Metrics(
                        e.getKey(),
                        K8sMetricsService.deriveUnit(e.getKey()),
                        e.getValue(),
                        K8sMetricsService.summarize(e.getValue()),
                        null
                    )
                )
                .toList();

            return ResourceMetrics.builder().id("m_profile-" + id).metrics(metricsList).build();
        } catch (StoreException e) {
            throw new SystemException("Error fetching metrics for profile " + id, e);
        }
    }

    @AllArgsConstructor
    @NoArgsConstructor
    @Setter
    @Getter
    private static class K8sProfile {

        private String id;
        private String name;
        private String description;

        private CoreResources resources;

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((id == null) ? 0 : id.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            K8sProfile other = (K8sProfile) obj;
            if (id == null) {
                if (other.id != null) return false;
            } else if (!id.equals(other.id)) return false;
            return true;
        }

        public static K8sProfile fromTemplate(K8sTemplate<K8sRunnable> template) {
            K8sProfile p = new K8sProfile(
                template.getId(),
                template.getName(),
                template.getDescription(),
                new CoreResources()
            );
            if (template.getProfile() != null && template.getProfile().getResources() != null) {
                p.setResources(template.getProfile().getResources());
            }
            return p;
        }
    }
}
