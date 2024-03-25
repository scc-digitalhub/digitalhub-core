package it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.*;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import jakarta.validation.constraints.NotNull;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

@Slf4j
@FrameworkComponent(framework = K8sBuildFramework.FRAMEWORK)
public class K8sBuildFramework extends K8sBaseFramework<K8sJobRunnable, V1Job> {

    public static final String FRAMEWORK = "k8sbuild";

    public static final int DEADLINE_SECONDS = 3600 * 24 * 3; //3 days

    private final BatchV1Api batchV1Api;

    private int activeDeadlineSeconds = DEADLINE_SECONDS;

    public K8sBuildFramework(ApiClient apiClient) {
        super(apiClient);
        batchV1Api = new BatchV1Api(apiClient);
    }

    public void setActiveDeadlineSeconds(int activeDeadlineSeconds) {
        Assert.isTrue(activeDeadlineSeconds > 300, "Minimum deadline seconds is 300");
        this.activeDeadlineSeconds = activeDeadlineSeconds;
    }

    // TODO: instead of void define a Result object that have to be merged with the run from the
    // caller.
    @Override
    public K8sJobRunnable run(K8sJobRunnable runnable) throws K8sFrameworkException {
        V1Job job = build(runnable);
        job = create(job);

        // Update runnable state..
        runnable.setState(State.RUNNING.name());

        return runnable;
    }

    @Override
    public K8sJobRunnable stop(K8sJobRunnable runnable) throws K8sFrameworkException {
        V1Job job = get(build(runnable));

        //stop by deleting
        delete(job);
        runnable.setState(State.STOPPED.name());

        return runnable;
    }

    @Override
    public K8sJobRunnable delete(K8sJobRunnable runnable) throws K8sFrameworkException {
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

    public V1Job build(K8sJobRunnable runnable) throws K8sFrameworkException {
        // Log service execution initiation
        log.info("----------------- BUILD KUBERNETES JOB ----------------");

        // Generate jobName and ContainerName
        String jobName = k8sBuilderHelper.getJobName(runnable.getRuntime(), runnable.getTask(), runnable.getId());
        String containerName = k8sBuilderHelper.getContainerName(
                runnable.getRuntime(),
                runnable.getTask(),
                runnable.getId()
        );

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

        // Build Container
        V1Container container = new V1Container()
                .name(containerName)
                .image(runnable.getImage())
                .imagePullPolicy("Always")
                .imagePullPolicy("IfNotPresent")
                .command(command)
                .args(args)
                .resources(resources)
                .volumeMounts(volumeMounts)
                .envFrom(envFrom)
                .env(env);

        // Create a PodSpec for the container
        V1PodSpec podSpec = new V1PodSpec()
                .containers(Collections.singletonList(container))
                .nodeSelector(buildNodeSelector(runnable))
                .affinity(runnable.getAffinity())
                .tolerations(buildTolerations(runnable))
                .volumes(volumes)
                .restartPolicy("Never");

        // Create a PodTemplateSpec with the PodSpec
        V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec().metadata(metadata).spec(podSpec);

        int backoffLimit = Optional.ofNullable(runnable.getBackoffLimit()).orElse(3).intValue();

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

    public V1Job apply(@NotNull V1Job job) throws K8sFrameworkException {
        //nothing to do
        return job;
    }

    public V1Job get(@NotNull V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            // Log service execution initiation
            log.info("----------------- GET KUBERNETES JOB ----------------");

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
            log.info("----------------- RUN KUBERNETES JOB ----------------");

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
            log.info("----------------- DELETE KUBERNETES JOB ----------------");

            batchV1Api.deleteNamespacedJob(job.getMetadata().getName(), namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getResponseBody());
        }
    }
    //TODO delete refactor method below

    // private void writeLog(K8sJobRunnable runnable, String log) {
    //     LogMetadata logMetadata = new LogMetadata();
    //     logMetadata.setProject(runnable.getProject());
    //     logMetadata.setRun(runnable.getId());
    //     Log logDTO = Log.builder().body(Map.of("content", log)).metadata(logMetadata.toMap()).build();
    //     logService.createLog(logDTO);
    // }

    // /**
    //  * Logging pod
    //  *
    //  * @param jobName  the name of the Job
    //  * @param runnable the runnable Type in this case K8SJobRunnable
    //  */
    // private void logPod(String jobName, String cName, String namespace, K8sJobRunnable runnable) {
    //     try {
    //         // Retrieve and print the logs of the associated Pod
    //         V1PodList v1PodList = coreV1Api.listNamespacedPod(
    //             namespace,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null
    //         );

    //         for (V1Pod pod : v1PodList.getItems()) {
    //             if (pod.getMetadata() != null && pod.getMetadata().getName() != null) {
    //                 if (pod.getMetadata().getName().startsWith(jobName)) {
    //                     String podName = pod.getMetadata().getName();
    //                     String logs = coreV1Api.readNamespacedPodLog(
    //                         podName,
    //                         namespace,
    //                         cName,
    //                         false,
    //                         null,
    //                         null,
    //                         null,
    //                         null,
    //                         null,
    //                         null,
    //                         null
    //                     );

    //                     log.info("Logs for Pod: " + podName);
    //                     log.info("Log is: " + logs);
    //                     if (logs != null) writeLog(runnable, logs);
    //                 }
    //             }
    //         }
    //     } catch (ApiException e) {
    //         log.error(e.getResponseBody());
    //         //throw new RuntimeException(e);
    //     }
    // }

    // /**
    //  * Delete job
    //  *
    //  * @param jobName  the name of the Job
    //  * @param runnable the runnable Type in this case K8SJobRunnable
    //  */
    // private void deleteAssociatedPodAndJob(String jobName, String namespace, K8sJobRunnable runnable) {
    //     // Delete the Pod associated with the Job
    //     try {
    //         V1PodList v1PodList = coreV1Api.listNamespacedPod(
    //             namespace,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null,
    //             null
    //         );

    //         for (V1Pod pod : v1PodList.getItems()) {
    //             if (pod.getMetadata() != null && pod.getMetadata().getName() != null) {
    //                 if (pod.getMetadata().getName().startsWith(jobName)) {
    //                     String podName = pod.getMetadata().getName();

    //                     // Delete the Pod
    //                     V1Pod v1Pod = coreV1Api.deleteNamespacedPod(
    //                         podName,
    //                         namespace,
    //                         null,
    //                         null,
    //                         null,
    //                         null,
    //                         null,
    //                         null
    //                     );
    //                     log.info("Pod deleted: " + podName);

    //                     try {
    //                         writeLog(
    //                             runnable,
    //                             JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(v1Pod.getStatus())
    //                         );
    //                     } catch (JsonProcessingException e) {
    //                         log.error(e.toString());
    //                     }

    //                     // Delete the Job
    //                     V1Status deleteStatus = batchV1Api.deleteNamespacedJob(
    //                         jobName,
    //                         "default",
    //                         null,
    //                         null,
    //                         null,
    //                         null,
    //                         null,
    //                         null
    //                     );

    //                     try {
    //                         writeLog(runnable, JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(deleteStatus));
    //                     } catch (JsonProcessingException e) {
    //                         log.error(e.toString());
    //                     }
    //                     log.info("Job deleted: " + jobName);
    //                 }
    //             }
    //         }
    //         throw new StopPoller("POLLER STOP SUCCESSFULLY");
    //     } catch (ApiException e) {
    //         throw new RuntimeException(e);
    //     }
    // }
}
