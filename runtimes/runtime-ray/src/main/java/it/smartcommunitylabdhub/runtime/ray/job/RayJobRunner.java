/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.job;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.ray.model.ClusterModel;
import it.smartcommunitylabdhub.framework.ray.model.PodModel;
import it.smartcommunitylabdhub.framework.ray.model.RayJobModel;
import it.smartcommunitylabdhub.framework.ray.model.WorkerGroupModel;
import it.smartcommunitylabdhub.framework.ray.runnables.K8sRayJobRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runtime.ray.RayRuntime;
import it.smartcommunitylabdhub.runtime.ray.config.RayProperties;
import it.smartcommunitylabdhub.runtime.ray.model.RayDependencyFormat;
import it.smartcommunitylabdhub.runtime.ray.model.RaySourceCode;
import it.smartcommunitylabdhub.runtime.ray.specs.RayFunctionSpec;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.MustacheFactory;

/**
 * Builds a {@link K8sRayJobRunnable} for the {@code ray+job} task.
 *
 * <p>Translates the simplified ray task spec into the underlying framework
 * model:</p>
 * <ul>
 *   <li>resolves images for head and worker pods (task &gt; function &gt; properties);</li>
 *   <li>builds a fixed head + single-worker-group cluster, applying inherited
 *       k8s properties to the head and the {@code worker_*} fields to the worker;</li>
 *   <li>derives the entrypoint from the source code (e.g. {@code python /shared/main.py})
 *       when the task does not provide an explicit override;</li>
 *   <li>maps function source/requirements to context refs/sources and ray
 *       runtime-env dependencies;</li>
 *   <li>fills in job lifecycle settings (backoff, shutdown, ttl, deadline)
 *       from runtime properties.</li>
 * </ul>
 */
@Slf4j
public class RayJobRunner {

    private static final String DEFAULT_MAIN_FILE = "main.py";
    private static final String REQUIREMENTS_FILE = "requirements.txt";
    private static final String DEFAULT_HOME_DIR = "/shared";
    private static final int DEFAULT_WORKER_REPLICAS = 1;

    private final RayProperties properties;
    private final K8sBuilderHelper k8sBuilderHelper;
    private Mustache handlerTemplate;

    private CoreResource defaultCoreResource;

    public RayJobRunner(RayProperties properties, @Nullable K8sBuilderHelper k8sBuilderHelper) {
        this.properties = properties;
        this.k8sBuilderHelper = k8sBuilderHelper;
        setHandlerTemplate(new ClassPathResource("runtime-ray/docker/_job_handler.py"));
        defaultCoreResource = new CoreResource();
        defaultCoreResource.setCpu(properties.getHeadCpu() != null ? properties.getHeadCpu() : "1");
        defaultCoreResource.setMem(properties.getHeadMemory() != null ? properties.getHeadMemory() : "2Gi");
        defaultCoreResource.setDisk(properties.getHeadDiskSize() != null ? properties.getHeadDiskSize() : "2Gi");
    }


