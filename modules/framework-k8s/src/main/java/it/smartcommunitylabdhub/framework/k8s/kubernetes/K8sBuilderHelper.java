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
import io.kubernetes.client.openapi.models.V1EphemeralVolumeSource;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimTemplate;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource;
import io.kubernetes.client.openapi.models.V1SecretKeySelector;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1VolumeResourceRequirements;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResourceDefinition;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResources;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume.VolumeType;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Value("${kubernetes.resources.gpu.key}")
    String gpuResourceKey;

    private String emptyDirDefaultSize = "128Mi";
    private String emptyDirDefaultMedium = null;

    protected CoreResourceDefinition ephemeralResourceDefinition = new CoreResourceDefinition();
    protected String ephemeralStorageClass;

    public void setEphemeralResourceDefinition(CoreResourceDefinition ephemeralResourceDefinition) {
        this.ephemeralResourceDefinition = ephemeralResourceDefinition;
    }

    //set ephemeral resource definition for volumes matching pvc
    //we want to manage pvc and ephemeral volumes as "user volumes" under the same limits
    @Autowired
    public void setEphemeralRequestsResourceDefinition(
        @Value("${kubernetes.resources.pvc.requests}") String pvcResourceDefinition
    ) {
        if (StringUtils.hasText(pvcResourceDefinition)) {
            this.ephemeralResourceDefinition.setValue(pvcResourceDefinition);
        }
    }

    @Autowired
    public void setEphemeralStorageClass(
        @Value("${kubernetes.resources.ephemeral.storage-class}") String ephemeralStorageClass
    ) {
        if (StringUtils.hasText(ephemeralStorageClass)) {
            this.ephemeralStorageClass = ephemeralStorageClass;
        }
    }

    @Autowired(required = false)
    public void setEmptyDirDefaultMedium(@Value("${kubernetes.empty-dir.medium}") String emptyDirDefaultMedium) {
        this.emptyDirDefaultMedium = emptyDirDefaultMedium;
    }

    @Autowired
    public void setEmptyDirDefaultSize(@Value("${kubernetes.empty-dir.size}") String emptyDirDefaultSize) {
        if (StringUtils.hasText(emptyDirDefaultSize)) {
            //ensure we have a valid size
            Quantity.fromString(emptyDirDefaultSize);
            this.emptyDirDefaultSize = emptyDirDefaultSize;
        } else {
            log.warn("EmptyDir default size not set, using default 128Mi");
        }
    }

    public K8sBuilderHelper(ApiClient client) {
        api = new CoreV1Api(client);
    }

    @Override
    public void afterPropertiesSet() {
        Assert.hasText(coreEndpoint, "core endpoint must be set");
    }

    public List<V1EnvVar> getV1EnvVar() {
        List<V1EnvVar> vars = new ArrayList<>();

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

        //always inject core endpoint + namespace when missing
        if (vars.stream().noneMatch(v -> v.getName().equals("DHCORE_ENDPOINT"))) {
            vars.add(new V1EnvVar().name("DHCORE_ENDPOINT").value(coreEndpoint));
        }
        if (vars.stream().noneMatch(v -> v.getName().equals("DHCORE_NAMESPACE"))) {
            vars.add(new V1EnvVar().name("DHCORE_NAMESPACE").value(namespace));
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
        VolumeType type = coreVolume.getVolumeType();
        Map<String, String> spec = Optional.ofNullable(coreVolume.getSpec()).orElse(Collections.emptyMap());
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
            case VolumeType.shared_volume:
                return volume.persistentVolumeClaim(
                    new V1PersistentVolumeClaimVolumeSource()
                        .claimName(spec.getOrDefault("claimName", coreVolume.getName()))
                );
            case VolumeType.persistent_volume_claim:
                return volume.persistentVolumeClaim(
                    new V1PersistentVolumeClaimVolumeSource().claimName(getVolumeName(id, coreVolume.getName()))
                    // .claimName(spec.getOrDefault("claimName", coreVolume.getName()))
                );
            case VolumeType.ephemeral:
                //build claim
                Quantity quantity = Quantity.fromString(
                    spec.getOrDefault("size", ephemeralResourceDefinition.getValue())
                );
                V1VolumeResourceRequirements req = new V1VolumeResourceRequirements()
                    .requests(Map.of("storage", quantity));

                //enforce limit
                //TODO check if valid!
                // if (ephemeralResourceDefinition.getLimits() != null) {
                //     Quantity limit = Quantity.fromString(ephemeralResourceDefinition.getLimits());
                //     req.setLimits(Map.of("storage", limit));
                // }

                return volume.ephemeral(
                    new V1EphemeralVolumeSource()
                        .volumeClaimTemplate(
                            new V1PersistentVolumeClaimTemplate()
                                .metadata(new V1ObjectMeta().name(getVolumeName(id, coreVolume.getName())))
                                .spec(
                                    new V1PersistentVolumeClaimSpec()
                                        .accessModes(Collections.singletonList("ReadWriteOnce"))
                                        .volumeMode("Filesystem")
                                        .storageClassName(spec.getOrDefault("storage_class", ephemeralStorageClass))
                                        .resources(req)
                                )
                        )
                );
            case VolumeType.empty_dir:
                return volume.emptyDir(
                    new V1EmptyDirVolumeSource()
                        .medium(emptyDirDefaultMedium) //configured by admin only!
                        .sizeLimit(Quantity.fromString(spec.getOrDefault("size_limit", emptyDirDefaultSize)))
                );
            default:
                return null;
        }
    }

    public V1VolumeMount getVolumeMount(@NotNull CoreVolume coreVolume) {
        return new V1VolumeMount().name(coreVolume.getName()).mountPath(coreVolume.getMountPath());
    }

    public CoreResources convertResources(CoreResource resource) {
        if (resource == null) {
            return null;
        }

        CoreResources resources = new CoreResources();
        List<CoreResourceDefinition> definitions = new ArrayList<>();
        if (resource != null) {
            if (resource.getCpu() != null) {
                definitions.add(new CoreResourceDefinition("cpu", resource.getCpu()));
            }
            if (resource.getMem() != null) {
                definitions.add(new CoreResourceDefinition("memory", resource.getMem()));
            }
            if (resource.getGpu() != null) {
                definitions.add(new CoreResourceDefinition(gpuResourceKey, resource.getGpu()));
            }
        }
        resources.setRequests(definitions);
        return resources;
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
            String value = name.toLowerCase().replaceAll("[^a-z0-9-]+", "");
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
