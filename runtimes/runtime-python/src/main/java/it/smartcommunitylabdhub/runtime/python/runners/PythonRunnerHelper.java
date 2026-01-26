package it.smartcommunitylabdhub.runtime.python.runners;

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
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionBuilder;
import it.smartcommunitylabdhub.runtime.python.model.NuclioFunctionSpec;
import it.smartcommunitylabdhub.runtime.python.model.PythonSourceCode;
import it.smartcommunitylabdhub.runtime.python.specs.PythonFunctionSpec;


public class PythonRunnerHelper {

    protected static MustacheFactory mustacheFactory = new NoEncodingMustacheFactory();

    protected static final String[] HANDLER_FILES = new String[] { "_job_handler", "_serve_handler" };

    protected static final Resource jobHandlerTemplate = new ClassPathResource("runtime-python/docker/_job_handler.py");
    protected static final Resource serveHandlerTemplate = new ClassPathResource("runtime-python/docker/_serve_handler.py");
    protected static Map<String, Mustache> mustacheCache = new HashMap<>();

    static {
        try {
            for (String handlerFile : HANDLER_FILES) {
                mustacheCache.put(handlerFile, mustacheFactory.compile(new InputStreamReader(new ClassPathResource("runtime-python/docker/" + handlerFile + ".py").getInputStream()), handlerFile));
            }
        } catch (IOException e) {
            throw new RuntimeException("Error loading python runner templates", e);
        }
    }


    /**
     * Generate environment variable list for the given run and task spec
     * @param run
     * @param taskSpec
     * @return
     */
    public static List<CoreEnv> createEnvList(Run run, K8sFunctionTaskBaseSpec taskSpec) {
        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );
        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);
        //merge env with PYTHON path override
        coreEnvList.add(new CoreEnv("PYTHONPATH", "${PYTHONPATH}:/shared/"));

        return coreEnvList;
    }

    /**
     * Generate secret environment variable list for the given secret data map
     * @param secretData
     * @return
     */
    public static List<CoreEnv> createSecrets(Map<String, String> secretData) {
        List<CoreEnv> coreSecrets = secretData == null
                ? null
                : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();
        return coreSecrets;
    }

    /**
     * Generate context sources for the given function spec
     * @param functionSpec
     * @param triggerKind
     * @param handler
     * @return
     */
    public static List<ContextSource> createContextSources(PythonFunctionSpec functionSpec, HashMap<String, Serializable> event, Map<String, Serializable> triggers, String handlerFile) {
        List<ContextSource> contextSources = new ArrayList<>();

        // Build Nuclio function
        NuclioFunctionSpec nuclio = NuclioFunctionSpec
            .builder()
            .runtime("python")
            //invoke user code wrapped via default handler
            .handler(handlerFile + ":handler")
            //directly invoke user code
            // .handler("main:" + runSpec.getFunctionSpec().getSource().getHandler())
            .triggers(triggers)
            .event(event)
            .build();

        String nuclioFunction = NuclioFunctionBuilder.write(nuclio);    
        
        ContextSource fn = ContextSource
            .builder()
            .name("function.yaml")
            .base64(Base64.getEncoder().encodeToString(nuclioFunction.getBytes(StandardCharsets.UTF_8)))
            .build();
        contextSources.add(fn);

        if (functionSpec.getSource() != null) {
            PythonSourceCode source = functionSpec.getSource();
            String path = "main.py";

            if (StringUtils.hasText(source.getSource())) {
                try {
                    //evaluate if local path (no scheme)
                    UriComponents uri = UriComponentsBuilder.fromUriString(source.getSource()).build();
                    String scheme = uri.getScheme();

                    if (scheme == null) {
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
                Mustache handlerMustache = mustacheCache.get(handlerFile);
                handlerMustache.execute(writer, Collections.singletonMap("source", JacksonMapper.CUSTOM_OBJECT_MAPPER.writeValueAsString(source)));
                writer.flush();

                contextSources.add(ContextSource
                        .builder()
                        .name(handlerFile + ".py")
                        .base64(Base64.getEncoder().encodeToString(writer.toString().getBytes(StandardCharsets.UTF_8)))
                        .build());
            } catch (IOException ioe) {
                throw new CoreRuntimeException("error with reading handler template for runtime-guardrail");
            }
        }

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
        }
        return contextSources;
    }

    /**
     * Generate context refs for the given function spec
     * @param functionSpec
     * @return
     */
    public static List<ContextRef> createContextRefs(PythonFunctionSpec functionSpec) {
        if (functionSpec.getSource() != null) {
            PythonSourceCode source = functionSpec.getSource();

            if (StringUtils.hasText(source.getSource())) {
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
        }
        return null;
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