    public void setHandlerTemplate(Resource resource) {
        try {
            log.debug("Loading template handler from {}", resource.getURI().toURL());
            MustacheFactory mustacheFactory = new NoEncodingMustacheFactory();
            this.handlerTemplate = mustacheFactory.compile(new InputStreamReader(resource.getInputStream()), "handler");
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading handler template for runtime");
        }
    }
    public K8sRayJobRunnable produce(Run run, Map<String, String> secretData) {
        RayJobRunSpec runSpec = new RayJobRunSpec(run.getSpec());
        RayJobTaskSpec taskSpec = runSpec.getTaskJobSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        RayFunctionSpec functionSpec = runSpec.getFunctionSpec();

        //resolve images: head/worker overrides on task, then function image, then properties defaults
        String headImage = firstNonBlank(/*taskSpec.getHeadImage(),*/ functionSpec.getImage(), properties.getImage());
        String workerImage = firstNonBlank(/*taskSpec.getWorkerImage(),*/ functionSpec.getImage(), properties.getWorkerImage(), headImage);
        if (!StringUtils.hasText(headImage)) {
            throw new IllegalArgumentException("No ray image configured: set runtime.ray.image or function.image");
        }

        //envs and secrets are common to all nodes; framework propagates them to pods
        List<CoreEnv> envs = buildEnvList(run, taskSpec);
        List<CoreEnv> secrets = buildSecrets(secretData);

        //source code → context refs / sources
        List<ContextRef> contextRefs = buildContextRefs(functionSpec.getSource());
        List<ContextSource> contextSources = buildContextSources(functionSpec);

        //resolve entrypoint: explicit override wins, otherwise derive from source code
        // String entrypoint = StringUtils.hasText(taskSpec.getEntrypoint())
        //     ? taskSpec.getEntrypoint()
        //     : deriveEntrypoint(functionSpec.getSource());
        String entrypoint = deriveEntrypoint(functionSpec.getSource());

        String handlerMethod = deriveHandlerMethod(functionSpec.getSource());
        if (handlerMethod != null) {
            // and use wrapper python as entrypoint instead of the raw source to inject core references
            String handlerFile = buildHandler(functionSpec.getSource());

            contextSources.add(
                ContextSource
                .builder()
                .name("handler.py")
                .base64(Base64.getEncoder().encodeToString(handlerFile.getBytes(StandardCharsets.UTF_8)))
                .build()
            );
            entrypoint = "python handler.py";
        }

        //resolve dependencies → ray runtime env
        Dependencies deps = resolveDependencies(functionSpec);

        //assemble cluster: one head + one worker group
        ClusterModel cluster = buildCluster(taskSpec, headImage, workerImage, functionSpec.getRayVersion());

        //assemble ray spec
        RayJobModel raySpec = new RayJobModel();
        raySpec.setCluster(cluster);
        raySpec.setEntrypoint(entrypoint);
        // raySpec.setClusterSelector(taskSpec.getClusterSelector());
        raySpec.setDependencyFormat(deps.format);
        raySpec.setDependencySpec(deps.spec);
        raySpec.setBackoffLimit(properties.getBackoffLimit());
        raySpec.setShutdownAfterJobFinishes(properties.getShutdownAfterJobFinishes());
        raySpec.setTtlSecondsAfterFinished(properties.getTtlSecondsAfterFinished());
        raySpec.setPreRunningDeadlineSeconds(properties.getPreRunningDeadlineSeconds());

        K8sRayJobRunnable runnable = K8sRayJobRunnable
            .builder()
            .runtime(RayRuntime.RUNTIME)
            .task(RayJobTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            .image(headImage)
            .contextRefs(contextRefs)
            .contextSources(contextSources)
            .envs(envs)
            .secrets(secrets)
            .template(taskSpec.getProfile())
            .spec(raySpec)
            .build();

        runnable.setId(run.getId());
        runnable.setProject(run.getProject());

        return runnable;
    }

    // ---------- cluster ----------

    private ClusterModel buildCluster(RayJobTaskSpec taskSpec, String headImage, String workerImage, String version) {
        //head pod: inherited k8s properties (resources, volumes) apply here
        PodModel head = PodModel
            .builder()
            .image(headImage)
            .resources(k8sBuilderHelper.convertResources(defaultCoreResource))
            .volumes(taskSpec.getVolumes())
            .template(properties.getHeadProfile() != null ? properties.getHeadProfile() : null)
            .startParams(properties.getHeadStartParams())
            .rayResources(properties.getHeadRayResources())
            .build();

        //worker pod: dedicated worker_* fields
        PodModel worker = PodModel
            .builder()
            .image(workerImage)
            .resources(taskSpec.getResources() != null && k8sBuilderHelper != null
                ? k8sBuilderHelper.convertResources(taskSpec.getResources())
                : null)
            .volumes(taskSpec.getVolumes())
            .template(taskSpec.getProfile() != null ? taskSpec.getProfile() : properties.getWorkerProfile())
            .startParams(properties.getWorkerStartParams())
            .rayResources(properties.getWorkerRayResources())
            .build();

        int replicas = Optional.ofNullable(taskSpec.getReplicas()).orElse(DEFAULT_WORKER_REPLICAS);
        int minReplicas = Optional.ofNullable(taskSpec.getMinReplicas()).orElse(replicas);
        int maxReplicas = Optional.ofNullable(taskSpec.getMaxReplicas()).orElse(replicas);

        WorkerGroupModel workerGroup = WorkerGroupModel
            .builder()
            .name(StringUtils.hasText(properties.getWorkerGroupName()) ? properties.getWorkerGroupName() : "workers")
            .replicas(replicas)
            .minReplicas(minReplicas)
            .maxReplicas(maxReplicas)
            .workerSpec(worker)
            .build();

        String finalVersion = StringUtils.hasText(version)
            ? version
            : properties.getVersion();

        return ClusterModel
            .builder()
            .version(finalVersion)
            .headSpec(head)
            .workerGroups(new ArrayList<>(List.of(workerGroup)))
            .build();
    }

    // ---------- envs / secrets ----------

    private List<CoreEnv> buildEnvList(Run run, RayJobTaskSpec taskSpec) {
        List<CoreEnv> list = new ArrayList<>();
        list.add(new CoreEnv("PROJECT_NAME", run.getProject()));
        list.add(new CoreEnv("RUN_ID", run.getId()));
        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(list::addAll);

        String home = StringUtils.hasText(properties.getHomeDir()) ? properties.getHomeDir() : DEFAULT_HOME_DIR;
        list.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:" + home));
        return list;
    }

    private List<CoreEnv> buildSecrets(Map<String, String> secretData) {
        if (secretData == null || secretData.isEmpty()) {
            return null;
        }
        return secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();
    }

    // ---------- source / context ----------

    /**
     * Derive the staged file name from a source descriptor. When the source
     * carries a local path (no URI scheme) we honor it; otherwise we use a
     * default {@code main.py}.
     */
    private String resolveSourceFileName(@Nullable RaySourceCode source) {
        if (source == null || !StringUtils.hasText(source.getSource())) {
            return DEFAULT_MAIN_FILE;
        }
        try {
            UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
            // when the source is a simple local path, use it as-is; otherwise fallback to default
            if (uri.getScheme() == null && uri.getPath() != null) {
                String p = uri.getPath();
                if (p.startsWith(".")) {
                    p = p.substring(1);
                }
                if (StringUtils.hasText(p)) {
                    return p.startsWith("/") ? p.substring(1) : p;
                }
            // look at handler
            } else if (StringUtils.hasText(source.getHandler())) {
                // handler may be in the form path/to/file.py or module.submodule:func; in both cases we take the file/module part and convert to a path
                String file = source.getHandler();
                if (file.endsWith(".py")) {
                    return file;
                }
                if (file.contains(":")) {
                    file = file.split(":")[0];
                }
                file = file.replaceAll("\\.", "/");
                return file + ".py";
            }
        } catch (IllegalArgumentException e) {
            //fallthrough to default
        }
        return DEFAULT_MAIN_FILE;
    }

    private String deriveEntrypoint(@Nullable RaySourceCode source) {
        String file = resolveSourceFileName(source);
        //source language is python; use the python interpreter
        return "python "  + file;
    }


    private String deriveHandlerMethod(@Nullable RaySourceCode source) {
        if (source != null && StringUtils.hasText(source.getHandler())) {
            // handler may be in the form path/to/file.py or module.submodule:func; in both cases we take the func part if present
            String handler = source.getHandler();
            if (handler.endsWith(".py")) {
                return null;
            }
            return handler;
        }
        return null;
    }

    private List<ContextSource> buildContextSources(RayFunctionSpec functionSpec) {
        List<ContextSource> sources = new ArrayList<>();

        RaySourceCode source = functionSpec.getSource();
        if (source != null && StringUtils.hasText(source.getBase64())) {
            sources.add(ContextSource.builder().name(resolveSourceFileName(source)).base64(source.getBase64()).build());
        }

        //emit a requirements.txt only when no explicit dependency_spec is provided
        // if (functionSpec.getDependencyFormat() == null || functionSpec.getDependencySpec() == null)
        List<String> reqs = mergedRequirements(functionSpec);
        if (!reqs.isEmpty()) {
            String content = String.join("\n", reqs);
            sources.add(
                ContextSource
                    .builder()
                    .name(REQUIREMENTS_FILE)
                    .base64(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))
                    .build()
            );
        }
        

        return sources;
    }

