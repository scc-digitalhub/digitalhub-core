package it.smartcommunitylabdhub.envoygw;

import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.type.TypeReference;

import it.smartcommunitylabdhub.commons.accessors.spec.TaskSpecAccessor;
import it.smartcommunitylabdhub.commons.annotations.common.ProcessorType;
import it.smartcommunitylabdhub.commons.exceptions.CoreRuntimeException;
import it.smartcommunitylabdhub.commons.infrastructure.Processor;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.envoygw.model.ExtProcService;
import it.smartcommunitylabdhub.envoygw.model.GatewayInfo;
import it.smartcommunitylabdhub.envoygw.model.GenAIModelService;
import it.smartcommunitylabdhub.envoygw.model.GenericService;
import it.smartcommunitylabdhub.envoygw.specs.GatewayExtensionSpec;
import it.smartcommunitylabdhub.envoygw.specs.GatewayRunStatus;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import it.smartcommunitylabdhub.framework.k8s.model.K8sServiceInfo;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.RunManager;
import it.smartcommunitylabdhub.runs.specs.RunBaseStatus;
import lombok.extern.slf4j.Slf4j;
import it.smartcommunitylabdhub.commons.models.status.Status;

@ProcessorType(
    stages = { "onRunning" },
    type = Run.class,
    spec = Status.class
)@Component
@Slf4j
public class ServiceRunningProcessor implements Processor<Run, RunBaseStatus> {

    protected static final TypeReference<Map<String, Serializable>> typeRef = new TypeReference<
        Map<String, Serializable>
    >() {};


    private final RunManager runService;
    private final GatewayCRManager gatewayCRManager;
    private final K8sCRFramework k8sCRFramework;

    public ServiceRunningProcessor(RunManager runService, GatewayCRManager gatewayCRManager, K8sCRFramework k8sCRFramework) {
        this.runService = runService;
        this.gatewayCRManager = gatewayCRManager;
        this.k8sCRFramework = k8sCRFramework;
    }


