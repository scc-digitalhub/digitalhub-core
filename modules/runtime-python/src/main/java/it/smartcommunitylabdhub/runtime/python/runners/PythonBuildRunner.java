package it.smartcommunitylabdhub.runtime.python.runners;

import it.smartcommunitylabdhub.commons.accessors.spec.RunSpecAccessor;
import it.smartcommunitylabdhub.commons.infrastructure.Runner;
import it.smartcommunitylabdhub.commons.models.entities.run.Run;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.objects.SourceCode;
import it.smartcommunitylabdhub.commons.models.utils.RunUtils;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGenerator;
import it.smartcommunitylabdhub.framework.kaniko.infrastructure.docker.DockerfileGeneratorFactory;
import it.smartcommunitylabdhub.framework.kaniko.runnables.K8sKanikoRunnable;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionBuilder;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonBuildTaskSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec.PythonSourceCodeLanguages;
import it.smartcommunitylabdhub.runtime.python.specs.PythonRunSpec;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

@Slf4j
public class PythonBuildRunner implements Runner<K8sKanikoRunnable> {

    private final String image;
    private final String command;
    private final PythonFunctionSpec functionSpec;
    private final Map<String, Set<String>> groupedSecrets;

    public PythonBuildRunner(
        String image,
        String command,
        PythonFunctionSpec functionPythonSpec,
        Map<String, Set<String>> groupedSecrets
    ) {
        this.image = image;
        this.command = command;
        this.functionSpec = functionPythonSpec;
        this.groupedSecrets = groupedSecrets;
    }

    @Override
    public K8sKanikoRunnable produce(Run run) {
        PythonRunSpec runSpec = new PythonRunSpec(run.getSpec());
        PythonBuildTaskSpec taskSpec = runSpec.getTaskBuildSpec();

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        // Generate docker file
        DockerfileGeneratorFactory dockerfileGenerator = DockerfileGenerator.factory();

        String baseImage = StringUtils.hasText(functionSpec.getBaseImage()) ? functionSpec.getBaseImage() : image;

        // Add base Image
        dockerfileGenerator.from(baseImage);

        // Copy toolkit from builder if required
        if (!image.equals(baseImage)) {
            dockerfileGenerator.copy("--from=" + image + " /opt/nuclio/", "/opt/nuclio/");
            dockerfileGenerator.copy(
                "--from=" + image + " /usr/local/bin/processor  /usr/local/bin/uhttpc",
                "/usr/local/bin/"
            );
        }

        // Copy /shared folder (as workdir)
        dockerfileGenerator.copy(".", "/shared");

        // Build Nuclio function
        //define http trigger
        //TODO use proper model
        HashMap<String, Serializable> triggers = new HashMap<>();
        HashMap<String, Serializable> http = new HashMap<>(Map.of("kind", "http", "maxWorkers", 2));
        triggers.put("http", http);
        NuclioFunctionSpec nuclio = NuclioFunctionSpec
            .builder()
            .runtime("python")
            //invoke user code wrapped via default handler
            .handler("run_handler:handler_serve")
            //directly invoke user code
            // .handler("main:" + runSpec.getFunctionSpec().getSource().getHandler())
            .triggers(triggers)
            .build();

        String nuclioFunction = NuclioFunctionBuilder.write(nuclio);

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = new ArrayList<>();
        ContextSource fn = ContextSource
            .builder()
            .name("function.yaml")
            .base64(Base64.getUrlEncoder().encodeToString(nuclioFunction.getBytes(StandardCharsets.UTF_8)))
            .build();
        contextSources.add(fn);

        if (functionSpec.getSource() != null) {
            SourceCode<PythonSourceCodeLanguages> source = functionSpec.getSource();
            String path = "main.py";

            if (StringUtils.hasText(source.getSource())) {
                try {
                    //evaluate if local path (no scheme)
                    UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                    String scheme = uri.getScheme();

                    if (scheme != null) {
                        //write as ref
                        contextRefs = Collections.singletonList(ContextRef.from(source.getSource()));
                    } else {
                        if (StringUtils.hasText(path)) {
                            //override path for local src
                            path = uri.getPath();
                            if (path.startsWith(".")) {
                                path = path.substring(1);
                            }
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

        // install all requirements
        dockerfileGenerator.run(
            "python /opt/nuclio/whl/$(basename /opt/nuclio/whl/pip-*.whl)/pip install pip --no-index --find-links /opt/nuclio/whl " +
            "&& python -m pip install -r /opt/nuclio/requirements/common.txt" +
            "&& python -m pip install -r /opt/nuclio/requirements/" +
            functionSpec.getPythonVersion().name().toLowerCase() +
            ".txt"
        );

        // Add user instructions
        Optional
            .ofNullable(taskSpec.getInstructions())
            .ifPresent(instructions -> instructions.forEach(i -> dockerfileGenerator.run(i)));

        // If requirements.txt are defined add to build
        if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
            //write file
            String path = "requirements.txt";
            String content = String.join("\n", functionSpec.getRequirements());
            contextSources.add(
                ContextSource
                    .builder()
                    .name(path)
                    .base64(Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8)))
                    .build()
            );
            // install all requirements
            dockerfileGenerator.run("python -m pip install -r /shared/requirements.txt");
        }

        // Set entry point
        dockerfileGenerator.entrypoint(List.of(command));

        // Generate string docker file
        String dockerfile = dockerfileGenerator.build().generate();

        //merge env with PYTHON path override
        coreEnvList.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:/shared/"));

        // Parse run spec
        RunSpecAccessor runSpecAccessor = RunUtils.parseTask(runSpec.getTask());

        return K8sKanikoRunnable
            .builder()
            .id(run.getId())
            .project(run.getProject())
            .runtime(PythonRuntime.RUNTIME)
            .task(PythonBuildTaskSpec.KIND)
            .state(State.READY.name())
            //base
            .image(
                StringUtils.hasText(functionSpec.getImage())
                    ? functionSpec.getImage()
                    : runSpecAccessor.getProject() + "-" + runSpecAccessor.getFunction()
            )
            .contextRefs(contextRefs)
            .contextSources(contextSources)
            .envs(coreEnvList)
            .secrets(groupedSecrets)
            .resources(taskSpec.getResources())
            .volumes(taskSpec.getVolumes())
            .nodeSelector(taskSpec.getNodeSelector())
            .affinity(taskSpec.getAffinity())
            .tolerations(taskSpec.getTolerations())
            // Task Specific
            .dockerFile(dockerfile)
            //specific
            .backoffLimit(0)
            .build();
    }
}
