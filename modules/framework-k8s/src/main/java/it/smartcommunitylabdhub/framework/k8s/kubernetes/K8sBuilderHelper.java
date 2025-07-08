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

package it.smartcommunitylabdhub.framework.k8s.kubernetes;

import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapKeySelector;
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1EnvVarSource;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Helper class for building Kubernetes  environment variables.
 * This class provides methods to retrieve environment variables with fallback values
 * and constructs a list of V1EnvVar objects for use in Kubernetes  specifications.
 */
@Slf4j
@Component
@ConditionalOnKubernetes
public class K8sBuilderHelper implements InitializingBean {

    public static final int K8S_NAME_MAX_LENGTH = 62;
    public static final int TAG_LENGTH = 5;

    private final CoreV1Api api;

    @Autowired
    ApplicationProperties applicationProperties;

    @Value("${application.endpoint}")
    private String coreEndpoint;

    @Value("${kubernetes.config.config-map}")
    private List<String> sharedConfigMaps;

    @Value("${kubernetes.namespace}")
    private String namespace;

    public K8sBuilderHelper(ApiClient client) {
        api = new CoreV1Api(client);
    }

    @Override
    public void afterPropertiesSet() {
        Assert.hasText(coreEndpoint, "core endpoint must be set");
    }

    public List<V1EnvVar> getV1EnvVar() {
        List<V1EnvVar> vars = new ArrayList<>();

        //always inject the core endpoint + namespace
        vars.add(new V1EnvVar().name("DHCORE_ENDPOINT").value(coreEndpoint));
        vars.add(new V1EnvVar().name("DHCORE_NAMESPACE").value(namespace));

        // add shared config maps
        if (sharedConfigMaps != null) {
            sharedConfigMaps
                .stream()
                .forEach(c -> {
                    try {
                        V1ConfigMap configMap = api.readNamespacedConfigMap(c, namespace, "");
                        if (configMap != null && configMap.getData() != null) {
                            configMap
                                .getData()
                                .forEach((key, v) ->
                                    vars.add(
                                        //add as reference
                                        new V1EnvVar()
                                            .name(key)
                                            .valueFrom(
                                                new V1EnvVarSource()
                                                    .configMapKeyRef(new V1ConfigMapKeySelector().name(c).key(key))
                                            )
                                    )
                                );
                        }
                    } catch (ApiException e) {
                        //catch and skip this container's logs
                        log.error("Error with k8s: {}", e.getMessage());
                        if (log.isTraceEnabled()) {
                            log.trace("k8s api response: {}", e.getResponseBody());
                        }
                    }
                });
        }

        return vars;
    }

    // public List<V1EnvFromSource> getV1EnvFromSource() {
    //     // Get Env var from secret and config map
    //     return Stream
    //         .concat(
    //             sharedConfigMaps
    //                 .stream()
    //                 .map(value -> new V1EnvFromSource().configMapRef(new V1ConfigMapEnvSource().name(value))),
    //             sharedSecrets
    //                 .stream()
    //                 //.filter(secret -> !secret.equals("")) // skip postgres
    //                 .map(secret -> new V1EnvFromSource().secretRef(new V1SecretEnvSource().name(secret)))
    //         )
    //         .toList();
    // }

