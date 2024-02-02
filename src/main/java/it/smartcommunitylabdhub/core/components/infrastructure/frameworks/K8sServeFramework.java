package it.smartcommunitylabdhub.core.components.infrastructure.frameworks;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.*;
import it.smartcommunitylabdhub.core.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.core.components.fsm.StateMachine;
import it.smartcommunitylabdhub.core.components.fsm.enums.RunEvent;
import it.smartcommunitylabdhub.core.components.fsm.enums.RunState;
import it.smartcommunitylabdhub.core.components.fsm.types.RunStateMachine;
import it.smartcommunitylabdhub.core.components.infrastructure.factories.frameworks.Framework;
import it.smartcommunitylabdhub.core.components.infrastructure.runnables.K8sServeRunnable;
import it.smartcommunitylabdhub.core.components.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.core.components.pollers.PollingService;
import it.smartcommunitylabdhub.core.components.workflows.factory.WorkflowFactory;
import it.smartcommunitylabdhub.core.exceptions.CoreException;
import it.smartcommunitylabdhub.core.exceptions.StopPoller;
import it.smartcommunitylabdhub.core.models.builders.log.LogEntityBuilder;
import it.smartcommunitylabdhub.core.models.entities.log.Log;
import it.smartcommunitylabdhub.core.models.entities.log.metadata.LogMetadata;
import it.smartcommunitylabdhub.core.services.interfaces.LogService;
import it.smartcommunitylabdhub.core.services.interfaces.RunService;
import it.smartcommunitylabdhub.core.utils.ErrorList;
import it.smartcommunitylabdhub.core.utils.jackson.JacksonMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


//TODO: le operazioni di clean del deployment vanno fatte nel framework
@Slf4j
@FrameworkComponent(framework = "k8sserve")
public class K8sServeFramework implements Framework<K8sServeRunnable> {

    //     @Autowired
    //     BatchV1Api batchV1Api;

    @Autowired
    PollingService pollingService;
    @Autowired
    RunStateMachine runStateMachine;
    @Autowired
    LogEntityBuilder logEntityBuilder;
    @Autowired
    LogService logService;
    @Autowired
    RunService runService;
    @Autowired
    K8sBuilderHelper k8sBuilderHelper;
    private AppsV1Api appsV1Api;

    private CoreV1Api coreV1Api;

    @Value("${kubernetes.namespace}")
    private String namespace;

    public K8sServeFramework(ApiClient apiClient) {
        Assert.notNull(apiClient, "k8s api client is required");
        appsV1Api = new AppsV1Api(apiClient);
        coreV1Api = new CoreV1Api(apiClient);
    }

