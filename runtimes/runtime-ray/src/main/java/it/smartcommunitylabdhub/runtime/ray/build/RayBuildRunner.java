/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.runtime.ray.build;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreLabel;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGenerator;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGeneratorFactory;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileInstruction;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sContainerBuilderRunnable;
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

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

/**
 * Builds a {@link K8sContainerBuilderRunnable} for the {@code ray+build} task.
 *
 * <p>Generates a Dockerfile that:</p>
 * <ol>
 *   <li>starts {@code FROM} the function image (when set) or the runtime
 *       default ray image;</li>
 *   <li>copies the staged source context into the runtime home directory;</li>
 *   <li>installs dependencies, either from an explicit {@code dependency_spec}
 *       or by merging {@code requirements} with the runtime defaults
 *       (defaulting to {@code pip});</li>
 *   <li>executes any extra {@code instructions} provided on the build task.</li>
 * </ol>
 */
@Slf4j
public class RayBuildRunner {

    public static final int MIN_IMAGE_NAME_LENGTH = 3;
    private static final String DEFAULT_HOME_DIR = "/shared";
    private static final String DEFAULT_MAIN_FILE = "main.py";
    private static final String REQUIREMENTS_FILE = "requirements.txt";

    private final RayProperties properties;
    private final K8sBuilderHelper k8sBuilderHelper;

    protected final int userId;
    protected final int groupId;
    protected final String homeDir;

    private final DefaultResourceLoader loader = new DefaultResourceLoader();
    protected String passwdFile;

    public RayBuildRunner(RayProperties properties, @Nullable K8sBuilderHelper k8sBuilderHelper) {
        this.properties = properties;
        
        this.k8sBuilderHelper = k8sBuilderHelper;
        this.userId = properties.getUserId() != null ? properties.getUserId() : RayRuntime.UID;
        this.groupId = properties.getGroupId() != null ? properties.getGroupId() : RayRuntime.GID;
        this.homeDir = properties.getHomeDir() != null ? properties.getHomeDir() : RayRuntime.HOME_DIR;

        String passwdPath = properties.getPasswdTemplate() != null
            ? properties.getPasswdTemplate()
            : "classpath:/runtime-ray/docker/passwd.template";
        setPasswdTemplate(loader.getResource(passwdPath));

    }

    public void setPasswdTemplate(Resource resource) {
        String passwd = null;
        try {
            log.debug("Building passwd template for {}:{} with home {}", this.userId, this.groupId, this.homeDir);
            MustacheFactory mustacheFactory = new DefaultMustacheFactory();
            Mustache template = mustacheFactory.compile(new InputStreamReader(resource.getInputStream()), "passwd");

            passwd =
                template
                    .execute(
                        new StringWriter(),
                        Map.of("userId", this.userId, "groupId", this.groupId, "homeDir", this.homeDir)
                    )
                    .toString();
        } catch (IOException ioe) {
            log.error("error with building passwd template for runtime", ioe);
            //disable template
        }

        this.passwdFile = passwd;
    }

    public K8sContainerBuilderRunnable produce(Run run, Map<String, String> secretData) {
        RayBuildRunSpec runSpec = new RayBuildRunSpec(run.getSpec());
        RayBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();
        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(taskSpec.toMap());
        RayFunctionSpec functionSpec = runSpec.getFunctionSpec();

        //base image: function base image overrides; otherwise runtime default
        String baseImage = StringUtils.hasText(functionSpec.getBaseImage())
            ? functionSpec.getBaseImage()
            : properties.getImage();
        if (!StringUtils.hasText(baseImage)) {
            throw new CoreRuntimeException("no valid base image found for ray build");
        }

        //envs/secrets
        List<CoreEnv> coreEnvList = buildEnvList(run, taskSpec);
        List<CoreEnv> coreSecrets = buildSecrets(secretData);

        //source context
        RaySourceCode source = functionSpec.getSource();
        List<ContextRef> contextRefs = buildContextRefs(source);
        List<ContextSource> contextSources = buildContextSources(functionSpec);

        //inject custom passwd to add our user
        if (passwdFile != null) {
            ContextSource entry = ContextSource
                .builder()
                .name("passwd-template")
                .base64(Base64.getEncoder().encodeToString(passwdFile.getBytes(StandardCharsets.UTF_8)))
                .build();
            contextSources.add(entry);
        }

        //compose dockerfile
        String dockerfile = generateDockerfile(baseImage, functionSpec, taskSpec);

        //resolve image name
        RunSpecAccessor runSpecAccessor = RunSpecAccessor.with(run.getSpec());
        String imageName =
            K8sBuilderHelper.sanitizeNames(runSpecAccessor.getProject()) +
            "-" +
            K8sBuilderHelper.sanitizeNames(runSpecAccessor.getFunction());

        //honor user-provided image name override
        if (StringUtils.hasText(functionSpec.getImage())) {
            String name = functionSpec.getImage().split(":")[0]; //strip tag
            if (StringUtils.hasText(name) && name.length() > MIN_IMAGE_NAME_LENGTH) {
                imageName = name;
            }
        }

        return K8sContainerBuilderRunnable
            .builder()
            .id(run.getId())
            .project(run.getProject())
            .runtime(RayRuntime.RUNTIME)
            .task(RayBuildTaskSpec.KIND)
            .state(State.READY.name())
            .labels(
                k8sBuilderHelper != null
                    ? List.of(new CoreLabel(k8sBuilderHelper.getLabelName("function"), taskAccessor.getFunction()))
                    : null
            )
            .image(imageName)
            .contextRefs(contextRefs)
            .contextSources(contextSources)
            .envs(coreEnvList)
            .secrets(coreSecrets)
            .resources(
                taskSpec.getResources() != null && k8sBuilderHelper != null
                    ? k8sBuilderHelper.convertResources(taskSpec.getResources())
                    : null
            )
            .volumes(taskSpec.getVolumes())
            .template(taskSpec.getProfile())
            .dockerFile(dockerfile)
            .build();
    }

