package it.smartcommunitylabdhub.framework.kaniko.infrastructure.kaniko;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sJobFramework;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sKanikoRunnable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

@Slf4j
@FrameworkComponent(framework = K8sKanikoFramework.FRAMEWORK)
public class K8sKanikoFramework extends K8sBaseFramework<K8sKanikoRunnable, V1Job> {

    public static final String FRAMEWORK = "k8sbuild";
    private final BatchV1Api batchV1Api;

    @Value("${runtime.kaniko.image}")
    private String image;

    @Autowired
    private K8sJobFramework jobFramework;


    public K8sKanikoFramework(ApiClient apiClient) {
        super(apiClient);
        this.batchV1Api = new BatchV1Api(apiClient);
    }

    // TODO: instead of void define a Result object that have to be merged with the run from the
    // caller.
    @Override
    public K8sKanikoRunnable run(K8sKanikoRunnable runnable) throws K8sFrameworkException {
        V1Job job = build(runnable);
        job = create(job);

        // Update runnable state..
        runnable.setState(State.RUNNING.name());

        return runnable;
    }

    @Override
    public K8sKanikoRunnable stop(K8sKanikoRunnable runnable) throws K8sFrameworkException {
        V1Job job = get(build(runnable));

        //stop by deleting
        delete(job);
        runnable.setState(State.STOPPED.name());

        return runnable;
    }

    @Override
    public K8sKanikoRunnable delete(K8sKanikoRunnable runnable) throws K8sFrameworkException {
        V1Job job;
        try {
            job = get(build(runnable));
        } catch (K8sFrameworkException e) {
            runnable.setState(State.DELETED.name());
            return runnable;
        }

        delete(job);
        runnable.setState(State.DELETED.name());

        return runnable;
    }

    @Override
    public V1Job build(K8sKanikoRunnable runnable) throws K8sFrameworkException {
        // Log service execution initiation
        log.info("----------------- BUILD KUBERNETES CRON JOB ----------------");

        // Generate jobName and ContainerName
        String jobName = k8sBuilderHelper.getJobName(runnable.getRuntime(), runnable.getTask(), runnable.getId());

        //build labels
        Map<String, String> labels = buildLabels(runnable);

        // Create the Job metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(jobName).labels(labels);
        
        // Create sharedVolume
        CoreVolume sharedVolume = new CoreVolume("empty_dir", "/shared", "shared-dir", Map.of());

        // Merge volumes
        runnable.getVolumes().add(sharedVolume);

        K8sJobRunnable k8sJobRunnable = K8sJobRunnable
                .builder()
                .id(runnable.getId())
                .args(runnable.getArgs())
                .affinity(runnable.getAffinity())
                .backoffLimit(runnable.getBackoffLimit())
                .command(runnable.getCommand())
                .envs(runnable.getEnvs())
                .image(image)
                .labels(runnable.getLabels())
                .nodeSelector(runnable.getNodeSelector())
                .project(runnable.getProject())
                .resources(runnable.getResources())
                .runtime(runnable.getRuntime())
                .secrets(runnable.getSecrets())
                .task(runnable.getTask())
                .tolerations(runnable.getTolerations())
                .volumes(runnable.getVolumes())
                .state(State.READY.name())
                .build();

        // Build the Job
        V1Job job = jobFramework.build(k8sJobRunnable);

        // Build the Init Container
        V1Container initContainer = new V1Container()
                .name(runnable.getInitContainer().getName())
                .image(runnable.getInitContainer().getImage())
                .volumeMounts(
                        List.of(
                                new V1VolumeMount().name("shared-dir").mountPath("/shared")
                        )
                ).command(runnable.getInitContainer().getCommand());

        // Add Init Container
        Objects.requireNonNull(Objects.requireNonNull(job.getSpec()).getTemplate().getSpec())
                .getContainers()
                .add(initContainer);

        // Create a new job with updated metadata and spec.
        return new V1Job().metadata(metadata).spec(job.getSpec());
    }

    @Override
    public V1Job apply(@NotNull V1Job job) throws K8sFrameworkException {
        return job;
    }

    @Override
    public V1Job get(@NotNull V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            // Log service execution initiation
            log.info("----------------- GET KUBERNETES CRON JOB ----------------");

            return batchV1Api.readNamespacedJob(job.getMetadata().getName(), namespace, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage());
        }
    }

    @Override
    public V1Job create(V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            // Log service execution initiation
            log.info("----------------- RUN KUBERNETES CRON JOB ----------------");

            //dispatch job via api
            V1Job createdJob = batchV1Api.createNamespacedJob(namespace, job, null, null, null, null);
            log.info("Job created: {}", Objects.requireNonNull(createdJob.getMetadata()).getName());
            return createdJob;
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getResponseBody());
        }
    }

    @Override
    public void delete(V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            // Log service execution initiation
            log.info("----------------- RUN KUBERNETES CRON JOB ----------------");

            batchV1Api.deleteNamespacedJob(
                    job.getMetadata().getName(),
                    namespace,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getResponseBody());
        }
    }
}