    public List<V1EnvVar> getEnvVarsFromSecrets(Map<String, Set<String>> secrets) {
        if (secrets != null) {
            return secrets
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null)
                .flatMap(entry ->
                    entry
                        .getValue()
                        .stream()
                        .map(key ->
                            new V1EnvVar()
                                .name(key)
                                .valueFrom(
                                    new V1EnvVarSource()
                                        .secretKeyRef(new V1SecretKeySelector().name(entry.getKey()).key(key))
                                )
                        )
                )
                .toList();
        }
        return Collections.emptyList();
    }

    public V1Volume getVolume(String id, @NotNull CoreVolume coreVolume) {
        V1Volume volume = new V1Volume().name(coreVolume.getName());
        String type = coreVolume.getVolumeType().name();
        Map<String, String> spec = coreVolume.getSpec();
        switch (type) {
            // TODO: support items
            //DISABLED: users should not be able to mount arbitrary config maps
            // case "config_map":
            //     return volume.configMap(
            //         new V1ConfigMapVolumeSource().name(spec.getOrDefault("name", coreVolume.getName()))
            //     );
            //DISABLED: users should not be able to mount arbitrary secrets
            // case "secret":
            //     CoreItems coreItems = JacksonMapper.OBJECT_MAPPER.convertValue(
            //         spec.getOrDefault("items", new HashMap<>()),
            //         CoreItems.class
            //     );
            //     return volume.secret(
            //         new V1SecretVolumeSource()
            //             .secretName((String) spec.getOrDefault("secret_name", coreVolume.getName()))
            //             .items(
            //                 coreItems
            //                     .getCoreItems()
            //                     .stream()
            //                     .flatMap(map -> map.entrySet().stream())
            //                     .map(entry -> new V1KeyToPath().key(entry.getKey()).path((String) entry.getValue()))
            //                     .toList()
            //             )
            //     );
            case "persistent_volume_claim":
                return volume.persistentVolumeClaim(
                    new V1PersistentVolumeClaimVolumeSource().claimName(getVolumeName(id, coreVolume.getName()))
                    // .claimName(spec.getOrDefault("claimName", coreVolume.getName()))
                );
            case "empty_dir":
                return volume.emptyDir(
                    new V1EmptyDirVolumeSource()
                        .medium(spec.getOrDefault("medium", null))
                        .sizeLimit(Quantity.fromString(spec.getOrDefault("size_limit", "128Mi")))
                );
            default:
                return null;
        }
    }

    public V1VolumeMount getVolumeMount(@NotNull CoreVolume coreVolume) {
        return new V1VolumeMount().name(coreVolume.getName()).mountPath(coreVolume.getMountPath());
    }

    public Map<String, Quantity> convertResources(Map<String, String> map) {
        return map
            .entrySet()
            .stream()
            .map(entry -> Map.entry(entry.getKey(), Quantity.fromString(entry.getValue())))
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue));
    }

    /*
     * Helpers
     */
    //TODO align names!
    // Generate and return container name
    public String getContainerName(String runtime, String task, String id) {
        return sanitizeNames("c" + "-" + task + "-" + id);
    }

    // Generate and return job name
    public String getJobName(String runtime, String task, String id) {
        return sanitizeNames("j" + "-" + task + "-" + id);
    }

    // Generate and return deployment name
    public String getDeploymentName(String runtime, String task, String id) {
        return sanitizeNames("d" + "-" + task + "-" + id);
    }

    public String getCRName(String name, String id) {
        return sanitizeNames(name + "-" + id);
    }

    // Generate and return service name
    public String getServiceName(String runtime, String task, String id) {
        return sanitizeNames("s" + "-" + task + "-" + id);
    }

    // Generate and return volume name
    public String getVolumeName(String id, String name) {
        return sanitizeNames("v" + "-" + id + "-" + name);
    }

    public String getImageName(String image, String id) {
        String tag = "latest";
        if (StringUtils.hasText(id)) {
            tag = sanitizeNames(id.substring(0, Math.min(TAG_LENGTH, id.length())));
        }

        return image + ":" + tag;
    }

    public String getLabelName(String name) {
        return sanitizeNames(applicationProperties.getName()) + "/" + sanitizeNames(name);
    }

    public static String sanitizeNames(String name) {
        //sanitize value
        if (name == null) {
            return null;
        } else {
            //use only allowed chars in k8s resource names!
            String value = name.toLowerCase().replaceAll("[^a-zA-Z0-9._-]+", "");
            if (value.length() > K8S_NAME_MAX_LENGTH) {
                log.error("Name exceeds max length: {} ({})", String.valueOf(value.length()), value);

                throw new IllegalArgumentException(
                    "Name exceeds max length: " + String.valueOf(value.length()) + "(" + value + ")"
                );
                //DISABLED: multiple sanitizations should return the *same* value
                // return (
                //     value.substring(0, K8S_NAME_MAX_LENGTH - 7) +
                //     "-" +
                //     RandomStringUtils.randomAlphabetic(5).toLowerCase()
                // );
            }

            return value;
        }
    }
}
