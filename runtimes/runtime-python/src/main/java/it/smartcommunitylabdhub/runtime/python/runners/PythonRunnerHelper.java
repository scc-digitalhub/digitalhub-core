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

package it.smartcommunitylabdhub.runtime.python.runners;

import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreVolume;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class PythonRunnerHelper {

    // protected static MustacheFactory mustacheFactory = new NoEncodingMustacheFactory();

    // protected static final String[] HANDLER_FILES = new String[] { "_job_handler", "_serve_handler" };

    // protected static final Resource jobHandlerTemplate = new ClassPathResource("runtime-python/docker/_job_handler.py");
    // protected static final Resource serveHandlerTemplate = new ClassPathResource(
    //     "runtime-python/docker/_serve_handler.py"
    // );
    // protected static Map<String, Mustache> mustacheCache = new HashMap<>();

    // static {
    //     try {
    //         for (String handlerFile : HANDLER_FILES) {
    //             mustacheCache.put(
    //                 handlerFile,
    //                 mustacheFactory.compile(
    //                     new InputStreamReader(
    //                         new ClassPathResource("runtime-python/docker/" + handlerFile + ".py").getInputStream()
    //                     ),
    //                     handlerFile
    //                 )
    //             );
    //         }
    //     } catch (IOException e) {
    //         throw new RuntimeException("Error loading python runner templates", e);
    //     }
    // }

    private PythonRunnerHelper() {}

    // /**
    //  * Generate environment variable list for the given run and task spec
    //  * @param run
    //  * @param taskSpec
    //  * @return
    //  */
    // public static List<CoreEnv> createEnvList(Run run, K8sFunctionTaskBaseSpec taskSpec) {
    //     List<CoreEnv> coreEnvList = new ArrayList<>(
    //         List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
    //     );
    //     Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);
    //     //merge env with PYTHON path override
    //     coreEnvList.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:/shared/"));

    //     return coreEnvList;
    // }

    // /**
    //  * Generate secret environment variable list for the given secret data map
    //  * @param secretData
    //  * @return
    //  */
    // public static List<CoreEnv> createSecrets(Map<String, String> secretData) {
    //     List<CoreEnv> coreSecrets = secretData == null
    //         ? null
    //         : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();
    //     return coreSecrets;
    // }

    // /**
    //  * Generate context sources for the given function spec
    //  * @param functionSpec
    //  * @param triggerKind
    //  * @param handler
    //  * @return
    //  */
    // public static List<ContextSource> createContextSources(
    //     PythonFunctionSpec functionSpec,
    //     HashMap<String, Serializable> event,
    //     Map<String, Serializable> triggers,
    //     String handlerFile,
    //     List<String> dependencies
    // ) {
    //     List<ContextSource> contextSources = new ArrayList<>();

    //     // Build Nuclio function
    //     NuclioFunctionSpec nuclio = NuclioFunctionSpec
    //         .builder()
    //         .runtime("python")
    //         //invoke user code wrapped via default handler
    //         .handler(handlerFile + ":handler")
    //         //directly invoke user code
    //         // .handler("main:" + runSpec.getFunctionSpec().getSource().getHandler())
    //         .triggers(triggers)
    //         .event(event)
    //         .build();

    //     String nuclioFunction = NuclioFunctionBuilder.write(nuclio);

    //     ContextSource fn = ContextSource
    //         .builder()
    //         .name("function.yaml")
    //         .base64(Base64.getEncoder().encodeToString(nuclioFunction.getBytes(StandardCharsets.UTF_8)))
    //         .build();
    //     contextSources.add(fn);

    //     if (functionSpec.getSource() != null) {
    //         PythonSourceCode source = functionSpec.getSource();
    //         String path = "main.py";

    //         if (StringUtils.hasText(source.getSource())) {
    //             try {
    //                 //evaluate if local path (no scheme)
    //                 UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
    //                 String scheme = uri.getScheme();

    //                 if (scheme == null) {
    //                     if (StringUtils.hasText(path)) {
    //                         //override path for local src
    //                         path = uri.getPath();
    //                         if (path.startsWith(".")) {
    //                             path = path.substring(1);
    //                         }
    //                     }
    //                 }
    //             } catch (IllegalArgumentException e) {
    //                 //skip invalid source
    //             }
    //         }

    //         if (StringUtils.hasText(source.getBase64())) {
    //             contextSources.add(ContextSource.builder().name(path).base64(source.getBase64()).build());
    //         }

    //         try {
    //             StringWriter writer = new StringWriter();
    //             Mustache handlerMustache = mustacheCache.get(handlerFile);
    //             handlerMustache.execute(
    //                 writer,
    //                 Collections.singletonMap("source", JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(source))
    //             );
    //             writer.flush();

    //             contextSources.add(
    //                 ContextSource
    //                     .builder()
    //                     .name(handlerFile + ".py")
    //                     .base64(Base64.getEncoder().encodeToString(writer.toString().getBytes(StandardCharsets.UTF_8)))
    //                     .build()
    //             );
    //         } catch (IOException ioe) {
    //             throw new CoreRuntimeException("error with reading handler template for runtime-guardrail");
    //         }
    //     }

    //     List<String> requirements = new LinkedList<>();
    //     if (dependencies != null && !dependencies.isEmpty()) {
    //         requirements.addAll(dependencies);
    //     }

    //     if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
    //         requirements.addAll(functionSpec.getRequirements());
    //     }
    //     if (!requirements.isEmpty()) {
    //         //write file
    //         String path = "requirements.txt";
    //         String content = String.join("\n", requirements);
    //         contextSources.add(
    //             ContextSource
    //                 .builder()
    //                 .name(path)
    //                 .base64(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))
    //                 .build()
    //         );
    //     }
    //     return contextSources;
    // }
    public static List<ContextSource> createContextSources(
        @NotNull String entrypoint,
        @NotNull String handlerFile,
        @NotNull String nuclioFunction,
        @Nullable PythonSourceCode source,
        @Nullable List<String> dependencies
    ) {
        List<ContextSource> contextSources = new ArrayList<>();

        //write entrypoint
        ContextSource entry = ContextSource
            .builder()
            .name("entrypoint.sh")
            .base64(Base64.getEncoder().encodeToString(entrypoint.getBytes(StandardCharsets.UTF_8)))
            .build();
        contextSources.add(entry);

        //write handler file
        contextSources.add(
            ContextSource
                .builder()
                .name("handler.py")
                .base64(Base64.getEncoder().encodeToString(handlerFile.getBytes(StandardCharsets.UTF_8)))
                .build()
        );

        //write function file
        contextSources.add(
            ContextSource
                .builder()
                .name("function.yaml")
                .base64(Base64.getEncoder().encodeToString(nuclioFunction.getBytes(StandardCharsets.UTF_8)))
                .build()
        );

        //write source file if present
        if (source != null) {
            String path = "main.py";

            if (StringUtils.hasText(source.getSource())) {
                try {
                    //evaluate if local path (no scheme)
                    UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                    String scheme = uri.getScheme();

                    if (scheme == null && uri.getPath() != null) {
                        //override path for local src
                        path = uri.getPath();
                        if (path.startsWith(".")) {
                            path = path.substring(1);
                        }
                    }
                } catch (IllegalArgumentException e) {
                    //skip invalid source
                }
            }

            if (StringUtils.hasText(source.getBase64())) {
                contextSources.add(ContextSource.builder().name(path).base64(source.getBase64()).build());
            }
        }

        List<String> requirements = new LinkedList<>();
        if (dependencies != null && !dependencies.isEmpty()) {
            requirements.addAll(dependencies);
        }

        if (!requirements.isEmpty()) {
            //write file
            String path = "requirements.txt";
            String content = String.join("\n", requirements);

            contextSources.add(
                ContextSource
                    .builder()
                    .name(path)
                    .base64(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))
                    .build()
            );
        }

        return contextSources;
    }

    /**
     * Generate context refs for the given function spec
     * @param functionSpec
     * @return
     */
    public static List<ContextRef> createContextRefs(PythonSourceCode source) {
        if (source != null && StringUtils.hasText(source.getSource())) {
            try {
                //evaluate if local path (no scheme)
                UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                String scheme = uri.getScheme();

                if (scheme != null) {
                    //write as ref
                    return Collections.singletonList(ContextRef.from(source.getSource()));
                }
            } catch (IllegalArgumentException e) {
                //skip invalid source
            }
        }

        return List.of();
    }

    public static List<String> buildEntrypointArgs(
        String basePath,
        String processor,
        String uvPath,
        List<String> commonRequirementsPath,
        String wheelPath
    ) {
        List<String> args = new ArrayList<>();
        args.addAll(
            List.of(
                basePath + "/entrypoint.sh",
                "--processor",
                processor,
                "--config",
                basePath + "/function.yaml",
                "--requirements",
                basePath + "/requirements.txt"
            )
        );
        if (commonRequirementsPath != null && !commonRequirementsPath.isEmpty()) {
            args.addAll(List.of("--common_requirements", String.join(",", commonRequirementsPath)));
        }
        if (uvPath != null) {
            args.addAll(List.of("--uv_path", uvPath));
        }
        if (wheelPath != null) {
            args.addAll(List.of("--wheel_path", wheelPath));
        }
        return args;
    }

    public static CoreVolume createServerlessImageVolume(String imageName) {
        CoreVolume volume = new CoreVolume();
        volume.setName("serverless-image-volume");
        volume.setVolumeType(CoreVolume.VolumeType.image);
        volume.setMountPath("/opt/nuclio");
        Map<String, String> spec = new HashMap<>();
        spec.put("image", imageName);
        spec.put("subPath", "opt/nuclio");
        volume.setSpec(spec);
        return volume;
    }
}