    // ---------- dockerfile ----------

    private String generateDockerfile(String baseImage, RayFunctionSpec functionSpec, RayBuildTaskSpec taskSpec) {
        DockerfileGeneratorFactory df = DockerfileGenerator.factory();

        df.from(baseImage);

        //arg+env from task envs (build-time + runtime)
        List<CoreEnv> envs = taskSpec.getEnvs();
        if (envs != null) {
            envs.forEach(env ->
                df.instruction(DockerfileInstruction.Kind.ARG, env.name() + "=" + env.value())
            );
            envs.forEach(env ->
                df.instruction(DockerfileInstruction.Kind.ENV, env.name() + "=$" + env.name())
            );
        }

        String home = StringUtils.hasText(properties.getHomeDir()) ? properties.getHomeDir() : DEFAULT_HOME_DIR;

        //stage source context as workdir
        df.copy(".", home);
        df.workdir(home);

        //extra user instructions before dependency install
        if (taskSpec.getInstructions() != null) {
            taskSpec.getInstructions().forEach(df::run);
        }

        //install dependencies
        appendDependencies(df, functionSpec, home);

        return df.build().generate();
    }

    private void appendDependencies(DockerfileGeneratorFactory df, RayFunctionSpec functionSpec, String home) {
        //explicit dependency spec wins
        if (functionSpec.getDependencyFormat() != null && functionSpec.getDependencySpec() != null) {
            RayDependencyFormat fmt = functionSpec.getDependencyFormat();
            Serializable spec = functionSpec.getDependencySpec();
            switch (fmt) {
                case pip -> {
                    // list of reqs
                    if (spec instanceof List<?> list) {
                        df.run("pip install --no-cache-dir " + joinPackages(list));
                    }
                    // req file
                    else if (spec instanceof String s && StringUtils.hasText(s)) {
                        df.run("pip install --no-cache-dir -r " + s);
                    }
                    // else not supported, skip. TODO: support as of https://www.python.org/dev/peps/pep-0508/
                }
                case conda -> {
                    // conda env file
                    df.run("conda env update -f " + spec);
                    // else not supported, skip
                }
                case uv -> {
                    if (spec instanceof List<?> list) {
                        df.run("uv pip install --system " + joinPackages(list));
                    }
                    // req file 
                    else if (spec instanceof String s && StringUtils.hasText(s)) {
                        df.run("uv pip install --system -r " + s);
                    }
                    // else not supported, skip. TODO: support 
                }
            }
            return;
        }

        //otherwise merge runtime defaults + function requirements into a single
        //pip install pulled from the staged requirements.txt
        List<String> reqs = mergedRequirements(functionSpec);
        if (!reqs.isEmpty()) {
            df.run("pip install --no-cache-dir -r " + (home.endsWith("/") ? home : home + "/") + REQUIREMENTS_FILE);
        }
    }

    private static String joinPackages(List<?> list) {
        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            //quote to be safe with version specifiers like "ray>=2.9"
            sb.append('"').append(String.valueOf(o).replace("\"", "\\\"")).append('"');
        }
        return sb.toString();
    }

    // ---------- context ----------

    private String resolveSourceFileName(@Nullable RaySourceCode source) {
        if (source == null || !StringUtils.hasText(source.getSource())) {
            return DEFAULT_MAIN_FILE;
        }
        try {
            UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
            if (uri.getScheme() == null && uri.getPath() != null) {
                String p = uri.getPath();
                if (p.startsWith(".")) {
                    p = p.substring(1);
                }
                if (StringUtils.hasText(p)) {
                    return p.startsWith("/") ? p.substring(1) : p;
                }
            }
        } catch (IllegalArgumentException e) {
            //fallthrough
        }
        return DEFAULT_MAIN_FILE;
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

    private List<ContextSource> buildContextSources(RayFunctionSpec functionSpec) {
        List<ContextSource> sources = new ArrayList<>();

        RaySourceCode source = functionSpec.getSource();
        if (source != null && StringUtils.hasText(source.getBase64())) {
            sources.add(
                ContextSource.builder().name(resolveSourceFileName(source)).base64(source.getBase64()).build()
            );
        }

        //emit requirements.txt only when no explicit dependency_spec is provided
        if (functionSpec.getDependencyFormat() == null || functionSpec.getDependencySpec() == null) {
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
        }

        return sources;
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

    // ---------- envs ----------

    private List<CoreEnv> buildEnvList(Run run, RayBuildTaskSpec taskSpec) {
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
}