    @SuppressWarnings("unchecked")
    @Override
    public <I> RunBaseStatus process(String stage, Run run, I input) throws CoreRuntimeException {

        try {
            //read event
            String id = run.getId();

            Map<String, Serializable> status = run.getStatus();

            // check run has service info and has not been populated, otherwise ignore
            if (status != null && status.get("service") != null && status.get("gatewayInfo") == null) {
                // Use service to retrieve the full run and check if state is changed
                run = runService.getRun(id);
                // - check if extensions has envoygw, otherwise ignore
                Optional<Map<String, Serializable>> extension = run.getExtensions().stream().filter(e -> GatewayExtensionSpec.KIND.equals(e.get("kind"))).findAny();
                
                if (extension.isPresent()) {
                    GatewayExtensionSpec   gatewayExtensionSpec = GatewayExtensionSpec.with((Map<String, Serializable>)extension.get().get("spec"));

                    // if already has gateway info, ignore
                    if (status.get("gatewayInfo") == null) {
                        // - if not, create gateway info and update run status
                        K8sServiceInfo serviceInfo = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(status.get("service"), K8sServiceInfo.class);
                        TaskSpecAccessor taskAccessor = TaskSpecAccessor.with(run.getSpec());

                        List<K8sCRRunnable> runnables = new LinkedList<>();

                        GatewayInfo gatewayInfo = null;
                        GenericService service = null;
                        // Based on openai status component
                        if (status.containsKey("openai")) {
                            Map<String, Serializable> openaiStatus = (Map<String, Serializable>) status.get("openai");
                            String baseUrl = openaiStatus.containsKey("baseUrl") ? (String) openaiStatus.get("baseUrl") : serviceInfo.getUrl();
                            URL serviceUrl = getServiceUrl(baseUrl);
                            if (serviceUrl == null) {
                                log.error("Invalid service URL: {}", baseUrl);
                                return null;
                            }   

                            String serviceHost =  serviceUrl.getHost();
                            Integer servicePort = serviceUrl.getPort();
                            String path = serviceUrl.getPath();
                            if (!StringUtils.hasText(path)) {
                                path = "/v1";
                            }

                            gatewayInfo = gatewayCRManager.getGenAIGatewayInfo();
                            service = new GenAIModelService();
                            service.setProjectName(run.getProject());
                            service.setServiceId(run.getId());
                            service.setFunctionName(taskAccessor.getFunction());
                            service.setServiceHost(serviceHost);
                            service.setServicePort(servicePort);
                            service.setPath(path);

                            if (openaiStatus.containsKey("model")) {
                                ((GenAIModelService)service).setModelName((String) openaiStatus.get("model"));
                            } else {
                                ((GenAIModelService)service).setModelName(taskAccessor.getFunction());
                            }
                            ((GenAIModelService)service).setSchemaName("OpenAI");
                            runnables.addAll(gatewayCRManager.createGenAIRunnables(taskAccessor.getRuntime(), run.getKind(), (GenAIModelService)service));

                        } else {
                            URL serviceUrl = getServiceUrl(serviceInfo.getUrl());
                            if (serviceUrl == null) {
                                log.error("Invalid service URL: {}", serviceInfo.getUrl());
                                return null;
                            }   

                            String serviceHost =  serviceUrl.getHost();
                            Integer servicePort = serviceUrl.getPort();
                            String path = serviceUrl.getPath();
                            if (!StringUtils.hasText(path)) {
                                path = "";
                            }

                            gatewayInfo = gatewayCRManager.getGenericGatewayInfo();
                            service = new GenericService();
                            service.setProjectName(run.getProject());
                            service.setServiceId(run.getId());
                            service.setFunctionName(taskAccessor.getFunction());
                            service.setServiceHost(serviceHost);
                            service.setServicePort(servicePort);
                            service.setPath(path);

                            runnables.addAll(gatewayCRManager.createGenericServiceRunnables(taskAccessor.getRuntime(), run.getKind(), service));
                        }

                        List<ExtProcService> extProcServices = new LinkedList<>();
                        // if guardrails are requested
                        if (gatewayExtensionSpec.getGuardrails() != null && !gatewayExtensionSpec.getGuardrails().isEmpty()) {
                            for (int i = 0; i < gatewayExtensionSpec.getGuardrails().size(); i++) {
                                String guardrail = gatewayExtensionSpec.getGuardrails().get(i);
                                ExtProcService extProcService = new ExtProcService();
                                URL extProcUrl = getServiceUrl(guardrail);
                                extProcService.setServiceHost(extProcUrl.getHost());
                                extProcService.setServicePort(extProcUrl.getPort());
                                extProcServices.add(extProcService);
                            }
                        }

                        // TODO enable when payload logger is supported again
                        boolean enablePayloadLogging = false; //Boolean.TRUE.equals(gatewayExtensionSpec.getEnabledPayloadLogging());
                        runnables.addAll(gatewayCRManager.createExtensionPolicies(taskAccessor.getRuntime(), run.getKind(), service, enablePayloadLogging, extProcServices));

                        GatewayRunStatus gatewayRunStatus = new GatewayRunStatus();
                        gatewayRunStatus.setRunnables(runnables);
                        gatewayRunStatus.setGatewayInfo(gatewayInfo);

                        for (K8sCRRunnable crRunnable : runnables) {
                            k8sCRFramework.run(crRunnable);
                        }
                        return gatewayRunStatus;
                    }
                }
            }

        } catch (Exception e) {
            log.error("Error handling runnable changed event: {}", e.getMessage(), e);
        }
        return null;
    }

    protected URL getServiceUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return URI.create(url).toURL();
        } catch (Exception e) {
            try {
                return URI.create("http://" + url).toURL();
            } catch (MalformedURLException e1) {
                return null;
            }
        }
    }
}
