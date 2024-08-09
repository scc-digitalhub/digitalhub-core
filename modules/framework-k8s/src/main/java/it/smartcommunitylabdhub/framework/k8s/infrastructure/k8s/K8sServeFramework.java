package it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1Deployment;
import io.kubernetes.client.openapi.models.V1DeploymentSpec;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1LabelSelector;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1Service;
import io.kubernetes.client.openapi.models.V1ServicePort;
import io.kubernetes.client.openapi.models.V1ServiceSpec;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sServeRunnable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;

@Slf4j
@FrameworkComponent(framework = K8sServeFramework.FRAMEWORK)
public class K8sServeFramework extends K8sBaseFramework<K8sServeRunnable, V1Service> {

    public static final String FRAMEWORK = "k8sserve";
    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    //TODO refactor usage of framework: should split framework from infrastructure!
    private final K8sDeploymentFramework deploymentFramework;

    private String initImage;

    public K8sServeFramework(ApiClient apiClient) {
        super(apiClient);
        deploymentFramework = new K8sDeploymentFramework(apiClient);
    }

    @Autowired
    public void setInitImage(@Value("${kubernetes.init-image}") String initImage) {
        this.initImage = initImage;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();

        //configure dependant framework
        this.deploymentFramework.setApplicationProperties(applicationProperties);
        this.deploymentFramework.setCollectLogs(collectLogs);
        this.deploymentFramework.setCollectMetrics(collectMetrics);
        this.deploymentFramework.setCollectResults(collectResults);
        this.deploymentFramework.setCpuResourceDefinition(cpuResourceDefinition);
        this.deploymentFramework.setDisableRoot(disableRoot);
        this.deploymentFramework.setImagePullPolicy(imagePullPolicy);
        this.deploymentFramework.setInitImage(initImage);
        this.deploymentFramework.setMemResourceDefinition(memResourceDefinition);
        this.deploymentFramework.setNamespace(namespace);
        this.deploymentFramework.setRegistrySecret(registrySecret);
        this.deploymentFramework.setTemplates(templateKeys);
        this.deploymentFramework.setVersion(version);
        this.deploymentFramework.setK8sBuilderHelper(k8sBuilderHelper);
        this.deploymentFramework.setK8sSecretHelper(k8sSecretHelper);

        this.deploymentFramework.afterPropertiesSet();
    }

    @Override
    public K8sServeRunnable run(K8sServeRunnable runnable) throws K8sFrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        Map<String, KubernetesObject> results = new HashMap<>();
        // Create a deployment from a Deployment+Service
        V1Deployment deployment = buildDeployment(runnable);

        //secrets
        V1Secret secret = buildRunSecret(runnable);
        if (secret != null) {
            storeRunSecret(secret);
            results.put("secret", secret);
        }

