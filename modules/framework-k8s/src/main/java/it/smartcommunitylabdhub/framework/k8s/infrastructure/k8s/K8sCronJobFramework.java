package it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.*;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCronJobRunnable;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@FrameworkComponent(framework = K8sCronJobFramework.FRAMEWORK)
public class K8sCronJobFramework extends K8sBaseFramework<K8sCronJobRunnable, V1CronJob> {

    public static final String FRAMEWORK = "k8scronjob";

    private final BatchV1Api batchV1Api;

    @Autowired
    private K8sJobFramework jobFramework;

    public K8sCronJobFramework(ApiClient apiClient) {
        super(apiClient);
        this.batchV1Api = new BatchV1Api(apiClient);
    }

    // TODO: instead of void define a Result object that have to be merged with the run from the
    // caller.
    @Override
    public K8sCronJobRunnable execute(K8sCronJobRunnable runnable) throws K8sFrameworkException {
        return runnable;
    }

    @Override
    public V1CronJob build(K8sCronJobRunnable runnable) throws K8sFrameworkException {
        // Log service execution initiation
        log.info("----------------- BUILD KUBERNETES JOB ----------------");

        // Generate jobName and ContainerName
        String jobName = k8sBuilderHelper.getJobName(runnable.getRuntime(), runnable.getTask(), runnable.getId());

        //build labels
        Map<String, String> labels = buildLabels(runnable);

        // Create the Job metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(jobName).labels(labels);

        if (!StringUtils.hasText(runnable.getSchedule())) {
            throw new K8sFrameworkException("missing or invalid schedule in spec");
        }

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
        V1CronJobSpec cronJobSpec = new V1CronJobSpec()
            .schedule(runnable.getSchedule())
            .jobTemplate(new V1JobTemplateSpec().spec(job.getSpec()));

        return new V1CronJob().metadata(metadata).spec(cronJobSpec);
    }

    @Override
    public V1CronJob apply(@NotNull V1CronJob job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            // Log service execution initiation
            log.info("----------------- RUN KUBERNETES JOB ----------------");

            //dispatch job via api
            V1CronJob createdJob = batchV1Api.createNamespacedCronJob(namespace, job, null, null, null, null);
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
    public V1CronJob get(@NotNull V1CronJob job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            // Log service execution initiation
            log.info("----------------- GET KUBERNETES JOB ----------------");

            return batchV1Api.readNamespacedCronJob(job.getMetadata().getName(), namespace, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage());
        }
    }
}