package it.smartcommunitylabdhub.runtime.servicegraph.runners;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.k8s.kubernetes.K8sBuilderHelper;
import it.smartcommunitylabdhub.framework.k8s.model.ContextRef;
import it.smartcommunitylabdhub.framework.k8s.model.ContextSource;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreEnv;
import it.smartcommunitylabdhub.runtime.servicegraph.model.ServicegraphSourceCode;
import it.smartcommunitylabdhub.runtime.servicegraph.specs.ServicegraphFunctionSpec;
import it.smartcommunitylabdhub.runtime.servicegraph.specs.ServicegraphRunSpec;
import it.smartcommunitylabdhub.runtime.servicegraph.specs.ServicegraphServeTaskSpec;

public class ServicegraphBaseRunner {

    protected static final int HTTP_PORT = 8080;
    protected static final int GRPC_PORT = 9000;

    protected final String image;

    protected final String command;

    protected final K8sBuilderHelper k8sBuilderHelper;

    protected record Context(
        List<ContextRef> contextRefs, 
        List<ContextSource> contextSources,
        List<CoreEnv> coreEnvList,
        List<CoreEnv> coreSecrets
    ) {}

    public ServicegraphBaseRunner(
        String image,
        String command,
        K8sBuilderHelper k8sBuilderHelper
    ) {
        this.image = image;
        this.command = command;

        this.k8sBuilderHelper = k8sBuilderHelper;
    }

    protected Context prepareContext(Run run, Map<String, String> secretData, ServicegraphRunSpec runSpec, ServicegraphServeTaskSpec taskSpec, ServicegraphFunctionSpec functionSpec) {

        List<CoreEnv> coreEnvList = new ArrayList<>(
            List.of(new CoreEnv("PROJECT_NAME", run.getProject()), new CoreEnv("RUN_ID", run.getId()))
        );

        List<CoreEnv> coreSecrets = secretData == null
            ? null
            : secretData.entrySet().stream().map(e -> new CoreEnv(e.getKey(), e.getValue())).toList();

        Optional.ofNullable(taskSpec.getEnvs()).ifPresent(coreEnvList::addAll);

        ServicegraphSourceCode servicegraphSourceCode = functionSpec.getDefinition();
        String servicegraphSpec = new String(Base64.getDecoder().decode(servicegraphSourceCode.getBase64()), StandardCharsets.UTF_8);

        //read source and build context
        List<ContextRef> contextRefs = null;
        List<ContextSource> contextSources = new ArrayList<>();

        //function definition
        ContextSource fn = ContextSource
            .builder()
            .name("servicegraph.yaml")
            .base64(Base64.getEncoder().encodeToString(servicegraphSpec.getBytes(StandardCharsets.UTF_8)))
            .build();
        contextSources.add(fn);

        return new Context(contextRefs, contextSources, coreEnvList, coreSecrets);
    } 
}
