package it.smartcommunitylabdhub.runtime.guardrail.runners;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheException;
import com.github.mustachejava.MustacheFactory;

import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.base.K8sFunctionTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.runtime.guardrail.specs.GuardrailFunctionSpec;
import it.smartcommunitylabdhub.runtime.guardrail.specs.GuardrailRunSpec;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionBuilder;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;

public class GuardrailBaseRunner {

    private static final int HTTP_PORT = 5051;

    protected final Map<String, String> images;
    protected final Map<String, String> serverlessImages;
    protected final Map<String, String> baseImages;

    protected final String command;

    protected final K8sBuilderHelper k8sBuilderHelper;
    protected final List<String> dependencies;

    protected MustacheFactory mustacheFactory = new NoEncodingMustacheFactory();
    protected final Resource handlerTemplate = new ClassPathResource("runtime-guardrail/docker/guardrail-handler.py");
    protected Mustache handlerMustache;

    protected record Context(
        List<ContextRef> contextRefs, 
        List<ContextSource> contextSources,
        List<CoreEnv> coreEnvList,
        List<CoreEnv> coreSecrets
    ) {}

    public GuardrailBaseRunner(
        Map<String, String> images,
        Map<String, String> serverlessImages,
        Map<String, String> baseImages,
        String command,
        K8sBuilderHelper k8sBuilderHelper,
        List<String> dependencies
    ) {
        this.images = images;
        this.serverlessImages = serverlessImages;
        this.baseImages = baseImages;
        this.command = command;
        this.dependencies = dependencies;

        this.k8sBuilderHelper = k8sBuilderHelper;
        try {
            this.handlerMustache = mustacheFactory.compile(new InputStreamReader( handlerTemplate.getInputStream()), "guardrail-handler");
        } catch (IOException ioe) {
            throw new CoreRuntimeException("error with reading handler template for runtime-guardrail");
        }
    }

    protected Context prepareContext(Run run, Map<String, String> secretData, GuardrailRunSpec runSpec, K8sFunctionTaskBaseSpec taskSpec, GuardrailFunctionSpec functionSpec) {


        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        //define extproc trigger
        HashMap<String, Serializable> triggers = new HashMap<>();
        HashMap<String, Serializable> attributes = new HashMap<>();
        attributes.put("type", functionSpec.getProcessingMode().getMode());
        attributes.put("port", HTTP_PORT);
        HashMap<String, Serializable> extproc = new HashMap<>(Map.of("kind", "extproc", "maxWorkers", 4, "attributes", attributes));
        triggers.put("extproc", extproc);
        NuclioFunctionSpec nuclio = NuclioFunctionSpec
            .builder()
            .runtime("python")
            //invoke user code wrapped via default handler
            .handler("_guardrail_handler:handler_serve")
            .triggers(triggers)
            .build();

        String nuclioFunction = NuclioFunctionBuilder.write(nuclio);

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = new ArrayList<>();

        //function definition
        ContextSource fn = ContextSource
            .builder()
            .name("function.yaml")
            .base64(Base64.getEncoder().encodeToString(nuclioFunction.getBytes(StandardCharsets.UTF_8)))
            .build();
        contextSources.add(fn);

        //source
        if (functionSpec.getSource() != null) {
            PythonSourceCode source = functionSpec.getSource();
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

            try {            
                StringWriter writer = new StringWriter();
                handlerMustache.execute(writer, Collections.singletonMap("source", JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(source)));
                writer.flush();

                contextSources.add(ContextSource
                        .builder()
                        .name("_guardrail_handler.py")
                        .base64(Base64.getEncoder().encodeToString(writer.toString().getBytes(StandardCharsets.UTF_8)))
                        .build());
            } catch (IOException ioe) {
                throw new CoreRuntimeException("error with reading handler template for runtime-guardrail");
            }
        }

        List<String> requirements = new LinkedList<>();
        if (dependencies != null && !dependencies.isEmpty()) {
            requirements.addAll(dependencies);
        }

        if (functionSpec.getRequirements() != null && !functionSpec.getRequirements().isEmpty()) {
            requirements.addAll(functionSpec.getRequirements());
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

        //merge env with PYTHON path override
        coreEnvList.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:/shared/"));

        return new Context(contextRefs, contextSources, coreEnvList, coreSecrets);
    } 

    private static class NoEncodingMustacheFactory extends DefaultMustacheFactory {

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
