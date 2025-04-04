package it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
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
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sDeploymentRunnable;
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
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
@FrameworkComponent(framework = K8sDeploymentFramework.FRAMEWORK)
public class K8sDeploymentFramework extends K8sBaseFramework<K8sDeploymentRunnable, V1Deployment> {

    public static final String FRAMEWORK = "k8sdeployment";

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};
    private final AppsV1Api appsV1Api;

    private String initImage;
    private List<String> initCommand = null;

    public K8sDeploymentFramework(ApiClient apiClient) {
        super(apiClient);
        appsV1Api = new AppsV1Api(apiClient);
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
        this.templates = loadTemplates(K8sDeploymentRunnable.class);

        //build default shared volume definition for context building
        if (k8sProperties.getSharedVolume() == null) {
            k8sProperties.setSharedVolume(
                new CoreVolume(CoreVolume.VolumeType.empty_dir, "/shared", "shared-dir", Map.of("sizeLimit", "100Mi"))
            );
        }
    }

    @Override
    public K8sDeploymentRunnable run(K8sDeploymentRunnable runnable) throws K8sFrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        Map<String, KubernetesObject> results = new HashMap<>();
        V1Deployment deployment = build(runnable);

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
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }

        log.info("create deployment for {}", String.valueOf(deployment.getMetadata().getName()));
        deployment = create(deployment);
        results.put("deployment", deployment);

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

        if (deployment != null) {
            runnable.setMessage(String.format("deployment %s created", deployment.getMetadata().getName()));
        }

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sDeploymentRunnable delete(K8sDeploymentRunnable runnable) throws K8sFrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        List<String> messages = new ArrayList<>();

        V1Deployment deployment;
        try {
            deployment = get(build(runnable));
        } catch (K8sFrameworkException e) {
            runnable.setState(State.DELETED.name());
            return runnable;
        }

        log.info("delete deployment for {}", String.valueOf(deployment.getMetadata().getName()));
        delete(deployment);
        messages.add(String.format("deployment %s deleted", deployment.getMetadata().getName()));

        //secrets
        cleanRunSecret(runnable);

        //init config map
        try {
            String configMapName = "init-config-map-" + runnable.getId();
            V1ConfigMap initConfigMap = coreV1Api.readNamespacedConfigMap(configMapName, namespace, null);
            if (initConfigMap != null) {
                coreV1Api.deleteNamespacedConfigMap(configMapName, namespace, null, null, null, null, null, null);
                messages.add(String.format("configMap %s deleted", configMapName));
            }
        } catch (ApiException | NullPointerException e) {
            //ignore, not existing or error
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
        runnable.setState(State.DELETED.name());
        runnable.setMessage(String.join(", ", messages));

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sDeploymentRunnable stop(K8sDeploymentRunnable runnable) throws K8sFrameworkException {
        log.info("stop for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        V1Deployment deployment = get(build(runnable));

        //stop by setting replicas to 0
        deployment.getSpec().setReplicas(0);
        deployment = apply(deployment);
        runnable.setMessage(String.format("deployment %s replicas set to 0", deployment.getMetadata().getName()));

        //update results
        if (!"disable".equals(collectResults)) {
            try {
                Map<String, Serializable> results = MapUtils.mergeMultipleMaps(
                    runnable.getResults(),
                    Map.of("deployment", mapper.convertValue(deployment, typeRef))
                );

                //clear pods if present
                results.remove("pods");

                //store
                runnable.setResults(results);
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
    public K8sDeploymentRunnable resume(K8sDeploymentRunnable runnable) throws K8sFrameworkException {
        log.info("resume for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        V1Deployment deployment = get(build(runnable));

        //resume by setting replicas as per spec
        int replicas = Optional.ofNullable(runnable.getReplicas()).orElse(1);
        deployment.getSpec().setReplicas(replicas);
        deployment = apply(deployment);
        runnable.setMessage(
            String.format("deployment %s replicas set to %d", deployment.getMetadata().getName(), replicas)
        );

        //update results
        if (!"disable".equals(collectResults)) {
            try {
                Map<String, Serializable> results = MapUtils.mergeMultipleMaps(
                    runnable.getResults(),
                    Map.of("deployment", mapper.convertValue(deployment, typeRef))
                );

                //clear pods if present
                results.remove("pods");

                //store
                runnable.setResults(results);
            } catch (IllegalArgumentException e) {
                log.error("error reading k8s results: {}", e.getMessage());
            }
        }

        //update state
        runnable.setState(State.RUNNING.name());

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    /*
     * K8s
     */
    public V1Deployment build(K8sDeploymentRunnable runnable) throws K8sFrameworkException {
        log.debug("build for {}", runnable.getId());
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

        //check template
        K8sTemplate<K8sDeploymentRunnable> template = null;
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //get template
            template = templates.get(runnable.getTemplate());
        }

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
            .map(K8sTemplate::getDeployment)
            .map(V1Deployment::getSpec)
            .map(V1DeploymentSpec::getTemplate)
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
            .restartPolicy("Always")
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
                .volumeMounts(volumeMounts)
                .resources(resources)
                .env(env)
                .envFrom(envFrom)
                .command(initCommand);

            podSpec.setInitContainers(Collections.singletonList(initContainer));
        }

        // Create a PodTemplateSpec with the PodSpec
        V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec().metadata(metadata).spec(podSpec);

        int replicas = Optional.ofNullable(runnable.getReplicas()).orElse(1);
        if (template != null && template.getProfile().getReplicas() != null) {
            //override with template
            replicas = template.getProfile().getReplicas().intValue();
        }

        //sanity check: replicas should be > 0
        if (replicas <= 0) {
            throw new K8sFrameworkException("replicas should be > 0");
        }

        // Create the deploymentSpec with the PodTemplateSpec, leveraging template
        V1DeploymentSpec deploymentSpec = Optional
            .ofNullable(template)
            .map(K8sTemplate::getDeployment)
            .map(V1Deployment::getSpec)
            .orElse(new V1DeploymentSpec());

        deploymentSpec.replicas(replicas).selector(new V1LabelSelector().matchLabels(labels)).template(podTemplateSpec);

        // Create the V1Deployment object with metadata and JobSpec
        return new V1Deployment().metadata(metadata).spec(deploymentSpec);
    }

    /*
     * K8s
     */
    public V1Deployment apply(@NotNull V1Deployment deployment) throws K8sFrameworkException {
        Assert.notNull(deployment.getMetadata(), "metadata can not be null");
        Assert.notNull(deployment.getSpec(), "spec can not be null");

        try {
            String deploymentName = deployment.getMetadata().getName();
            log.debug("update k8s deployment for {}", deploymentName);

            return appsV1Api.replaceNamespacedDeployment(deploymentName, namespace, deployment, null, null, null, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    public V1Deployment get(@NotNull V1Deployment deployment) throws K8sFrameworkException {
        Assert.notNull(deployment.getMetadata(), "metadata can not be null");

        try {
            String deploymentName = deployment.getMetadata().getName();
            log.debug("get k8s deployment for {}", deploymentName);

            return appsV1Api.readNamespacedDeployment(deploymentName, namespace, null);
        } catch (ApiException e) {
            log.info("Error with k8s: {}", e.getResponseBody());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    public V1Deployment create(V1Deployment deployment) throws K8sFrameworkException {
        Assert.notNull(deployment.getMetadata(), "metadata can not be null");
        try {
            String deploymentName = deployment.getMetadata().getName();
            log.debug("create k8s deployment for {}", deploymentName);

            return appsV1Api.createNamespacedDeployment(namespace, deployment, null, null, null, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    public void delete(@NotNull V1Deployment deployment) throws K8sFrameworkException {
        Assert.notNull(deployment.getMetadata(), "metadata can not be null");
        try {
            String deploymentName = deployment.getMetadata().getName();
            log.debug("delete k8s deployment for {}", deploymentName);

            appsV1Api.deleteNamespacedDeployment(deploymentName, namespace, null, null, null, null, "Foreground", null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isTraceEnabled()) {
                log.trace("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }
}