        try {
            V1ConfigMap initConfigMap = buildInitConfigMap(runnable);
            if (initConfigMap != null) {
                log.info("create initConfigMap for {}", String.valueOf(initConfigMap.getMetadata().getName()));
                coreV1Api.createNamespacedConfigMap(namespace, initConfigMap, null, null, null, null);
                results.put("configMap", initConfigMap);
            }
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage());
        }

        log.info("create deployment for {}", String.valueOf(deployment.getMetadata().getName()));
        deployment = deploymentFramework.create(deployment);
        results.put("deployment", deployment);

        //create the service
        V1Service service = build(runnable);
        log.info("create service for {}", String.valueOf(service.getMetadata().getName()));
        service = create(service);
        results.put("service", service);

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

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sServeRunnable delete(K8sServeRunnable runnable) throws K8sFrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        V1Deployment deployment;
        try {
            // Retrieve the deployment
            deployment = deploymentFramework.get(buildDeployment(runnable));
        } catch (K8sFrameworkException e) {
            deployment = null;
        }

        if (deployment != null) {
            // Delete the deployment
            log.info("delete deployment for {}", String.valueOf(deployment.getMetadata().getName()));

            deploymentFramework.delete(deployment);
        }

        //secrets
        cleanRunSecret(runnable);

        //init config map
        try {
            String configMapName = "init-config-map-" + runnable.getId();
            V1ConfigMap initConfigMap = coreV1Api.readNamespacedConfigMap(configMapName, namespace, null);
            if (initConfigMap != null) {
                coreV1Api.deleteNamespacedConfigMap(configMapName, namespace, null, null, null, null, null, null);
            }
        } catch (ApiException | NullPointerException e) {
            //ignore, not existing or error
        }

        V1Service service;
        try {
            // Retrieve the service
            service = get(build(runnable));
        } catch (K8sFrameworkException e) {
            runnable.setState(State.DELETED.name());
            return runnable;
        }

        //Delete the service
        log.info("delete service for {}", String.valueOf(service.getMetadata().getName()));
        delete(service);

        if (!"keep".equals(collectResults)) {
            //update results
            try {
                runnable.setResults(Collections.emptyMap());
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }
        }

        //update state
        runnable.setState(State.DELETED.name());

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sServeRunnable stop(K8sServeRunnable runnable) throws K8sFrameworkException {
        log.info("stop for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        //stop deployment and delete service
        V1Deployment deployment = deploymentFramework.get(buildDeployment(runnable));
        if (deployment != null) {
            log.info("stop deployment for {}", String.valueOf(deployment.getMetadata().getName()));
            //stop by setting replicas to 0
            deployment.getSpec().setReplicas(0);
            deploymentFramework.apply(deployment);
        }

        V1Service service = get(build(runnable));
        log.info("delete service for {}", String.valueOf(service.getMetadata().getName()));
        delete(service);

        if (!"disable".equals(collectResults)) {
            //update results
            try {
                runnable.setResults(
                    Map.of(
                        "deployment",
                        deployment != null ? mapper.convertValue(deployment, typeRef) : null,
                        "service",
                        service != null ? mapper.convertValue(service, typeRef) : null
                    )
                );
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }
        }

        //update state
        runnable.setState(State.STOPPED.name());

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public V1Service build(K8sServeRunnable runnable) throws K8sFrameworkException {
        log.debug("build for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        // Generate deploymentName and ContainerName
        String serviceName = k8sBuilderHelper.getServiceName(
            runnable.getRuntime(),
            runnable.getTask(),
            runnable.getId()
        );

        log.debug("build k8s service for {}", serviceName);

        Map<String, String> labels = buildLabels(runnable);
        // Create the V1 service
        // TODO: the service definition contains a list of ports. service: { ports:[xxx,xxx,,xxx],.....}

        if (runnable.getServicePorts() == null || runnable.getServicePorts().isEmpty()) {
            log.warn("no service ports specified for {}", serviceName);
        }

        //build ports
        List<V1ServicePort> ports = Optional
            .ofNullable(runnable.getServicePorts())
            .map(list ->
                list
                    .stream()
                    .filter(p -> p.port() != null && p.targetPort() != null)
                    .map(p ->
                        new V1ServicePort().port(p.port()).targetPort(new IntOrString(p.targetPort())).protocol("TCP").name("port" + p.port())
                    )
                    .collect(Collectors.toList())
            )
            .orElse(null);

        // service type (ClusterIP or NodePort)
        String type = Optional.ofNullable(runnable.getServiceType().name()).orElse("NodePort");

        //build service spec
        V1ServiceSpec serviceSpec = new V1ServiceSpec().type(type).ports(ports).selector(labels);
        V1ObjectMeta serviceMetadata = new V1ObjectMeta().name(serviceName).labels(labels);

        return new V1Service().metadata(serviceMetadata).spec(serviceSpec);
    }

    /*
     * K8s
     */
    @Override
    public V1Service apply(@NotNull V1Service service) throws K8sFrameworkException {
        Assert.notNull(service.getMetadata(), "metadata can not be null");

        try {
            String serviceName = service.getMetadata().getName();
            log.debug("update k8s service for {}", serviceName);

            return coreV1Api.replaceNamespacedService(serviceName, namespace, service, null, null, null, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage());
        }
    }

    @Override
    public V1Service get(@NotNull V1Service service) throws K8sFrameworkException {
        Assert.notNull(service.getMetadata(), "metadata can not be null");

        try {
            String serviceName = service.getMetadata().getName();
            log.debug("get k8s service for {}", serviceName);

            return coreV1Api.readNamespacedService(serviceName, namespace, null);
        } catch (ApiException e) {
            log.info("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getResponseBody());
        }
    }

    @Override
    public V1Service create(V1Service service) throws K8sFrameworkException {
        Assert.notNull(service.getMetadata(), "metadata can not be null");

        try {
            String serviceName = service.getMetadata().getName();
            log.debug("create k8s service for {}", serviceName);

            return coreV1Api.createNamespacedService(namespace, service, null, null, null, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage());
        }
    }

    @Override
    public void delete(V1Service service) throws K8sFrameworkException {
        // Delete also the Service
        try {
            Assert.notNull(service.getMetadata(), "metadata can not be null");

            String serviceName = service.getMetadata().getName();
            log.debug("delete k8s service for {}", serviceName);

            coreV1Api.deleteNamespacedService(serviceName, namespace, null, null, null, null, null, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage());
        }
    }

    public V1Deployment buildDeployment(K8sServeRunnable runnable) throws K8sFrameworkException {
        log.debug("build deployment for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        // Generate deploymentName and ContainerName
        String deploymentName = k8sBuilderHelper.getDeploymentName(
            runnable.getRuntime(),
            runnable.getTask(),
            runnable.getId()
        );
        String containerName = k8sBuilderHelper.getContainerName(
            runnable.getRuntime(),
            runnable.getTask(),
            runnable.getId()
        );

        log.debug("build k8s deployment for {}", deploymentName);

        // Create labels for job
        Map<String, String> labels = buildLabels(runnable);

        // Create the Deployment metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(deploymentName).labels(labels);

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
        if (runnable.getContextRefs() != null || runnable.getContextSources() != null) {
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
            .restartPolicy("Always")
            .imagePullSecrets(buildImagePullSecrets(runnable));

        //check if context build is required
        if (runnable.getContextRefs() != null || runnable.getContextSources() != null) {
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

        int replicas = Optional.ofNullable(runnable.getReplicas()).orElse(1);

        // Create the JobSpec with the PodTemplateSpec
        V1DeploymentSpec deploymentSpec = new V1DeploymentSpec()
            .replicas(replicas)
            .selector(new V1LabelSelector().matchLabels(labels))
            .template(podTemplateSpec);

        // Create the V1Deployment object with metadata and JobSpec
        return new V1Deployment().metadata(metadata).spec(deploymentSpec);
    }
}
