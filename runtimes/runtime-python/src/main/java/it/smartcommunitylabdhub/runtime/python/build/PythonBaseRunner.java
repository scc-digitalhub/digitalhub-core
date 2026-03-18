/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package it.smartcommunitylabdhub.runtime.python.build;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreResource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import it.smartcommunitylabdhub.runtime.python.config.PythonProperties;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionBuilder;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;
import it.smartcommunitylabdhub.runtime.python.runners.PythonRunnerHelper;
import it.smartcommunitylabdhub.runtime.python.utils.NoEncodingMustacheFactory;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public abstract class PythonBaseRunner {

    protected final PythonProperties properties;

    protected final Map<String, String> images;
    protected final Map<String, String> serverlessImages;
    protected final Map<String, String> baseImages;

    protected final int userId;
    protected final int groupId;
    protected final String homeDir;
    protected final String volumeSizeSpec;

    protected final String command;
    protected final List<String> dependencies;

    protected final K8sBuilderHelper k8sBuilderHelper;

    private final DefaultResourceLoader loader = new DefaultResourceLoader();
    protected String entrypoint;
    protected String passwdFile;
    protected Mustache handlerTemplate;

    protected PythonBaseRunner(PythonProperties properties, K8sBuilderHelper k8sBuilderHelper) {
        Assert.notNull(properties, "properties are required");
        this.properties = properties;

        this.images = properties.getImages();
        this.serverlessImages = properties.getServerlessImages();
        this.baseImages = properties.getBaseImages();

        this.k8sBuilderHelper = k8sBuilderHelper;

        this.userId = properties.getUserId() != null ? properties.getUserId() : PythonRuntime.UID;
        this.groupId = properties.getGroupId() != null ? properties.getGroupId() : PythonRuntime.GID;
        this.homeDir = properties.getHomeDir() != null ? properties.getHomeDir() : PythonRuntime.HOME_DIR;
        this.volumeSizeSpec = properties.getVolumeSize();

        this.command = properties.getCommand();
        this.dependencies = properties.getDependencies();

        //init resources for entrypoint, passwd and handler template when setm, or fall back to default
        String entrypointPath = properties.getEntrypoint() != null
            ? properties.getEntrypoint()
            : "classpath:/runtime-python/docker/entrypoint.sh";
        setEntrypoint(loader.getResource(entrypointPath));

        String passwdPath = properties.getPasswdTemplate() != null
            ? properties.getPasswdTemplate()
            : "classpath:/runtime-python/docker/passwd.mustache";
        setPasswdTemplate(loader.getResource(passwdPath));
    }

    public void setEntrypoint(Resource resource) {
        try {
            this.entrypoint = new String(resource.getContentAsByteArray(), StandardCharsets.UTF_8);
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading entrypoint for runtime");
        }
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

    public void setHandlerTemplate(Resource resource) {
        try {
            log.debug("Loading template handler from {}", resource.getURI().toURL());
            MustacheFactory mustacheFactory = new NoEncodingMustacheFactory();
            this.handlerTemplate = mustacheFactory.compile(new InputStreamReader(resource.getInputStream()), "handler");
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading handler template for runtime");
        }
    }

    protected boolean useLayer(String pythonVersion, @Nullable String baseImage, @Nullable String userImage) {
        String defaultImage = images.get(pythonVersion);
        String defaultBaseImage = baseImages.get(pythonVersion);

        if (
            !StringUtils.hasText(baseImage) &&
            !StringUtils.hasText(userImage) &&
            !StringUtils.hasText(defaultImage) &&
            !StringUtils.hasText(defaultBaseImage)
        ) {
            throw new IllegalArgumentException("No suitable image configuration found");
        }

        // use layer image if no predefined image is set and user set base image
        // or there is no default image defined
        return (
            !StringUtils.hasText(userImage) && (StringUtils.hasText(baseImage) || !StringUtils.hasText(defaultImage))
        );
    }

    protected String buildNuclioFunction(
        @Nullable Map<String, Serializable> triggers,
        @Nullable Map<String, Serializable> event
    ) {
        HashMap<String, Serializable> httpTrigger = new HashMap<>(Map.of("kind", "http", "maxWorkers", 2));
        HashMap<String, Serializable> fnTrigger = triggers != null ? new HashMap<>(triggers) : new HashMap<>();
        if (fnTrigger.isEmpty()) {
            //add default http trigger if no trigger is provided
            fnTrigger.put("http", httpTrigger);
        }

        //event data is optional
        HashMap<String, Serializable> eventData = event != null ? new HashMap<>(event) : new HashMap<>();

        // Build Nuclio function
        NuclioFunctionSpec nuclio = NuclioFunctionSpec
            .builder()
            .runtime("python")
            //invoke user code wrapped via default handler
            .handler("handler:handler")
            //directly invoke user code
            // .handler("main:" + runSpec.getFunctionSpec().getSource().getHandler())
            .triggers(fnTrigger)
            .event(eventData)
            .build();

        return NuclioFunctionBuilder.write(nuclio);
    }

    protected String buildHandler(PythonSourceCode source) {
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

    protected List<String> buildArgs(String pythonVersion, @Nullable String baseImage, @Nullable String userImage) {
        List<String> args = new ArrayList<>();
        if (useLayer(pythonVersion, baseImage, userImage)) {
            args.addAll(
                PythonRunnerHelper.buildEntrypointArgs(
                    command,
                    "/opt/nuclio/uv/uv",
                    List.of("/opt/nuclio/requirements/nuclio.txt", "/opt/nuclio/requirements/common.txt"),
                    "/opt/nuclio/whl"
                )
            );
        } else {
            args.addAll(PythonRunnerHelper.buildEntrypointArgs(command, null, null, null));
        }

        return args;
    }

    protected List<CoreVolume> buildVolumes(
        Run run,
        K8sFunctionTaskBaseSpec taskSpec,
        String pythonVersion,
        @Nullable String baseImage,
        @Nullable String userImage
    ) {
        List<CoreVolume> coreVolumes = createVolumes(run, taskSpec);

        if (useLayer(pythonVersion, baseImage, userImage)) {
            coreVolumes.add(PythonRunnerHelper.createServerlessImageVolume(serverlessImages.get(pythonVersion)));
        }

        return coreVolumes;
    }

    protected List<String> buildRequirements(String image, @Nullable List<String> functionRequirements) {
        Set<String> reqs = new HashSet<>();
        if (dependencies != null && !dependencies.isEmpty()) {
            if (images.containsValue(image)) {
                //prebuild images already contain dependencies, no need to add them again
                log.debug("Skip adding runtime dependencies to pre-build images");
            } else {
                log.debug("Adding runtime dependencies {} to custom image {}", dependencies, image);
                reqs.addAll(dependencies);
            }
        }

        if (functionRequirements != null) {
            log.debug("Adding function dependencies to custom image {}", image);
            reqs.addAll(functionRequirements);
        }

        if (log.isTraceEnabled()) {
            log.trace("Requirements for image {}: {}", image, reqs);
        }

        return new ArrayList<>(reqs);
    }

    protected String buildImage(String pythonVersion, @Nullable String baseImage, @Nullable String userImage) {
        String defaultImage = images.get(pythonVersion);
        String defaultBaseImage = baseImages.get(pythonVersion);

        if (
            !StringUtils.hasText(baseImage) &&
            !StringUtils.hasText(userImage) &&
            !StringUtils.hasText(defaultImage) &&
            !StringUtils.hasText(defaultBaseImage)
        ) {
            throw new IllegalArgumentException("No suitable image configuration found");
        }

        if (useLayer(pythonVersion, baseImage, userImage)) {
            return StringUtils.hasText(baseImage) ? baseImage : defaultBaseImage;
        } else {
            return StringUtils.hasText(userImage) ? userImage : defaultImage;
        }
    }

    protected List<CoreEnv> createEnvList(Run run, K8sFunctionTaskBaseSpec taskSpec) {
        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );
        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        //merge env with PYTHON path override
        if (StringUtils.hasText(homeDir)) {
            coreEnvList.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:" + homeDir));
        }

        return coreEnvList;
    }

    protected List<CoreEnv> createSecrets(Run run, Map<String, String> secretData) {
        return secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();
    }

    protected List<CoreVolume> createVolumes(Run run, K8sFunctionTaskBaseSpec taskSpec) {
        List<CoreVolume> coreVolumes = new ArrayList<>(
            taskSpec.getVolumes() != null ? taskSpec.getVolumes() : List.of()
        );
        //check if scratch disk is requested as resource or set default
        String volumeSize = taskSpec.getResources() != null && taskSpec.getResources().getDisk() != null
            ? taskSpec.getResources().getDisk()
            : volumeSizeSpec;
        CoreResource diskResource = new CoreResource();
        diskResource.setDisk(volumeSize);

        Optional
            .ofNullable(k8sBuilderHelper)
            .ifPresent(helper -> {
                Optional.ofNullable(helper.buildSharedVolume(diskResource)).ifPresent(coreVolumes::add);
            });

        return coreVolumes;
    }
}
