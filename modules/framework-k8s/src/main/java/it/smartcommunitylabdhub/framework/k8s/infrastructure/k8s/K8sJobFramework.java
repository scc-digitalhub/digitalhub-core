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
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaim;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnableState;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@FrameworkComponent(framework = K8sJobFramework.FRAMEWORK)
public class K8sJobFramework extends K8sBaseFramework<K8sJobRunnable, V1Job> {

    public static final String FRAMEWORK = "k8sjob";

    public static final int DEADLINE_SECONDS = 3600 * 24 * 3; //3 days
    public static final int DEADLINE_MIN = 120;

    public static final int DEFAULT_BACKOFF_LIMIT = 0;

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};
    private final BatchV1Api batchV1Api;

    private String initImage;
    private List<String> initCommand = null;

    private int activeDeadlineSeconds = DEADLINE_SECONDS;
    private boolean suspend = false;

    public K8sJobFramework(ApiClient apiClient) {
        super(apiClient);
        batchV1Api = new BatchV1Api(apiClient);
    }

    @Autowired
    public void setActiveDeadlineSeconds(
        @Value("${kubernetes.jobs.activeDeadlineSeconds}") Integer activeDeadlineSeconds
    ) {
        Assert.isTrue(activeDeadlineSeconds > DEADLINE_MIN, "Minimum deadline seconds is " + DEADLINE_MIN);
        this.activeDeadlineSeconds = activeDeadlineSeconds;
    }

    @Autowired
    public void setSuspend(@Value("${kubernetes.jobs.suspend}") Boolean suspend) {
        if (suspend != null) {
            this.suspend = suspend.booleanValue();
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

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        Assert.hasText(initImage, "init image should be set to a valid builder-tool image");

        //load templates
        this.templates = loadTemplates(K8sJobRunnable.class);

        //build default shared volume definition for context building
        if (k8sProperties.getSharedVolume() == null) {
            k8sProperties.setSharedVolume(
                new CoreVolume(CoreVolume.VolumeType.empty_dir, "/shared", "shared-dir", Map.of("sizeLimit", "100Mi"))
            );
        }
    }

    @Override
    public K8sJobRunnable run(K8sJobRunnable runnable) throws K8sFrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        Map<String, Object> results = new HashMap<>();

        //create job
        V1Job job = build(runnable);

        //secrets
        V1Secret secret = buildRunSecret(runnable);
        if (secret != null) {
            storeRunSecret(secret);
            //clear data before storing
            results.put("secret", secret.stringData(Collections.emptyMap()).data(Collections.emptyMap()));
        }

        //configmap
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

        //pvcs
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

        //shared volumes
        List<V1PersistentVolumeClaim> sharedPvcs = buildSharedVolumeClaims(runnable);
        //shared volumes are already created, we check permissions
        if (sharedPvcs != null) {
            List<V1PersistentVolumeClaim> sharedPvcsFinal = new ArrayList<>();
            for (V1PersistentVolumeClaim pvc : sharedPvcs) {
                String pvcName = pvc.getMetadata().getName();
                try {
                    V1PersistentVolumeClaim v = coreV1Api.readNamespacedPersistentVolumeClaim(pvcName, namespace, null);
                    if (v == null) {
                        throw new K8sFrameworkException("Shared volume " + pvcName + " not found");
                    }

                    //check project label matches this runnable
                    Map.Entry<String, String> label = k8sLabelHelper.buildCoreLabel("project", runnable.getProject());
                    if (v != null && v.getMetadata() != null && v.getMetadata().getLabels() != null) {
                        if (!label.getValue().equals(v.getMetadata().getLabels().get(label.getKey()))) {
                            throw new K8sFrameworkException("Shared volume project mismatch");
                        }
                    } else {
                        throw new K8sFrameworkException("Shared volume " + pvcName + "invalid");
                    }

                    //keep updated definition
                    sharedPvcsFinal.add(v);
                } catch (ApiException e) {
                    log.error("Error with k8s: {}", e.getMessage());
                    if (log.isTraceEnabled()) {
                        log.trace("k8s api response: {}", e.getResponseBody());
                    }

                    throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
                }
            }

            //store
            results.put("sharedPvs", sharedPvcsFinal);
        }

        log.info("create job for {}", String.valueOf(job.getMetadata().getName()));
        job = create(job);
        results.put("job", job);

        //update state
        runnable.setState(K8sRunnableState.PENDING.name());

        if (!"disable".equals(collectResults)) {
            //update results
            try {
                runnable.setResults(
                    results
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(Entry::getKey, e -> mapper.convertValue(e, typeRef)))
                );
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }
        }

        if (job != null) {
            runnable.setMessage(String.format("job %s created", job.getMetadata().getName()));
        }

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sJobRunnable stop(K8sJobRunnable runnable) throws K8sFrameworkException {
        log.info("stop for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        List<String> messages = new ArrayList<>();
        K8sFrameworkException exception = null;

        V1Job job = get(build(runnable));

        //stop by deleting
        try {
            log.info("delete job for {}", String.valueOf(job.getMetadata().getName()));
            delete(job);
            messages.add(String.format("job %s deleted", job.getMetadata().getName()));
        } catch (K8sFrameworkException | NullPointerException e) {
            //collect but keep going
            log.error("error deleting job {}: {}", runnable.getId(), e.getMessage());
            exception = new K8sFrameworkException(e.getMessage());
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

        //update state
        runnable.setState(K8sRunnableState.STOPPED.name());
        runnable.setMessage(String.join(", ", messages));

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        if (exception != null) {
            throw exception;
        }

        return runnable;
    }

    @Override
    public K8sJobRunnable delete(K8sJobRunnable runnable) throws K8sFrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        K8sFrameworkException exception = null;
        List<String> messages = new ArrayList<>();

        V1Job job = null;
        try {
            job = get(build(runnable));
        } catch (K8sFrameworkException | IllegalArgumentException e) {
            runnable.setState(K8sRunnableState.DELETED.name());
        }

        if (job != null) {
            try {
                log.info("delete job for {}", String.valueOf(job.getMetadata().getName()));
                delete(job);
                messages.add(String.format("job %s deleted", job.getMetadata().getName()));
            } catch (K8sFrameworkException | NullPointerException e) {
                //collect but keep going
                log.error("error deleting job {}: {}", runnable.getId(), e.getMessage());
                exception = new K8sFrameworkException(e.getMessage());
            }
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

        //pvcs
        try {
            List<V1PersistentVolumeClaim> pvcs = buildPersistentVolumeClaims(runnable);
            if (pvcs != null) {
                for (V1PersistentVolumeClaim pvc : pvcs) {
                    String pvcName = pvc.getMetadata().getName();
                    try {
                        V1PersistentVolumeClaim v = coreV1Api.readNamespacedPersistentVolumeClaim(
                            pvcName,
                            namespace,
                            null
                        );
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
        } catch (IllegalArgumentException re) {
            //don't propagate
            log.error("Error with k8s: {}", re.getMessage());
        }

        if (!"keep".equals(collectResults)) {
            //update results
            try {
                runnable.setResults(Collections.emptyMap());
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }
        }

        //update state
        runnable.setState(K8sRunnableState.DELETED.name());
        runnable.setMessage(String.join(", ", messages));

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        if (exception != null) {
            throw exception;
        }

        return runnable;
    }

    public V1Job build(K8sJobRunnable runnable) throws K8sFrameworkException {
        log.debug("build for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        // Generate jobName and ContainerName
        String jobName = k8sBuilderHelper.getJobName(runnable.getRuntime(), runnable.getTask(), runnable.getId());
        String containerName = k8sBuilderHelper.getContainerName(
            runnable.getRuntime(),
            runnable.getTask(),
            runnable.getId()
        );

        log.debug("build k8s job for {}", jobName);

        //check template
        K8sTemplate<K8sJobRunnable> template = null;
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //get template
            template = templates.get(runnable.getTemplate());
        } else if (templates.containsKey(DEFAULT_TEMPLATE)) {
            //use default template
            template = templates.get(DEFAULT_TEMPLATE);
        }

        //build labels
        Map<String, String> labels = buildLabels(runnable);

        // Create the Job metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(jobName).labels(labels);

        // Prepare environment variables for the Kubernetes job
        List<V1EnvFromSource> envFrom = buildEnvFrom(runnable);
        List<V1EnvVar> env = buildEnv(runnable);

        // Volumes to attach to the pod based on the volume spec with the additional volume_type
        List<V1Volume> volumes = buildVolumes(runnable);
        List<V1VolumeMount> volumeMounts = buildVolumeMounts(runnable);

        // resources
        V1ResourceRequirements resources = buildResources(runnable);

        //command params
        List<String> command = buildCommand(runnable);
        List<String> args = buildArgs(runnable);

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

        // Create a PodSpec for the container, leverage template if provided
        V1PodSpec podSpec = Optional
            .ofNullable(template)
            .map(K8sTemplate::getJob)
            .map(V1Job::getSpec)
            .map(V1JobSpec::getTemplate)
            .map(V1PodTemplateSpec::getSpec)
            .orElse(new V1PodSpec());

        podSpec
            .containers(Collections.singletonList(container))
            .nodeSelector(buildNodeSelector(runnable))
            .affinity(buildAffinity(runnable))
            .tolerations(buildTolerations(runnable))
            .runtimeClassName(buildRuntimeClassName(runnable))
            .priorityClassName(buildPriorityClassName(runnable))
            .volumes(volumes)
            .restartPolicy("Never")
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
                .resources(resources)
                .env(env)
                .envFrom(envFrom)
                .securityContext(buildSecurityContext(runnable))
                .command(initCommand);

            podSpec.setInitContainers(Collections.singletonList(initContainer));
        }

        // Create a PodTemplateSpec with the PodSpec
        V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec().metadata(metadata).spec(podSpec);

        int backoffLimit = Optional.ofNullable(runnable.getBackoffLimit()).orElse(DEFAULT_BACKOFF_LIMIT).intValue();

        if (template != null && template.getProfile().getBackoffLimit() != null) {
            //override with template
            backoffLimit = template.getProfile().getBackoffLimit().intValue();
        }

        // Create the JobSpec with the PodTemplateSpec, leverage template if provided
        V1JobSpec jobSpec = Optional
            .ofNullable(template)
            .map(K8sTemplate::getJob)
            .map(V1Job::getSpec)
            .orElse(new V1JobSpec());

        jobSpec
            .activeDeadlineSeconds(Long.valueOf(activeDeadlineSeconds))
            //TODO support work-queue style/parallel jobs
            .parallelism(1)
            .completions(1)
            .backoffLimit(backoffLimit)
            .template(podTemplateSpec);

        //opt-int for suspend==true
        if (suspend) {
            jobSpec.suspend(suspend);
        }

        // Create the V1Job object with metadata and JobSpec
        return new V1Job().metadata(metadata).spec(jobSpec);
    }

    /*
     * K8s
     */

    public V1Job get(@NotNull V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("get k8s job for {}", jobName);

            return batchV1Api.readNamespacedJob(jobName, namespace, null);
        } catch (ApiException e) {
            log.info("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    private V1Job create(V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("create k8s job for {}", jobName);

            //dispatch job via api
            V1Job createdJob = batchV1Api.createNamespacedJob(namespace, job, null, null, null, null);
            log.info("Job created: {}", Objects.requireNonNull(createdJob.getMetadata()).getName());
            return createdJob;
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    private void delete(V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("delete k8s job for {}", jobName);

            batchV1Api.deleteNamespacedJob(jobName, namespace, null, null, null, null, null, "Foreground", null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }
}