    private List<ContextRef> buildContextRefs(@Nullable RaySourceCode source) {
        if (source == null || !StringUtils.hasText(source.getSource())) {
            return List.of();
        }
        try {
            UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
            if (uri.getScheme() != null) {
                return Collections.singletonList(ContextRef.from(source.getSource()));
            }
        } catch (IllegalArgumentException e) {
            //skip invalid source
        }
        return List.of();
    }

    protected String buildHandler(RaySourceCode source) {
        if (this.handlerTemplate == null) {
            throw new CoreRuntimeException("handler template not set");
        }

        try {
            StringWriter writer = new StringWriter();
            Mustache handlerMustache = this.handlerTemplate;
            handlerMustache.execute(
                writer,
                Collections.singletonMap("source", JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(source))
            );
            writer.flush();

            return writer.toString();
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with building handler template", ioe);
        }
    }

    
    // ---------- dependencies ----------

    private Dependencies resolveDependencies(RayFunctionSpec functionSpec) {
        // if (functionSpec.getDependencyFormat() != null && functionSpec.getDependencySpec() != null) {
        //     return new Dependencies(functionSpec.getDependencyFormat().value(), functionSpec.getDependencySpec());
        // }

        List<String> reqs = mergedRequirements(functionSpec);
        if (reqs.isEmpty()) {
            return Dependencies.NONE;
        }

        RayDependencyFormat fmt = properties.getDependencyFormat() != null
            ? properties.getDependencyFormat()
            : RayDependencyFormat.pip;
        return new Dependencies(fmt.value(), new ArrayList<>(reqs));
    }

    private List<String> mergedRequirements(RayFunctionSpec functionSpec) {
        Set<String> reqs = new LinkedHashSet<>();
        if (properties.getDependencies() != null) {
            reqs.addAll(properties.getDependencies());
        }
        if (functionSpec.getRequirements() != null) {
            reqs.addAll(functionSpec.getRequirements());
        }
        return new ArrayList<>(reqs);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (StringUtils.hasText(v)) {
                return v;
            }
        }
        return null;
    }

    private static final class Dependencies {

        static final Dependencies NONE = new Dependencies(null, null);

        final String format;
        final Serializable spec;

        Dependencies(String format, Serializable spec) {
            this.format = format;
            this.spec = spec;
        }
    }

    public class NoEncodingMustacheFactory extends DefaultMustacheFactory {
        @Override
        public void encode(String value, Writer writer) {
            try {
                //write string as is, no encoding
                //this is needed to avoid encoding of json strings
                writer.write(value);
            } catch (IOException e) {
                throw new MustacheException(e);
            }
        }
    }
}
