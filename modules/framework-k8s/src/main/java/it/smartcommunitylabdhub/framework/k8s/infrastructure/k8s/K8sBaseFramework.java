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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.Metrics;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.PodMetrics;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Affinity;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LocalObjectReference;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimSpec;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1PodSecurityContext;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretEnvSource;
import io.kubernetes.client.openapi.models.V1SecurityContext;
import io.kubernetes.client.openapi.models.V1Toleration;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import io.kubernetes.client.openapi.models.V1VolumeResourceRequirements;
import it.smartcommunitylabdhub.commons.config.ApplicationProperties;
import it.smartcommunitylabdhub.commons.infrastructure.Framework;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.config.KubernetesProperties;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.jackson.KubernetesMapper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sLabelHelper;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sSecretHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLog;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreMetric;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreNodeSelector;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResourceDefinition;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class K8sBaseFramework<T extends K8sRunnable, K extends KubernetesObject>
    implements Framework<T>, InitializingBean {

    //custom object mapper with mixIn for IntOrString
    protected static final ObjectMapper mapper = KubernetesMapper.OBJECT_MAPPER;

    protected final CoreV1Api coreV1Api;
    protected final Metrics metricsApi;

    protected ApplicationProperties applicationProperties;
    protected ResourceLoader resourceLoader;

    protected KubernetesProperties k8sProperties;

    //TODO move all props to bean
    protected String namespace;
    protected String registrySecret;

    //default  value
    protected String defaultImagePullPolicy = "IfNotPresent";

    protected boolean disableRoot = false;

    protected String gpuResourceKey;
    protected CoreResourceDefinition cpuResourceDefinition = new CoreResourceDefinition();
    protected CoreResourceDefinition memResourceDefinition = new CoreResourceDefinition();
    protected CoreResourceDefinition pvcResourceDefinition = new CoreResourceDefinition();
    protected String pvcStorageClass;

    protected List<String> templateKeys = Collections.emptyList();

    protected Map<String, K8sTemplate<T>> templates = Collections.emptyMap();

    protected Boolean collectLogs;
    protected Boolean collectMetrics;
    protected String collectResults = "default";

    protected String version;
    protected K8sBuilderHelper k8sBuilderHelper;
    protected K8sSecretHelper k8sSecretHelper;
    protected K8sLabelHelper k8sLabelHelper;

    protected K8sBaseFramework(ApiClient apiClient) {
        Assert.notNull(apiClient, "k8s api client is required");
        coreV1Api = new CoreV1Api(apiClient);
        metricsApi = new Metrics(apiClient);
    }

    @Autowired
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Autowired
    public void setApplicationProperties(ApplicationProperties applicationProperties) {
        this.applicationProperties = applicationProperties;
    }

    @Autowired
    public void setK8sProperties(KubernetesProperties k8sProperties) {
        this.k8sProperties = k8sProperties;
    }

    @Autowired
    public void setCollectLogs(@Value("${kubernetes.logs}") Boolean collectLogs) {
        this.collectLogs = collectLogs;
    }

    @Autowired
    public void setCollectMetrics(@Value("${kubernetes.metrics}") Boolean collectMetrics) {
        this.collectMetrics = collectMetrics;
    }

    @Autowired
    public void setCollectResults(@Value("${kubernetes.results}") String collectResults) {
        this.collectResults = collectResults;
    }

    public void setCpuResourceDefinition(CoreResourceDefinition cpuResourceDefinition) {
        this.cpuResourceDefinition = cpuResourceDefinition;
    }

    @Autowired
    public void setCpuRequestsResourceDefinition(
        @Value("${kubernetes.resources.cpu.requests}") String cpuResourceDefinition
    ) {
        if (StringUtils.hasText(cpuResourceDefinition)) {
            this.cpuResourceDefinition.setRequests(cpuResourceDefinition);
        }
    }

    @Autowired
    public void setCpuLimitsResourceDefinition(
        @Value("${kubernetes.resources.cpu.limits}") String cpuResourceDefinition
    ) {
        if (StringUtils.hasText(cpuResourceDefinition)) {
            this.cpuResourceDefinition.setLimits(cpuResourceDefinition);
        }
    }

    @Autowired
    public void setDisableRoot(@Value("${kubernetes.security.disable-root}") Boolean disableRoot) {
        if (disableRoot != null) {
            this.disableRoot = disableRoot.booleanValue();
        }
    }

    @Autowired
    public void setImagePullPolicy(@Value("${kubernetes.image-pull-policy}") String imagePullPolicy) {
        this.defaultImagePullPolicy = imagePullPolicy;
    }

    public void setMemResourceDefinition(CoreResourceDefinition memResourceDefinition) {
        this.memResourceDefinition = memResourceDefinition;
    }

    @Autowired
    public void setMemRequestsResourceDefinition(
        @Value("${kubernetes.resources.mem.requests}") String memResourceDefinition
    ) {
        if (StringUtils.hasText(memResourceDefinition)) {
            this.memResourceDefinition.setRequests(memResourceDefinition);
        }
    }

    @Autowired
    public void setMemLimitsResourceDefinition(
        @Value("${kubernetes.resources.mem.limits}") String memResourceDefinition
    ) {
        if (StringUtils.hasText(memResourceDefinition)) {
            this.memResourceDefinition.setLimits(memResourceDefinition);
        }
    }

    public void setPvcResourceDefinition(CoreResourceDefinition pvcResourceDefinition) {
        this.pvcResourceDefinition = pvcResourceDefinition;
    }

    @Autowired
    public void setPvcRequestsResourceDefinition(
        @Value("${kubernetes.resources.pvc.requests}") String pvcResourceDefinition
    ) {
        if (StringUtils.hasText(pvcResourceDefinition)) {
            this.pvcResourceDefinition.setRequests(pvcResourceDefinition);
        }
    }

    @Autowired
    public void setPvcLimitsResourceDefinition(
        @Value("${kubernetes.resources.pvc.limits}") String pvcResourceDefinition
    ) {
        if (StringUtils.hasText(pvcResourceDefinition)) {
            this.pvcResourceDefinition.setLimits(pvcResourceDefinition);
        }
    }

    @Autowired
    public void setPvcStorageClass(@Value("${kubernetes.resources.pvc.storage-class}") String pvcStorageClass) {
        if (StringUtils.hasText(pvcStorageClass)) {
            this.pvcStorageClass = pvcStorageClass;
        }
    }

    @Autowired
    public void setGpuResourceKey(@Value("${kubernetes.resources.gpu.key}") String gpuResourceKey) {
        if (StringUtils.hasText(gpuResourceKey)) {
            this.gpuResourceKey = gpuResourceKey;
        }
    }

    @Autowired
    public void setNamespace(@Value("${kubernetes.namespace}") String namespace) {
        this.namespace = namespace;
    }

    @Autowired
    public void setRegistrySecret(@Value("${registry.secret}") String secret) {
        this.registrySecret = secret;
    }

    @Autowired
    public void setTemplates(@Value("${kubernetes.templates}") List<String> templates) {
        this.templateKeys = templates;
    }

    @Autowired
    public void setVersion(@Value("${application.version}") String version) {
        this.version = version;
    }

    @Autowired
    public void setK8sBuilderHelper(K8sBuilderHelper k8sBuilderHelper) {
        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    @Autowired
    public void setK8sSecretHelper(K8sSecretHelper k8sSecretHelper) {
        this.k8sSecretHelper = k8sSecretHelper;
    }

    @Autowired
    public void setK8sLabelHelper(K8sLabelHelper k8sLabelHelper) {
        this.k8sLabelHelper = k8sLabelHelper;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Assert.notNull(k8sBuilderHelper, "k8s helper is required");
        Assert.notNull(k8sSecretHelper, "k8s secret helper is required");
        Assert.notNull(k8sLabelHelper, "k8s label helper is required");
        Assert.notNull(k8sProperties, "k8s properties required");
        Assert.notNull(namespace, "k8s namespace required");
        Assert.notNull(version, "k8s version required");
    }

    protected Map<String, K8sTemplate<T>> loadTemplates(Class<T> clazz) {
        //load templates if provided
        Map<String, K8sTemplate<T>> results = new HashMap<>();
        if (resourceLoader != null && templateKeys != null) {
            templateKeys.forEach(k -> {
                try {
                    String path = k;
                    //check if we received a bare path and fix
                    if (!path.startsWith("classpath:") && !path.startsWith("file:")) {
                        path = "file:" + k;
                    }

                    // Load as resource and deserialize as template
                    log.debug("Read template from {}", path);
                    Resource res = resourceLoader.getResource(path);
                    K8sTemplate<T> t = KubernetesMapper.readTemplate(
                        res.getContentAsString(StandardCharsets.UTF_8),
                        clazz
                    );

                    if (log.isTraceEnabled()) {
                        log.trace("Template result {}:\n {}", t.getId(), t);
                    }

                    //TODO validate template via smartValidator
                    results.put(t.getId(), t);
                } catch (IOException | ClassCastException | IllegalArgumentException e) {
                    //skip
                    log.error("Error loading templates: " + e.getMessage());
                }
            });
        }

        return results;
    }

    /*
     * Framework methods
     */

    @Override
    public T stop(T runnable) throws K8sFrameworkException {
        if (runnable != null) {
            log.info("destroy objects for runnable {}", runnable.getId());
            //TODO collect or rebuild objects
            // destroy(runnable);

            runnable.setState(State.STOPPED.name());
        }
        return runnable;
    }

    @Override
    public T resume(T runnable) throws K8sFrameworkException {
        //not resumable by default
        return null;
    }

    /*
     * K8s methods
     */

    public List<V1Pod> pods(K object) throws K8sFrameworkException {
        if (object == null || object.getMetadata() == null) {
            return null;
        }

        //fetch labels to select pods
        //assume pods have the same labels as their parent
        //can be overridden downstream
        String label = "app.kubernetes.io/instance";
        String labelValue = Optional.ofNullable(object.getMetadata().getLabels()).map(m -> m.get(label)).orElse(null);
        if (labelValue == null) {
            //no selectors available
            return null;
        }

        String labelSelector = "app.kubernetes.io/instance=" + labelValue;
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

    //TODO support sinceTime when implemented by api
    //https://github.com/kubernetes-client/java/issues/2648
    // public Map<String, String> logs(K object, @Nullable Long sinceTime) throws K8sFrameworkException {

    public List<CoreLog> logs(K object) throws K8sFrameworkException {
        if (object == null || object.getMetadata() == null) {
            return null;
        }

        if (Boolean.TRUE != collectLogs) {
            return Collections.emptyList();
        }

        List<CoreLog> logs = new ArrayList<>();
        List<V1Pod> pods = pods(object);

        for (V1Pod p : pods) {
            if (p.getMetadata() != null && p.getStatus() != null) {
                String pod = p.getMetadata().getName();

                //read init-containers first
                if (p.getStatus().getInitContainerStatuses() != null) {
                    List<String> containers = p
                        .getStatus()
                        .getInitContainerStatuses()
                        .stream()
                        .map(s -> s.getName())
                        .collect(Collectors.toList());
                    for (String c : containers) {
                        try {
                            String log = coreV1Api.readNamespacedPodLog(
                                pod,
                                namespace,
                                c,
                                Boolean.FALSE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                            );

                            logs.add(new CoreLog(pod, log, c, namespace));
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
                    List<String> containers = p
                        .getStatus()
                        .getContainerStatuses()
                        .stream()
                        .map(s -> s.getName())
                        .collect(Collectors.toList());
                    for (String c : containers) {
                        try {
                            String log = coreV1Api.readNamespacedPodLog(
                                pod,
                                namespace,
                                c,
                                Boolean.FALSE,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                            );

                            logs.add(new CoreLog(pod, log, c, namespace));
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

    public List<CoreMetric> metrics(K object) throws K8sFrameworkException {
        if (object == null || object.getMetadata() == null) {
            return null;
        }

        if (Boolean.TRUE != collectMetrics) {
            return Collections.emptyList();
        }

        try {
            List<CoreMetric> metrics = new ArrayList<>();
            List<V1Pod> pods = pods(object);

            List<PodMetrics> podMetrics = metricsApi.getPodMetrics(namespace).getItems();

            for (V1Pod p : pods) {
                if (p.getMetadata() != null && p.getStatus() != null) {
                    String pod = p.getMetadata().getName();

                    PodMetrics metric = podMetrics
                        .stream()
                        .filter(m -> pod.equals(m.getMetadata().getName()))
                        .findFirst()
                        .orElse(null);

                    if (metric != null && metric.getContainers() != null) {
                        metrics.add(
                            new CoreMetric(
                                pod,
                                metric.getContainers(),
                                metric.getTimestamp(),
                                metric.getWindow(),
                                namespace
                            )
                        );
                    }
                }
            }

            return metrics;
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    /*
     * Builder helpers
     * TODO move to a base builder class
     */
    protected Map<String, String> buildLabels(T runnable) {
        // Create base labels
        Map<String, String> baseLabels = k8sLabelHelper.buildBaseLabels(runnable);

        //build template labels when defined
        Map<String, String> templateLabels = new HashMap<>();
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //add template
            K8sRunnable template = templates.get(runnable.getTemplate()).getProfile();
            templateLabels.put(
                K8sBuilderHelper.sanitizeNames(applicationProperties.getName()) + "/template",
                runnable.getTemplate()
            );

            if (template.getLabels() != null && !template.getLabels().isEmpty()) {
                for (CoreLabel l : template.getLabels()) {
                    templateLabels.putIfAbsent(l.name(), K8sBuilderHelper.sanitizeNames(l.value()));
                }
            }
        }

        Map<String, String> labels = MapUtils.mergeMultipleMaps(templateLabels, baseLabels);

        //append user-defined labels with no override
        if (runnable.getLabels() != null && !runnable.getLabels().isEmpty()) {
            labels = new HashMap<>(labels);
            for (CoreLabel l : runnable.getLabels()) {
                labels.putIfAbsent(l.name(), K8sBuilderHelper.sanitizeNames(l.value()));
            }
        }

        return labels;
    }

    @SuppressWarnings("null")
    protected List<V1EnvVar> buildEnv(T runnable) {
        //shared envs
        List<V1EnvVar> sharedEnvs = k8sBuilderHelper.getV1EnvVar();

        //shared secrets
        List<V1EnvVar> secretEnvs = k8sSecretHelper.getV1EnvVar();

        // function specific envs
        // NOTE: we let users override core envs and secrets at their risk
        List<V1EnvVar> functionEnvs = runnable
            .getEnvs()
            .stream()
            .map(env -> new V1EnvVar().name(env.name()).value(env.value()))
            .collect(Collectors.toList());

        //merge all avoiding duplicates
        Map<String, V1EnvVar> envs = new HashMap<>();
        //shared have the priority
        sharedEnvs.forEach(e -> envs.putIfAbsent(e.getName(), e));
        secretEnvs.forEach(e -> envs.putIfAbsent(e.getName(), e));
        functionEnvs.forEach(e -> envs.putIfAbsent(e.getName(), e));

        return envs.values().stream().toList();
    }

    protected List<V1EnvFromSource> buildEnvFrom(T runnable) {
        //mount configmap and secret as env sources
        List<V1EnvFromSource> envVarsFrom = new LinkedList<>();

        //secrets
        V1Secret secret = buildRunSecret(runnable);
        if (secret != null && secret.getMetadata() != null && secret.getMetadata().getName() != null) {
            envVarsFrom.add(
                new V1EnvFromSource().secretRef(new V1SecretEnvSource().name(secret.getMetadata().getName()))
            );
        }

        //TODO evaluate configuration into configmap

        return envVarsFrom;
    }

    protected List<V1Volume> buildVolumes(T runnable) {
        // Volumes to attach to the pod based on the volume spec with the additional volume_type
        List<V1Volume> volumes = new LinkedList<>();
        if (runnable.getVolumes() != null) {
            runnable
                .getVolumes()
                .forEach(volumeMap -> {
                    V1Volume volume = k8sBuilderHelper.getVolume(runnable.getId(), volumeMap);
                    if (volume != null) {
                        volumes.add(volume);
                    }
                });
        }

        //volumes defined in template
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //add template
            K8sRunnable template = templates.get(runnable.getTemplate()).getProfile();

            if (template.getVolumes() != null) {
                template
                    .getVolumes()
                    .forEach(volumeMap -> {
                        V1Volume volume = k8sBuilderHelper.getVolume(runnable.getId(), volumeMap);
                        if (volume != null) {
                            volumes.add(volume);
                        }
                    });
            }
        }

        //check if context build volume is required
        if (
            (runnable.getContextRefs() != null && !runnable.getContextRefs().isEmpty()) ||
            (runnable.getContextSources() != null && !runnable.getContextSources().isEmpty())
        ) {
            //build shared context dir if missing and defined
            if (
                (k8sProperties.getSharedVolume() != null && runnable.getVolumes() == null) ||
                runnable
                    .getVolumes()
                    .stream()
                    .noneMatch(v -> k8sProperties.getSharedVolume().getMountPath().equals(v.getMountPath()))
            ) {
                //use framework definition
                V1Volume volume = k8sBuilderHelper.getVolume(runnable.getId(), k8sProperties.getSharedVolume());
                volumes.add(volume);
            }

            // build config map volume with fixed definition
            V1Volume volume = new V1Volume().name("init-config-map");
            volume.configMap(new V1ConfigMapVolumeSource().name("init-config-map-" + runnable.getId()));
            volumes.add(volume);
        }

        return volumes;
    }

    protected List<V1VolumeMount> buildVolumeMounts(T runnable) {
        // Volumes to attach to the pod based on the volume spec with the additional volume_type
        List<V1VolumeMount> volumeMounts = new LinkedList<>();
        if (runnable.getVolumes() != null) {
            runnable
                .getVolumes()
                .forEach(volumeMap -> {
                    V1VolumeMount mount = k8sBuilderHelper.getVolumeMount(volumeMap);
                    if (mount != null) {
                        volumeMounts.add(mount);
                    }
                });
        }

        //volumes defined in template
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //add template
            K8sRunnable template = templates.get(runnable.getTemplate()).getProfile();

            if (template.getVolumes() != null) {
                template
                    .getVolumes()
                    .forEach(volumeMap -> {
                        V1VolumeMount mount = k8sBuilderHelper.getVolumeMount(volumeMap);
                        if (mount != null) {
                            volumeMounts.add(mount);
                        }
                    });
            }
        }

        //check if context build volume is required
        if (
            (runnable.getContextRefs() != null && !runnable.getContextRefs().isEmpty()) ||
            (runnable.getContextSources() != null && !runnable.getContextSources().isEmpty())
        ) {
            //build shared context dir if missing and defined
            if (
                (k8sProperties.getSharedVolume() != null && runnable.getVolumes() == null) ||
                runnable
                    .getVolumes()
                    .stream()
                    .noneMatch(v -> k8sProperties.getSharedVolume().getMountPath().equals(v.getMountPath()))
            ) {
                //use framework definition
                V1VolumeMount sharedMount = k8sBuilderHelper.getVolumeMount(k8sProperties.getSharedVolume());
                volumeMounts.add(sharedMount);
            }

            // Create config map volume mount with fixed definition
            V1VolumeMount configMapMount = new V1VolumeMount().name("init-config-map").mountPath("/init-config-map");
            volumeMounts.add(configMapMount);
        }

        return volumeMounts;
    }

    protected V1ResourceRequirements buildResources(T runnable) {
        V1ResourceRequirements resources = new V1ResourceRequirements();
        if (runnable.getResources() != null) {
            //translate requests and limits
            CoreResource res = runnable.getResources();
            Map<String, String> requests = new HashMap<>();
            Map<String, String> limits = new HashMap<>();

            //cpu
            Optional
                .ofNullable(res.getCpu())
                .ifPresent(cpu -> {
                    if (cpu.getRequests() != null) {
                        requests.put("cpu", cpu.getRequests());
                    }
                    if (cpu.getLimits() != null) {
                        limits.put("cpu", cpu.getLimits());
                    }
                });

            //mem
            Optional
                .ofNullable(res.getMem())
                .ifPresent(mem -> {
                    if (mem.getRequests() != null) {
                        requests.put("memory", mem.getRequests());
                    }
                    if (mem.getLimits() != null) {
                        limits.put("memory", mem.getLimits());
                    }
                });

            //gpu
            Optional
                .ofNullable(res.getGpu())
                .ifPresent(cpu -> {
                    if (gpuResourceKey != null && res.getGpu().getRequests() != null) {
                        requests.put(gpuResourceKey, res.getGpu().getRequests());
                    }
                    if (gpuResourceKey != null && res.getGpu().getLimits() != null) {
                        limits.put(gpuResourceKey, res.getGpu().getLimits());
                    }
                });

            resources.setRequests(k8sBuilderHelper.convertResources(requests));
            resources.setLimits(k8sBuilderHelper.convertResources(limits));
        }

        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //add template
            K8sRunnable template = templates.get(runnable.getTemplate()).getProfile();
            if (template != null && template.getResources() != null) {
                //override *all* definitions if set
                //translate requests and limits
                CoreResource res = template.getResources();
                Map<String, String> requests = new HashMap<>();
                Map<String, String> limits = new HashMap<>();

                //cpu
                Optional
                    .ofNullable(res.getCpu())
                    .ifPresent(cpu -> {
                        if (cpu.getRequests() != null) {
                            requests.put("cpu", cpu.getRequests());
                        }
                        if (cpu.getLimits() != null) {
                            limits.put("cpu", cpu.getLimits());
                        }
                    });

                //mem
                Optional
                    .ofNullable(res.getMem())
                    .ifPresent(mem -> {
                        if (mem.getRequests() != null) {
                            requests.put("memory", mem.getRequests());
                        }
                        if (mem.getLimits() != null) {
                            limits.put("memory", mem.getLimits());
                        }
                    });

                //gpu
                Optional
                    .ofNullable(res.getGpu())
                    .ifPresent(cpu -> {
                        if (gpuResourceKey != null && res.getGpu().getRequests() != null) {
                            requests.put(gpuResourceKey, res.getGpu().getRequests());
                        }
                        if (gpuResourceKey != null && res.getGpu().getLimits() != null) {
                            limits.put(gpuResourceKey, res.getGpu().getLimits());
                        }
                    });

                resources.setRequests(k8sBuilderHelper.convertResources(requests));
                resources.setLimits(k8sBuilderHelper.convertResources(limits));
            }
        }

        //default resources fallback
        Map<String, Quantity> requests = resources.getRequests() == null
            ? new HashMap<>()
            : new HashMap<>(resources.getRequests());

        if (cpuResourceDefinition.getRequests() != null) {
            //merge if missing
            requests.putIfAbsent("cpu", Quantity.fromString(cpuResourceDefinition.getRequests()));
        }

        if (memResourceDefinition.getRequests() != null) {
            //merge if missing
            requests.putIfAbsent("memory", Quantity.fromString(memResourceDefinition.getRequests()));
        }

        resources.setRequests(requests);

        //default limits
        Map<String, Quantity> limits = resources.getLimits() == null
            ? new HashMap<>()
            : new HashMap<>(resources.getLimits());

        if (cpuResourceDefinition.getLimits() != null) {
            //merge if missing
            limits.putIfAbsent("cpu", Quantity.fromString(cpuResourceDefinition.getLimits()));
        }

        if (memResourceDefinition.getLimits() != null) {
            //merge if missing
            limits.putIfAbsent("memory", Quantity.fromString(memResourceDefinition.getLimits()));
        }

        resources.setLimits(limits);

        return resources;
    }

    protected @Nullable Map<String, String> buildNodeSelector(T runnable) {
        Map<String, String> selectors = new HashMap<>();
        if (runnable.getNodeSelector() != null && !runnable.getNodeSelector().isEmpty()) {
            selectors.putAll(
                runnable
                    .getNodeSelector()
                    .stream()
                    .collect(Collectors.toMap(CoreNodeSelector::key, CoreNodeSelector::value))
            );
        }

        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //add template
            K8sRunnable template = templates.get(runnable.getTemplate()).getProfile();
            if (template.getNodeSelector() != null && !template.getNodeSelector().isEmpty()) {
                selectors.putAll(
                    template
                        .getNodeSelector()
                        .stream()
                        .collect(Collectors.toMap(CoreNodeSelector::key, CoreNodeSelector::value))
                );
            }
        }

        if (selectors.isEmpty()) {
            return null;
        }

        return selectors;
    }

    protected @Nullable List<V1Toleration> buildTolerations(T runnable) {
        List<V1Toleration> tolerations = new ArrayList<>();

        if (runnable.getTolerations() != null && !runnable.getTolerations().isEmpty()) {
            tolerations.addAll(runnable.getTolerations().stream().map(t -> t).collect(Collectors.toList()));
        }

        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //add template
            K8sRunnable template = templates.get(runnable.getTemplate()).getProfile();
            if (template.getTolerations() != null && !template.getTolerations().isEmpty()) {
                tolerations.addAll(template.getTolerations().stream().map(t -> t).collect(Collectors.toList()));
            }
        }

        if (tolerations.isEmpty()) {
            return null;
        }

        return tolerations;
    }

    protected List<String> buildCommand(T runnable) {
        return Optional.ofNullable(runnable.getCommand()).map(Collections::singletonList).orElse(null);
    }

    protected List<String> buildArgs(T runnable) {
        return Optional.ofNullable(runnable.getArgs()).map(Arrays::asList).orElse(null);
    }

    @Nullable
    protected List<V1LocalObjectReference> buildImagePullSecrets(T runnable) {
        //always include registry secret if defined
        return Optional
            .ofNullable(registrySecret)
            .map(s -> Collections.singletonList(new V1LocalObjectReference().name(registrySecret)))
            .orElse(null);
    }

    protected V1Secret buildRunSecret(T runnable) {
        Map<String, String> data = new HashMap<>();

        //add all user-defined secrets as-is
        if (runnable.getSecrets() != null) {
            runnable.getSecrets().forEach(s -> data.put(s.name(), s.value()));
        }

        //set core config as env
        if (runnable.getConfigurationMap() != null) {
            runnable.getConfigurationMap().entrySet().forEach(e -> data.put(e.getKey().toUpperCase(), e.getValue()));
        }

        //set core credentials as env
        if (runnable.getCredentialsMap() != null) {
            runnable.getCredentialsMap().entrySet().forEach(e -> data.put(e.getKey().toUpperCase(), e.getValue()));
        }

        if (!data.isEmpty()) {
            V1Secret secret = k8sSecretHelper.convertSecrets(
                k8sSecretHelper.getSecretName(runnable.getRuntime(), runnable.getTask(), runnable.getId()),
                data
            );

            if (secret != null && secret.getMetadata() != null) {
                //attach labels
                Map<String, String> labels = buildLabels(runnable);
                labels
                    .entrySet()
                    .forEach(e -> {
                        secret.getMetadata().putLabelsItem(e.getKey(), e.getValue());
                    });
            }

            return secret;
        }

        return null;
    }

    @SuppressWarnings("null")
    protected void storeRunSecret(V1Secret secret) throws K8sFrameworkException {
        try {
            k8sSecretHelper.storeSecretData(secret.getMetadata().getName(), secret.getStringData());
        } catch (JsonProcessingException e) {
            throw new K8sFrameworkException(e.getMessage());
        } catch (ApiException e) {
            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    protected void cleanRunSecret(T runnable) {
        String secretName = k8sSecretHelper.getSecretName(runnable.getRuntime(), runnable.getTask(), runnable.getId());
        try {
            k8sSecretHelper.deleteSecret(secretName);
        } catch (ApiException e) {
            log.warn("Failed to delete secret {}", secretName, e);
        }
    }

    public V1ConfigMap buildInitConfigMap(T runnable) throws K8sFrameworkException {
        //check context refs and build config
        if (
            (runnable.getContextSources() == null || runnable.getContextSources().isEmpty()) &&
            (runnable.getContextRefs() == null || runnable.getContextRefs().isEmpty())
        ) {
            //nothing to do
            return null;
        }

        //build and create configMap
        log.debug("build initConfigMap for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("contextSources {}", runnable.getContextSources());
            log.trace("contextRefs {}", runnable.getContextRefs());
        }

        try {
            // Generate Config map
            Optional<List<ContextRef>> contextRefsOpt = Optional.ofNullable(runnable.getContextRefs());
            Optional<List<ContextSource>> contextSourcesOpt = Optional.ofNullable(runnable.getContextSources());

            V1ConfigMap configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("init-config-map-" + runnable.getId()).labels(buildLabels(runnable)))
                .data(
                    MapUtils.mergeMultipleMaps(
                        // Generate context-refs.txt if exist
                        contextRefsOpt
                            .map(contextRefsList ->
                                Map.of(
                                    "context-refs.txt",
                                    contextRefsList
                                        .stream()
                                        .map(v ->
                                            v.getProtocol() + "," + v.getDestination() + "," + v.getSource() + "\n"
                                        )
                                        .collect(Collectors.joining(""))
                                )
                            )
                            .orElseGet(Map::of),
                        // Generate context-sources.txt if exist
                        contextSourcesOpt
                            .map(contextSources ->
                                contextSources
                                    .stream()
                                    .filter(e -> StringUtils.hasText(e.getBase64()))
                                    .collect(
                                        Collectors.toMap(
                                            c ->
                                                Base64
                                                    .getUrlEncoder()
                                                    .withoutPadding()
                                                    .encodeToString(c.getName().getBytes()),
                                            c ->
                                                new String(
                                                    Base64.getDecoder().decode(c.getBase64()),
                                                    StandardCharsets.UTF_8
                                                )
                                        )
                                    )
                            )
                            .orElseGet(Map::of),
                        contextSourcesOpt
                            .map(contextSources ->
                                Map.of(
                                    "context-sources-map.txt",
                                    contextSources
                                        .stream()
                                        .filter(e -> StringUtils.hasText(e.getBase64()))
                                        .map(c ->
                                            Base64
                                                .getUrlEncoder()
                                                .withoutPadding()
                                                .encodeToString(c.getName().getBytes()) +
                                            "," +
                                            c.getName() +
                                            "\n"
                                        )
                                        .collect(Collectors.joining(""))
                                )
                            )
                            .orElseGet(Map::of)
                    )
                );

            if (log.isTraceEnabled()) {
                log.trace("configMap for {}: {}", runnable.getId(), configMap);
            }

            return configMap;
        } catch (NullPointerException e) {
            throw new K8sFrameworkException(e.getMessage());
        }
    }

    public V1SecurityContext buildSecurityContext(T runnable) throws K8sFrameworkException {
        V1SecurityContext context = new V1SecurityContext();
        //always disable privileged
        context.privileged(false);

        //enforce policy for non root when requested by admin
        if (disableRoot) {
            context.allowPrivilegeEscalation(false);
            context.runAsNonRoot(true);
        }

        //check for additional config, root is *always* disallowed
        if (runnable.getRunAsUser() != null && runnable.getRunAsUser() != 0) {
            context.setRunAsUser((long) runnable.getRunAsUser().intValue());
        }
        if (runnable.getRunAsGroup() != null && runnable.getRunAsGroup() != 0) {
            context.setRunAsGroup((long) runnable.getRunAsGroup().intValue());
        }

        return context;
    }

    public V1PodSecurityContext buildPodSecurityContext(T runnable) throws K8sFrameworkException {
        V1PodSecurityContext context = new V1PodSecurityContext();

        //enforce policy for non root when requested by admin
        if (disableRoot) {
            context.runAsNonRoot(true);
        }

        //check for additional config, root is *always* disallowed
        if (runnable.getRunAsUser() != null && runnable.getRunAsUser() != 0) {
            context.setRunAsUser((long) runnable.getRunAsUser().intValue());
        }
        if (runnable.getRunAsGroup() != null && runnable.getRunAsGroup() != 0) {
            context.setRunAsGroup((long) runnable.getRunAsGroup().intValue());
        }
        if (runnable.getFsGroup() != null && runnable.getFsGroup() != 0) {
            context.setFsGroup((long) runnable.getFsGroup().intValue());
        }
        return context;
    }

    public String buildPriorityClassName(T runnable) throws K8sFrameworkException {
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //use template
            return templates.get(runnable.getTemplate()).getProfile().getPriorityClass();
        }

        return runnable.getPriorityClass();
    }

    public String buildRuntimeClassName(T runnable) throws K8sFrameworkException {
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //use template
            return templates.get(runnable.getTemplate()).getProfile().getRuntimeClass();
        }

        return runnable.getRuntimeClass();
    }

    public V1Affinity buildAffinity(T runnable) throws K8sFrameworkException {
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //use template
            return templates.get(runnable.getTemplate()).getProfile().getAffinity();
        }

        return runnable.getAffinity();
    }

    public List<V1PersistentVolumeClaim> buildPersistentVolumeClaims(T runnable) throws K8sFrameworkException {
        // Volumes to attach to the pod based on the volume spec with the additional volume_type
        List<V1PersistentVolumeClaim> volumes = new LinkedList<>();
        if (runnable.getVolumes() != null) {
            runnable
                .getVolumes()
                .stream()
                .filter(v -> v.getVolumeType() == CoreVolume.VolumeType.persistent_volume_claim)
                .forEach(v -> {
                    //build claim
                    Map<String, String> spec = Optional.ofNullable(v.getSpec()).orElse(Collections.emptyMap());
                    Quantity quantity = Quantity.fromString(
                        spec.getOrDefault("size", pvcResourceDefinition.getRequests())
                    );
                    V1VolumeResourceRequirements req = new V1VolumeResourceRequirements()
                        .requests(Map.of("storage", quantity));

                    //enforce limit
                    //TODO check if valid!
                    if (pvcResourceDefinition.getLimits() != null) {
                        Quantity limit = Quantity.fromString(pvcResourceDefinition.getLimits());
                        req.setLimits(Map.of("storage", limit));
                    }

                    V1PersistentVolumeClaim claim = new V1PersistentVolumeClaim()
                        .metadata(
                            new V1ObjectMeta().name(k8sBuilderHelper.getVolumeName(runnable.getId(), v.getName()))
                        )
                        .spec(
                            new V1PersistentVolumeClaimSpec()
                                .accessModes(Collections.singletonList("ReadWriteOnce"))
                                .volumeMode("Filesystem")
                                .storageClassName(spec.getOrDefault("storage_class", pvcStorageClass))
                                .resources(req)
                        );

                    volumes.add(claim);
                });
        }

        //volumes defined in template
        //TODO evaluate support
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //add template
            K8sRunnable template = templates.get(runnable.getTemplate()).getProfile();

            if (template.getVolumes() != null) {
                template
                    .getVolumes()
                    .stream()
                    .filter(v -> v.getVolumeType() == CoreVolume.VolumeType.persistent_volume_claim)
                    .forEach(v -> {
                        //TODO evaluate support
                        log.warn("Volumes defined in templates are not fully supported");
                    });
            }
        }

        return volumes;
    }
}
