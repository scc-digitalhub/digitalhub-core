package it.smartcommunitylabdhub.framework.kaniko.infrastructure.k8s;

import com.fasterxml.jackson.core.type.TypeReference;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.BatchV1Api;
import io.kubernetes.client.openapi.models.V1ConfigMap;
import io.kubernetes.client.openapi.models.V1ConfigMapVolumeSource;
import io.kubernetes.client.openapi.models.V1Container;
import io.kubernetes.client.openapi.models.V1EnvFromSource;
import io.kubernetes.client.openapi.models.V1EnvVar;
import io.kubernetes.client.openapi.models.V1Job;
import io.kubernetes.client.openapi.models.V1JobSpec;
import io.kubernetes.client.openapi.models.V1KeyToPath;
import io.kubernetes.client.openapi.models.V1ObjectMeta;
import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1PodTemplateSpec;
import io.kubernetes.client.openapi.models.V1ResourceRequirements;
import io.kubernetes.client.openapi.models.V1Secret;
import io.kubernetes.client.openapi.models.V1SecretVolumeSource;
import io.kubernetes.client.openapi.models.V1Volume;
import io.kubernetes.client.openapi.models.V1VolumeMount;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.FrameworkComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.utils.MapUtils;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sBaseFramework;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.model.K8sTemplate;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sKanikoRunnable;
import jakarta.validation.constraints.NotNull;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
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
@FrameworkComponent(framework = K8sKanikoFramework.FRAMEWORK)
public class K8sKanikoFramework extends K8sBaseFramework<K8sKanikoRunnable, V1Job> {

    public static final String FRAMEWORK = "k8sbuild";
    public static final int DEADLINE_SECONDS = 3600 * 24 * 3; //3 days
    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    private final BatchV1Api batchV1Api;

    private int activeDeadlineSeconds = DEADLINE_SECONDS;

    @Value("${kaniko.image}")
    private String kanikoImage;

    private String initImage;
    private List<String> initCommand = null;

    @Value("${kaniko.image-prefix}")
    private String imagePrefix;

    @Value("${kaniko.image-registry}")
    private String imageRegistry;

    @Value("${kaniko.secret}")
    private String kanikoSecret;

    @Value("${kaniko.args}")
    private List<String> kanikoArgs;

    public K8sKanikoFramework(ApiClient apiClient) {
        super(apiClient);
        this.batchV1Api = new BatchV1Api(apiClient);
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
        this.templates = loadTemplates(K8sKanikoRunnable.class);

        //build default shared volume definition for context building
        if (k8sProperties.getSharedVolume() == null) {
            k8sProperties.setSharedVolume(
                new CoreVolume(CoreVolume.VolumeType.empty_dir, "/shared", "shared-dir", Map.of("sizeLimit", "100Mi"))
            );
        }
    }

