package it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s;

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
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sBuildRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;

@Slf4j
@FrameworkComponent(framework = K8sBuildFramework.FRAMEWORK)
public class K8sBuildFramework extends K8sBaseFramework<K8sBuildRunnable, V1Job> {

    public static final String FRAMEWORK = "k8scronjob";

    private final BatchV1Api batchV1Api;

    @Autowired
    private K8sJobFramework jobFramework;

    public K8sBuildFramework(ApiClient apiClient) {
        super(apiClient);
        this.batchV1Api = new BatchV1Api(apiClient);
    }

    // TODO: instead of void define a Result object that have to be merged with the run from the
    // caller.
    @Override
    public K8sBuildRunnable run(K8sBuildRunnable runnable) throws K8sFrameworkException {
        V1Job job = build(runnable);
        job = create(job);

        // Update runnable state..
        runnable.setState(State.RUNNING.name());

        return runnable;
    }

    @Override
    public K8sBuildRunnable stop(K8sBuildRunnable runnable) throws K8sFrameworkException {
        V1Job job = get(build(runnable));

        //stop by deleting
        delete(job);
        runnable.setState(State.STOPPED.name());

        return runnable;
    }

    @Override
    public K8sBuildRunnable delete(K8sBuildRunnable runnable) throws K8sFrameworkException {
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
    public V1Job build(K8sBuildRunnable runnable) throws K8sFrameworkException {
        // Log service execution initiation
        log.info("----------------- BUILD KUBERNETES CRON JOB ----------------");

        // Generate jobName and ContainerName
        String jobName = k8sBuilderHelper.getJobName(runnable.getRuntime(), runnable.getTask(), runnable.getId());

        //build labels
        Map<String, String> labels = buildLabels(runnable);

        // Create the Job metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(jobName).labels(labels);

        K8sJobRunnable k8sJobRunnable = K8sJobRunnable
                .builder()
                .id(runnable.getId())
                .args(runnable.getArgs())
                .affinity(runnable.getAffinity())
                .backoffLimit(runnable.getBackoffLimit())
                .command(runnable.getCommand())
                .envs(runnable.getEnvs())
                .image(runnable.getImage())
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

        V1Job job = jobFramework.build(k8sJobRunnable);

        //TODO Add here additional spec from build runnable
        Objects.requireNonNull(Objects.requireNonNull(job.getSpec()).getTemplate().getSpec())
                .initContainers(runnable.getInitContainers().stream().map(initContainer -> {

                            //TODO Add here additional spec from build runnable
                            return new V1Container()
                                    .name(initContainer.getName())
                                    .image(initContainer.getImage())
                                    .volumeMounts(
                                            initContainer
                                                    .getVolumes().stream()
                                                    .map(volumeMount -> new V1VolumeMount().name(volumeMount.name())
                                                            .mountPath(volumeMount.mountPath())).collect(Collectors.toList()))
                                    .command(initContainer.getCommand());

                        })
                        .collect(Collectors.toList()));

        List.of(new V1Container()
                .name("kaniko-init" + jobBuildConfig.getIdentifier())
                .image("alpine:latest")
                .volumeMounts(
                        List.of(
                                new V1VolumeMount().name("shared-dir").mountPath("/shared")
                        )
                )
                .command(
                        List.of(
                                "sh",
                                "-c",
                                "wget " +
                                        buildConfig.getSharedData() +
                                        " -O /shared/data.tgz && tar xf /shared/data.tgz -C /shared"
                        )
                )
        )
                            );


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