    // TODO: instead of void define a Result object that have to be merged with the run from the
    // caller.
    @Override
    public void execute(K8sServeRunnable runnable) throws CoreException {
        // FIXME: DELETE THIS IS ONLY FOR DEBUG
        String threadName = Thread.currentThread().getName();
        //String placeholder = "-" + RandomStringGenerator.generateRandomString(3);

        // Log service execution initiation
        log.info("----------------- PREPARE KUBERNETES Serve ----------------");

        // Generate deploymentName and ContainerName
        String deploymentName = getDeploymentName(
                runnable.getRuntime(),
                runnable.getTask(),
                runnable.getId()
        );

        String serviceName = getServiceName(
                runnable.getRuntime(),
                runnable.getTask(),
                runnable.getId()
        );

        String containerName = getContainerName(
                runnable.getRuntime(),
                runnable.getTask(),
                runnable.getId()
        );

        // Create labels for job
        Map<String, String> labels = Map.of(
                "app.kubernetes.io/instance", "dhcore-" + deploymentName,
                "app.kubernetes.io/version", "0.0.3",
                "app.kubernetes.io/component", "deployment",
                "app.kubernetes.io/part-of", "dhcore-k8sdeployment",
                "app.kubernetes.io/managed-by", "dhcore");


        // Prepare environment variables for the Kubernetes job
        List<V1EnvFromSource> envVarsFromSource = k8sBuilderHelper.getV1EnvFromSource();

        List<V1EnvVar> envVars = k8sBuilderHelper.getV1EnvVar();


        // Merge function specific envs
        runnable.getEnvs().forEach((key, value) -> envVars.add(
                new V1EnvVar().name(key).value(value)));


        // Create the Serve metadata
        V1ObjectMeta metadata = new V1ObjectMeta()
                .name(deploymentName)
                .labels(labels);

        // Build Container
        V1Container container = new V1Container()
                .name(containerName)
                .image(runnable.getImage())
                .imagePullPolicy("Always")
                .imagePullPolicy("IfNotPresent")
                .envFrom(envVarsFromSource)
                .env(envVars);

//        if present entry point set it
        Optional.ofNullable(runnable.getEntrypoint())
                .ifPresent(entrypoint -> container.command(getEntryPoint(runnable)));


        // Create a PodSpec for the container
        V1PodSpec podSpec = new V1PodSpec()
                .containers(Collections.singletonList(container))
                .restartPolicy("Always");

        // Create a PodTemplateSpec with the PodSpec
        V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec()
                .metadata(metadata)
                .spec(podSpec);

        // Create the JobSpec with the PodTemplateSpec
        V1DeploymentSpec deploymentSpec = new V1DeploymentSpec()
                // .completions(1)
                // .backoffLimit(6)    // is the default value
                .selector(new V1LabelSelector()
                        .matchLabels(labels))
                .template(podTemplateSpec);

        // Create the V1Serve object with metadata and JobSpec

        V1Deployment deployment = new V1Deployment()
                .metadata(metadata)
                .spec(deploymentSpec);


        // Create the V1 service
        V1ServicePort servicePort = new V1ServicePort()
                .port(8501)  // The port exposed by your deployment
                .targetPort(new IntOrString(8501))
                .protocol("TCP");

        V1ServiceSpec serviceSpec = new V1ServiceSpec()
                .type("NodePort")
                .addPortsItem(servicePort)
                .selector(labels);

        V1ObjectMeta serviceMetadata = new V1ObjectMeta().name(serviceName);

        V1Service service = new V1Service()
                .metadata(serviceMetadata)
                .spec(serviceSpec);


        try {

            V1Deployment createdServe = appsV1Api.createNamespacedDeployment(
                    namespace,
                    deployment,
                    null,
                    null,
                    null,
                    null
            );

            V1Service createdService = coreV1Api.createNamespacedService(
                    namespace,
                    service,
                    null,
                    null,
                    null,
                    null);

            log.info("Serve created: " + Objects.requireNonNull(createdServe.getMetadata()).getName());
        } catch (Exception e) {
            log.error("====== K8s FATAL ERROR =====");
            log.error(String.valueOf(e));
            // Handle exceptions here
            throw new CoreException(
                    ErrorList.RUN_JOB_ERROR.getValue(),
                    e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }


        // Initialize the run state machine considering current state and context
        StateMachine<RunState, RunEvent, Map<String, Object>> fsm = runStateMachine
                .create(RunState.valueOf(runnable.getState()),
                        Map.of("runId", runnable.getId()));


        // Log the initiation of Dbt Kubernetes Listener
        log.info("Dbt Kubernetes Listener [" + threadName + "] "
                + deploymentName
                + "@"
                + namespace);


        // Define a function with parameters
        Function<String, Function<String, Function<StateMachine<RunState, RunEvent, Map<String, Object>>, Void>>> checkServeStatus =
                dName -> cName -> fMachine -> {
                    try {

                        V1Deployment v1Deployment = appsV1Api.readNamespacedDeployment(dName, namespace, null);
                        V1DeploymentStatus v1DeploymentStatus = v1Deployment.getStatus();

                        assert v1DeploymentStatus != null;
                        Objects.requireNonNull(v1DeploymentStatus.getConditions()).forEach(
                                v -> log.info(v.getStatus())
                        );
//                // Check the Serve status
//                if (Objects.requireNonNull(v1ServeStatus).getReadyReplicas() != null
//                        && !fMachine.getCurrentState().equals(RunState.COMPLETED)) {
//
//
//                    // Serve has completed successfully
//                    log.info("Serve completed successfully.");
//                    // Update state machine and update runDTO
//                    fMachine.goToState(RunState.COMPLETED);
//                    Run runDTO = runService.getRun(runnable.getId());
//                    runDTO.getStatus().put("state", fsm.getCurrentState().name());
//                    runService.updateRun(runDTO, runDTO.getId());
//
//                    // Log pod status
//                    logPod(dName, cName, namespace, runnable);
//                    // Delete job and pod
//                    //deleteAssociatedPodAndJob(dName, namespace, runnable);
//
//                } else if (Objects.requireNonNull(v1ServeStatus).getFailed() != null) {
//                    // Serve has failed delete job and pod
//                    //deleteAssociatedPodAndJob(dName, namespace, runnable);
//
//                } else if (v1ServeStatus.getActive() != null && v1ServeStatus.getActive() > 0) {
//                    if (!fMachine.getCurrentState().equals(RunState.RUNNING)) {
//                        fMachine.goToState(RunState.READY);
//                        fMachine.goToState(RunState.RUNNING);
//                    }
//                    log.warn("Serve is running...");
//                    logPod(dName, cName, namespace, runnable);
//                } else {
//                    String v1JobStatusString = JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(v1ServeStatus);
//                    log.warn("Serve is in an unknown state : " + v1JobStatusString);
//                    writeLog(runnable, v1JobStatusString);
//                }

                    } catch (ApiException | CoreException e) {
                        deleteAssociatedPodAndJob(dName, namespace, runnable);
                        throw new StopPoller(e.getMessage());
                    }

                    return null;
                };

        // Using the step method with explicit arguments
        pollingService.createPoller(runnable.getId(), List.of(
                WorkflowFactory.builder().step((Function<?, ?>) i ->
                        checkServeStatus.apply(deploymentName).apply(containerName).apply(fsm)
                ).build()
        ), 1, true, false);

        // Start job poller
        pollingService.startOne(runnable.getId());
    }

    @Override
    public void stop(K8sServeRunnable runnable) {

    }

    @Override
    public String status(K8sServeRunnable runnable) {
        return null;
    }

    // Concat command with arguments
    private List<String> getEntryPoint(K8sServeRunnable runnable) {
        return Stream.concat(
                Stream.of(Optional.ofNullable(runnable.getEntrypoint()).orElse("")),
                Optional.ofNullable(runnable.getArgs()).stream().flatMap(Arrays::stream)
        ).collect(Collectors.toList());
    }

    // Generate and return deployment name
    private String getDeploymentName(String runtime, String task, String id) {
        return "j" + "-" + runtime + "-" + task + "-" + id;
    }

    // Generate and return service name
    private String getServiceName(String runtime, String task, String id) {
        return "s" + "-" + runtime + "-" + task + "-" + id;
    }

    // Generate and return container name
    private String getContainerName(String runtime, String task, String id) {
        return "c" + "-" + runtime + "-" + task + "-" + id;
    }


    private void writeLog(K8sServeRunnable runnable, String log) {

        LogMetadata logMetadata = new LogMetadata();
        logMetadata.setProject(runnable.getProject());
        logMetadata.setRun(runnable.getId());
        Log logDTO = Log.builder()
                .body(Map.of("content", log))
                .metadata(logMetadata)
                .build();
        logService.createLog(logDTO);
    }

    /**
     * Logging pod
     *
     * @param jobName  the name of the Serve
     * @param runnable the runnable Type in this case K8SJobRunnable
     */
    private void logPod(String jobName, String cName, String namespace, K8sServeRunnable runnable) {
        try {

            // Retrieve and print the logs of the associated Pod
            V1PodList v1PodList = coreV1Api.listNamespacedPod(
                    namespace, null,
                    null, null,
                    null, null,
                    null, null,
                    null, null,
                    null, null);

            for (V1Pod pod : v1PodList.getItems()) {
                if (pod.getMetadata() != null && pod.getMetadata().getName() != null) {
                    if (pod.getMetadata().getName().startsWith(jobName)) {
                        String podName = pod.getMetadata().getName();
                        String logs = coreV1Api.readNamespacedPodLog(podName, namespace, cName,
                                false, null,
                                null, null,
                                null, null,
                                null, null);


                        log.info("Logs for Pod: " + podName);
                        log.info("Log is: " + logs);
                        if (logs != null)
                            writeLog(runnable, logs);
                    }
                }
            }
        } catch (ApiException e) {
            log.error(e.getResponseBody());
            //throw new RuntimeException(e);
        }
    }

    /**
     * Delete job
     *
     * @param jobName  the name of the Serve
     * @param runnable the runnable Type in this case K8SJobRunnable
     */
    private void deleteAssociatedPodAndJob(String jobName, String namespace, K8sServeRunnable runnable) {
        // Delete the Pod associated with the Serve
        try {
            V1PodList v1PodList = coreV1Api.listNamespacedPod(
                    namespace, null,
                    null, null,
                    null, null,
                    null, null,
                    null, null,
                    null, null);


            for (V1Pod pod : v1PodList.getItems()) {
                if (pod.getMetadata() != null && pod.getMetadata().getName() != null) {
                    if (pod.getMetadata().getName().startsWith(jobName)) {
                        String podName = pod.getMetadata().getName();

                        // Delete the Pod
                        V1Pod v1Pod = coreV1Api.deleteNamespacedPod(podName, namespace, null,
                                null, null,
                                null, null,
                                null);
                        log.info("Pod deleted: " + podName);

                        try {
                            writeLog(runnable, JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(v1Pod.getStatus()));
                        } catch (JsonProcessingException e) {
                            log.error(e.toString());
                        }

                        // // Delete the Serve
                        // V1Status deleteStatus = batchV1Api.deleteNamespacedJob(
                        //         jobName, "default", null,
                        //         null, null, null,
                        //         null, null);

                        // try {
                        //     writeLog(runnable, JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(deleteStatus));
                        // } catch (JsonProcessingException e) {
                        //     log.error(e.toString());
                        // }
                        log.info("Serve deleted: " + jobName);
                    }
                }
            }
            throw new StopPoller("POLLER STOP SUCCESSFULLY");
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
    }

}