    @Override
    public K8sKanikoRunnable run(K8sKanikoRunnable runnable) throws K8sFrameworkException {
        log.info("run for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        Map<String, KubernetesObject> results = new HashMap<>();

        //create job
        V1Job job = build(runnable);

        //secrets
        V1Secret secret = buildRunSecret(runnable);
        if (secret != null) {
            storeRunSecret(secret);
            //clear data before storing
            results.put("secret", secret.stringData(Collections.emptyMap()).data(Collections.emptyMap()));
        }

        //build and create configMap
        try {
            // Generate Config map
            Optional<List<ContextRef>> contextRefsOpt = Optional.ofNullable(runnable.getContextRefs());
            Optional<List<ContextSource>> contextSourcesOpt = Optional.ofNullable(runnable.getContextSources());
            V1ConfigMap configMap = new V1ConfigMap()
                .metadata(new V1ObjectMeta().name("init-config-map-" + runnable.getId()).labels(buildLabels(runnable)))
                .data(
                    MapUtils.mergeMultipleMaps(
                        //dockerfile
                        Map.of("Dockerfile", runnable.getDockerFile()),
                        // Generate context-refs.txt if exist
                        contextRefsOpt
                            .map(contextRefsList ->
                                Map.of(
                                    "context-refs.txt",
                                    contextRefsList
                                        .stream()
                                        .map(v ->
                                            v.getProtocol() + "," + v.getDestination() + "," + v.getSource() + "\n"
                                        )
                                        .collect(Collectors.joining(""))
                                )
                            )
                            .orElseGet(Map::of),
                        // Generate context-sources.txt if exist
                        contextSourcesOpt
                            .map(contextSources ->
                                contextSources
                                    .stream()
                                    .collect(
                                        Collectors.toMap(
                                            c ->
                                                Base64
                                                    .getUrlEncoder()
                                                    .withoutPadding()
                                                    .encodeToString(c.getName().getBytes()),
                                            c ->
                                                new String(
                                                    Base64.getDecoder().decode(c.getBase64()),
                                                    StandardCharsets.UTF_8
                                                )
                                        )
                                    )
                            )
                            .orElseGet(Map::of),
                        contextSourcesOpt
                            .map(contextSources ->
                                Map.of(
                                    "context-sources-map.txt",
                                    contextSources
                                        .stream()
                                        .map(c ->
                                            Base64
                                                .getUrlEncoder()
                                                .withoutPadding()
                                                .encodeToString(c.getName().getBytes()) +
                                            "," +
                                            c.getName() +
                                            "\n"
                                        )
                                        .collect(Collectors.joining(""))
                                )
                            )
                            .orElseGet(Map::of)
                    )
                );

            coreV1Api.createNamespacedConfigMap(namespace, configMap, null, null, null, null);
            //clear data before storing
            results.put("configMap", configMap.data(Collections.emptyMap()));
        } catch (NullPointerException e) {
            throw new K8sFrameworkException(e.getMessage());
        } catch (ApiException e) {
            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }

        log.info("create job for {}", String.valueOf(job.getMetadata().getName()));
        job = create(job);
        results.put("job", job);

        //update state
        runnable.setState(State.RUNNING.name());

        //update results
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
    public K8sKanikoRunnable stop(K8sKanikoRunnable runnable) throws K8sFrameworkException {
        log.info("stop for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        List<String> messages = new ArrayList<>();

        V1Job job = get(build(runnable));

        //stop by deleting
        log.info("delete job for {}", String.valueOf(job.getMetadata().getName()));
        delete(job);
        messages.add(String.format("job %s deleted", job.getMetadata().getName()));

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

        //update state
        runnable.setState(State.STOPPED.name());
        runnable.setMessage(String.join(", ", messages));

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    @Override
    public K8sKanikoRunnable delete(K8sKanikoRunnable runnable) throws K8sFrameworkException {
        log.info("delete for {}", runnable.getId());
        if (log.isTraceEnabled()) {
            log.trace("runnable: {}", runnable);
        }

        V1Job job;
        try {
            job = get(build(runnable));
        } catch (K8sFrameworkException e) {
            runnable.setState(State.DELETED.name());
            return runnable;
        }

        List<String> messages = new ArrayList<>();
        log.info("delete job for {}", String.valueOf(job.getMetadata().getName()));
        delete(job);
        messages.add(String.format("job %s deleted", job.getMetadata().getName()));

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

        //update state
        runnable.setState(State.DELETED.name());
        runnable.setMessage(String.join(", ", messages));

        if (log.isTraceEnabled()) {
            log.trace("result: {}", runnable);
        }

        return runnable;
    }

    public V1Job build(K8sKanikoRunnable runnable) throws K8sFrameworkException {
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
        K8sTemplate<K8sKanikoRunnable> template = null;
        if (StringUtils.hasText(runnable.getTemplate()) && templates.containsKey(runnable.getTemplate())) {
            //get template
            template = templates.get(runnable.getTemplate());
        }

        //build destination image name and set to runnable
        String prefix =
            (StringUtils.hasText(imageRegistry) ? imageRegistry + "/" : "") +
            (StringUtils.hasText(imagePrefix) ? imagePrefix + "-" : "");
        // workaround: update image name only first time
        String imageName = runnable.getImage();
        if (StringUtils.hasText(prefix) && !runnable.getImage().startsWith(prefix)) {
            imageName = k8sBuilderHelper.getImageName(prefix + runnable.getImage(), runnable.getId());
            runnable.setImage(imageName);
        }

        //build labels
        Map<String, String> labels = buildLabels(runnable);

        // Create the Job metadata
        V1ObjectMeta metadata = new V1ObjectMeta().name(jobName).labels(labels);

        // Prepare environment variables for the Kubernetes job
        List<V1EnvFromSource> envFrom = buildEnvFrom(runnable);
        List<V1EnvVar> env = buildEnv(runnable);

        // Volumes to attach to the pod based on the volume spec with the additional volume_type
        List<V1Volume> volumes = new LinkedList<>(buildVolumes(runnable));
        List<V1VolumeMount> volumeMounts = new LinkedList<>(buildVolumeMounts(runnable));

        //make sure init and shared volumes are defined
        if (volumeMounts.stream().noneMatch(v -> "/init-config-map".equals(v.getMountPath()))) {
            // Create config map volume with fixed definition
            V1Volume configMap = new V1Volume().name("init-config-map");
            configMap.configMap(new V1ConfigMapVolumeSource().name("init-config-map-" + runnable.getId()));
            volumes.add(configMap);

            V1VolumeMount configMapMount = new V1VolumeMount().name("init-config-map").mountPath("/init-config-map");
            volumeMounts.add(configMapMount);
        }

        if (
            volumeMounts
                .stream()
                .noneMatch(v -> k8sProperties.getSharedVolume().getMountPath().equals(v.getMountPath()))
        ) {
            //use framework definition
            V1Volume sharedVolume = k8sBuilderHelper.getVolume(k8sProperties.getSharedVolume());
            volumes.add(sharedVolume);

            V1VolumeMount sharedVolumeMount = k8sBuilderHelper.getVolumeMount(k8sProperties.getSharedVolume());
            volumeMounts.add(sharedVolumeMount);
        }

        // Add secret for kaniko
        // NOTE: we support *only* docker config files
        if (StringUtils.hasText(kanikoSecret)) {
            V1Volume secretVolume = new V1Volume()
                .name(kanikoSecret)
                .secret(
                    new V1SecretVolumeSource()
                        .secretName(kanikoSecret)
                        .items(List.of(new V1KeyToPath().key(".dockerconfigjson").path("config.json")))
                );

            V1VolumeMount secretVolumeMount = new V1VolumeMount().name(kanikoSecret).mountPath("/kaniko/.docker");

            volumes.add(secretVolume);
            volumeMounts.add(secretVolumeMount);
        }

        // resources
        V1ResourceRequirements resources = buildResources(runnable);

        List<String> kanikoArgsAll = new ArrayList<>(
            List.of(
                "--dockerfile=/init-config-map/Dockerfile",
                "--context=" + k8sProperties.getSharedVolume().getMountPath(),
                "--destination=" + imageName
            )
        );
        // Add Kaniko args
        kanikoArgsAll.addAll(kanikoArgs);

        // Build Container
        V1Container container = new V1Container()
            .name(containerName)
            .image(kanikoImage)
            .imagePullPolicy("IfNotPresent")
            .args(kanikoArgsAll)
            .resources(resources)
            .volumeMounts(volumeMounts)
            .envFrom(envFrom)
            .env(env);

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
            .volumes(volumes)
            .restartPolicy("Never")
            .securityContext(buildPodSecurityContext(runnable));

        // Create a PodTemplateSpec with the PodSpec
        V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec().metadata(metadata).spec(podSpec);

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

        // Set initContainer as first container in the PodSpec
        podSpec.setInitContainers(Collections.singletonList(initContainer));

        int backoffLimit = Optional.ofNullable(runnable.getBackoffLimit()).orElse(0);

        // Create the JobSpec with the PodTemplateSpec, leverage template if provided
        V1JobSpec jobSpec = Optional
            .ofNullable(template)
            .map(K8sTemplate::getJob)
            .map(V1Job::getSpec)
            .orElse(new V1JobSpec());

        jobSpec
            .activeDeadlineSeconds(Long.valueOf(activeDeadlineSeconds))
            .parallelism(1)
            .completions(1)
            .backoffLimit(backoffLimit)
            .template(podTemplateSpec);

        // Return a new job with metadata and jobSpec
        return new V1Job().metadata(metadata).spec(jobSpec);
    }

    /*
     * K8s
     */
    public V1Job apply(@NotNull V1Job job) throws K8sFrameworkException {
        return job;
    }

    public V1Job get(@NotNull V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("get k8s job for {}", jobName);

            return batchV1Api.readNamespacedJob(jobName, namespace, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    public V1Job create(V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("create k8s job for {}", jobName);

            //dispatch job via api
            return batchV1Api.createNamespacedJob(namespace, job, null, null, null, null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }

    public void delete(V1Job job) throws K8sFrameworkException {
        Assert.notNull(job.getMetadata(), "metadata can not be null");

        try {
            String jobName = job.getMetadata().getName();
            log.debug("delete k8s job for {}", jobName);

            batchV1Api.deleteNamespacedJob(jobName, namespace, null, null, null, null, "Foreground", null);
        } catch (ApiException e) {
            log.error("Error with k8s: {}", e.getResponseBody());
            if (log.isDebugEnabled()) {
                log.debug("k8s api response: {}", e.getResponseBody());
            }

            throw new K8sFrameworkException(e.getMessage(), e.getResponseBody());
        }
    }
}
