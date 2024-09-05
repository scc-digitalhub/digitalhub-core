package it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s;

import com.fasterxml.jackson.core.type.TypeReference;
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
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCronJobRunnable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    public void setInitImage(@Value("${kubernetes.init-image}") String initImage) {
        this.initImage = initImage;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        Assert.hasText(initImage, "init image should be set to a valid builder-tool image");
    }

    @Override
    public K8sCronJobRunnable run(K8sCronJobRunnable runnable) throws K8sFrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        //create job
        V1CronJob job = build(runnable);

        //secrets
        V1Secret secret = buildRunSecret(runnable);
        if (secret != null) {
            storeRunSecret(secret);
        }

        try {
            V1ConfigMap initConfigMap = buildInitConfigMap(runnable);
            if (initConfigMap != null) {
                log.info("create initConfigMap for {}", String.valueOf(initConfigMap.getMetadata().getName()));
                coreV1Api.createNamespacedConfigMap(namespace, initConfigMap, null, null, null, null);
            }
        } catch (ApiException | NullPointerException e) {
            throw new K8sFrameworkException(e.getMessage());
        }

        log.info("create job for {}", String.valueOf(job.getMetadata().getName()));
        job = create(job);

        //update state
        runnable.setState(State.RUNNING.name());

        if (!"disable".equals(collectResults)) {
            //update results
            try {
                runnable.setResults(Map.of("cronJob", mapper.convertValue(job, typeRef)));
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }
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

        //build labels
        Map<String, String> labels = buildLabels(runnable);

        // Create the Job metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(jobName).labels(labels);

        if (!StringUtils.hasText(runnable.getSchedule())) {
            throw new K8sFrameworkException("missing or invalid schedule in spec");
        }

        V1Job job = buildJob(runnable);
        V1CronJobSpec cronJobSpec = new V1CronJobSpec()
            .schedule(runnable.getSchedule())
            .jobTemplate(new V1JobTemplateSpec().spec(job.getSpec()));

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

        //check if context build is required
        if (
            (runnable.getContextRefs() != null && !runnable.getContextRefs().isEmpty()) ||
            (runnable.getContextSources() != null && !runnable.getContextSources().isEmpty())
        ) {
            // Create sharedVolume
            CoreVolume sharedVolume = new CoreVolume(
                CoreVolume.VolumeType.empty_dir,
                "/shared",
                "shared-dir",
                Map.of("sizeLimit", "100Mi")
            );

            // Create config map volume
            CoreVolume configMapVolume = new CoreVolume(
                CoreVolume.VolumeType.config_map,
                "/init-config-map",
                "init-config-map",
                Map.of("name", "init-config-map-" + runnable.getId())
            );

            List<V1Volume> initVolumes = List.of(
                k8sBuilderHelper.getVolume(sharedVolume),
                k8sBuilderHelper.getVolume(configMapVolume)
            );
            List<V1VolumeMount> initVolumesMounts = List.of(
                k8sBuilderHelper.getVolumeMount(sharedVolume),
                k8sBuilderHelper.getVolumeMount(configMapVolume)
            );

            //add volume
            volumes = Stream.concat(buildVolumes(runnable).stream(), initVolumes.stream()).collect(Collectors.toList());
            volumeMounts =
                Stream
                    .concat(buildVolumeMounts(runnable).stream(), initVolumesMounts.stream())
                    .collect(Collectors.toList());
        }

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

        // Create a PodSpec for the container
        V1PodSpec podSpec = new V1PodSpec()
            .containers(Collections.singletonList(container))
            .nodeSelector(buildNodeSelector(runnable))
            .affinity(buildAffinity(runnable))
            .tolerations(buildTolerations(runnable))
            .runtimeClassName(buildRuntimeClassName(runnable))
            .priorityClassName(buildPriorityClassName(runnable))
            .volumes(volumes)
            .restartPolicy("Never")
            .imagePullSecrets(buildImagePullSecrets(runnable));

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
                .volumeMounts(volumeMounts)
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

        // Create the JobSpec with the PodTemplateSpec
        V1JobSpec jobSpec = new V1JobSpec()
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

            throw new K8sFrameworkException(e.getMessage());
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

            throw new K8sFrameworkException(e.getResponseBody());
        }
    }

    public void delete(V1CronJob job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("delete k8s job for {}", jobName);

            batchV1Api.deleteNamespacedCronJob(jobName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getResponseBody());
        }
    }
}
