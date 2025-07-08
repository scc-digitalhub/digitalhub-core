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
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1CronJob;
import io.kubernetes.client.openapi.models.V1CronJobSpec;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1JobTemplateSpec;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCronJobRunnable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@FrameworkComponent(framework = K8sCronJobFramework.FRAMEWORK)
public class K8sCronJobFramework extends K8sBaseFramework<K8sCronJobRunnable, V1CronJob> {

    public static final String FRAMEWORK = "k8scronjob";

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};
    private final BatchV1Api batchV1Api;

    private String initImage;
    private int activeDeadlineSeconds = K8sJobFramework.DEADLINE_SECONDS;

    public K8sCronJobFramework(ApiClient apiClient) {
        super(apiClient);
        this.batchV1Api = new BatchV1Api(apiClient);
    }

    @Autowired
    public void setActiveDeadlineSeconds(
        @Value("${kubernetes.jobs.activeDeadlineSeconds}") Integer activeDeadlineSeconds
    ) {
        Assert.isTrue(
            activeDeadlineSeconds > K8sJobFramework.DEADLINE_MIN,
            "Minimum deadline seconds is " + K8sJobFramework.DEADLINE_MIN
        );
        this.activeDeadlineSeconds = activeDeadlineSeconds;
    }

    @Autowired
    public void setInitImage(@Value("${kubernetes.init.image}") String initImage) {
        this.initImage = initImage;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        Assert.hasText(initImage, "init image should be set to a valid builder-tool image");

        //load templates
        this.templates = loadTemplates(K8sCronJobRunnable.class);
    }

    @Override
    public K8sCronJobRunnable run(K8sCronJobRunnable runnable) throws K8sFrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        Map<String, KubernetesObject> results = new HashMap<>();

        //create job
        V1CronJob job = build(runnable);

        //secrets
        V1Secret secret = buildRunSecret(runnable);
        if (secret != null) {
            storeRunSecret(secret);
            //clear data before storing
            results.put("secret", secret.stringData(Collections.emptyMap()).data(Collections.emptyMap()));
        }

        try {
            V1ConfigMap initConfigMap = buildInitConfigMap(runnable);
            if (initConfigMap != null) {
                log.info("create initConfigMap for {}", String.valueOf(initConfigMap.getMetadata().getName()));
                coreV1Api.createNamespacedConfigMap(namespace, initConfigMap, null, null, null, null);
                //clear data before storing
                results.put("configMap", initConfigMap.data(Collections.emptyMap()));
            }
        } catch (NullPointerException e) {
            throw new K8sFrameworkException(e.getMessage());
        } catch (ApiException e) {
            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }

        log.info("create job for {}", String.valueOf(job.getMetadata().getName()));
        job = create(job);
        results.put("cronJob", job);

        //update state
        runnable.setState(State.RUNNING.name());

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
            runnable.setMessage(String.format("cronJob %s created", job.getMetadata().getName()));
        }

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sCronJobRunnable stop(K8sCronJobRunnable runnable) throws K8sFrameworkException {
        log.info("stop for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        V1CronJob job = get(build(runnable));

        //stop by deleting
        log.info("delete job for {}", String.valueOf(job.getMetadata().getName()));
        delete(job);

        //update state
        runnable.setState(State.STOPPED.name());

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sCronJobRunnable delete(K8sCronJobRunnable runnable) throws K8sFrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        V1CronJob job;
        try {
            job = get(build(runnable));
        } catch (K8sFrameworkException e) {
            runnable.setState(State.DELETED.name());
            return runnable;
        }
        //secrets
        cleanRunSecret(runnable);

        log.info("delete job for {}", String.valueOf(job.getMetadata().getName()));
        delete(job);

        //update state
        runnable.setState(State.DELETED.name());
        runnable.setMessage(String.format("job %s deleted", job.getMetadata().getName()));

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    public V1CronJob build(K8sCronJobRunnable runnable) throws K8sFrameworkException {
        log.debug("build for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }
        // Generate jobName and ContainerName
        String jobName = k8sBuilderHelper.getJobName(runnable.getRuntime(), runnable.getTask(), runnable.getId());
        log.debug("build k8s job for {}", jobName);

        //check template
        K8sTemplate<K8sCronJobRunnable> template = null;
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //get template
            template = templates.get(runnable.getTemplate());
        }

        //build labels
        Map<String, String> labels = buildLabels(runnable);

        // Create the Job metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(jobName).labels(labels);

        if (!StringUtils.hasText(runnable.getSchedule())) {
            throw new K8sFrameworkException("missing or invalid schedule in spec");
        }

        V1Job job = buildJob(runnable);

        V1CronJobSpec cronJobSpec = Optional
            .ofNullable(template)
            .map(K8sTemplate::getCronJob)
            .map(V1CronJob::getSpec)
            .orElse(new V1CronJobSpec());

        cronJobSpec.schedule(runnable.getSchedule()).jobTemplate(new V1JobTemplateSpec().spec(job.getSpec()));

        return new V1CronJob().metadata(metadata).spec(cronJobSpec);
    }

    public V1Job buildJob(K8sCronJobRunnable runnable) throws K8sFrameworkException {
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
        K8sTemplate<K8sCronJobRunnable> template = null;
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //get template
            template = templates.get(runnable.getTemplate());
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
            .map(K8sTemplate::getCronJob)
            .map(V1CronJob::getSpec)
            .map(V1CronJobSpec::getJobTemplate)
            .map(V1JobTemplateSpec::getSpec)
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
                //TODO below execute a command that is a Go script
                .command(List.of("/bin/bash", "-c", "/app/builder-tool.sh"));

            podSpec.setInitContainers(Collections.singletonList(initContainer));
        }

        // Create a PodTemplateSpec with the PodSpec
        V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec().metadata(metadata).spec(podSpec);

        int backoffLimit = Optional
            .ofNullable(runnable.getBackoffLimit())
            .orElse(K8sJobFramework.DEFAULT_BACKOFF_LIMIT)
            .intValue();

        // Create the JobSpec with the PodTemplateSpec, leverage template if provided
        V1JobSpec jobSpec = Optional
            .ofNullable(template)
            .map(K8sTemplate::getCronJob)
            .map(V1CronJob::getSpec)
            .map(V1CronJobSpec::getJobTemplate)
            .map(V1JobTemplateSpec::getSpec)
            .orElse(new V1JobSpec());

        jobSpec
            .activeDeadlineSeconds(Long.valueOf(activeDeadlineSeconds))
            //TODO support work-queue style/parallel jobs
            .parallelism(1)
            .completions(1)
            .backoffLimit(backoffLimit)
            .template(podTemplateSpec);

        // Create the V1Job object with metadata and JobSpec
        return new V1Job().metadata(metadata).spec(jobSpec);
    }

    /*
     * K8s
     */

    public V1CronJob get(@NotNull V1CronJob job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("get k8s job for {}", jobName);

            return batchV1Api.readNamespacedCronJob(jobName, namespace, null);
        } catch (ApiException e) {
            log.info("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    public V1CronJob create(V1CronJob job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("create k8s job for {}", jobName);

            //dispatch job via api
            return batchV1Api.createNamespacedCronJob(namespace, job, null, null, null, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    public void delete(V1CronJob job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("delete k8s job for {}", jobName);

            batchV1Api.deleteNamespacedCronJob(jobName, namespace, null, null, null, null, "Foreground", null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }
}
