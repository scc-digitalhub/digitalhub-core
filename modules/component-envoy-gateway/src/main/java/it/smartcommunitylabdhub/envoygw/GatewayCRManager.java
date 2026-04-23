/*
 * Copyright © 2026 DSLab – Fondazione Bruno Kessler and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.envoygw;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.envoygw.config.EnvoyGwProperties;
import it.smartcommunitylabdhub.envoygw.config.PayloadLoggerProperties;
import it.smartcommunitylabdhub.envoygw.model.ExtProcService;
import it.smartcommunitylabdhub.envoygw.model.GatewayInfo;
import it.smartcommunitylabdhub.envoygw.model.GenAIModelService;
import it.smartcommunitylabdhub.envoygw.model.GenericService;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Slf4j
public class GatewayCRManager implements InitializingBean {

    private final EnvoyGwProperties envoyGwProperties;
    private PayloadLoggerProperties payloadLoggerProperties;

    @Autowired
    ResourceLoader resourceLoader;

    @Value("${kubernetes.namespace}")
    private String namespace;

    private static final String AIGATEWAY_API_GROUP = "aigateway.envoyproxy.io";
    private static final String AIGATEWAY_API_VERSION = "v1alpha1";

    private Mustache aigatewayrouteMustache;
    private static final String AIGATEWAY_API_KIND = "AIGatewayRoute";
    private static final String AIGATEWAY_API_PLURAL = "aigatewayroutes";

    private Mustache aibackendMustache;
    private static final String AIBACKEND_API_KIND = "AIServiceBackend";
    private static final String AIBACKEND_API_PLURAL = "aiservicebackends";

    private Mustache backendMustache;
    private static final String BACKEND_API_KIND = "Backend";
    private static final String BACKEND_API_PLURAL = "backends";
    private static final String BACKEND_API_GROUP = "gateway.envoyproxy.io";
    private static final String BACKEND_API_VERSION = "v1alpha1";

    private Mustache genericHttpRouteMustache;
    private static final String GENERIC_HTTPROUTE_API_KIND = "HTTPRoute";
    private static final String GENERIC_HTTPROUTE_API_PLURAL = "httproutes";
    private static final String GENERIC_HTTPROUTE_API_GROUP = "gateway.networking.k8s.io";
    private static final String GENERIC_HTTPROUTE_API_VERSION = "v1";

    private Mustache extPolicyMustache;
    private static final String EXTPOLICY_API_KIND = "EnvoyExtensionPolicy";
    private static final String EXTPOLICY_API_PLURAL = "envoyextensionpolicies";
    private static final String EXTPOLICY_API_GROUP = "gateway.envoyproxy.io";
    private static final String EXTPOLICY_API_VERSION = "v1alpha1";

    private MustacheFactory mustacheFactory = new DefaultMustacheFactory();

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    private static final ObjectMapper objectMapper = JacksonMapper.YAML_OBJECT_MAPPER;

    public GatewayCRManager(EnvoyGwProperties envoyGwProperties) {
        Assert.notNull(envoyGwProperties, "envoyGwProperties is required");
        if (log.isTraceEnabled()) {
            log.trace("GatewayCRManager created with properties: {}", envoyGwProperties.toString());
        }

        this.envoyGwProperties = envoyGwProperties;
    }

    @Autowired(required = false)
    public void setPayloadLoggerProperties(PayloadLoggerProperties payloadLoggerProperties) {
        this.payloadLoggerProperties = payloadLoggerProperties;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("Loading templates from resources...");

        aibackendMustache = loadTemplate("aibackend");
        aigatewayrouteMustache = loadTemplate("aigatewayroute");
        backendMustache = loadTemplate("backend");
        extPolicyMustache = loadTemplate("envoy-policy");
        genericHttpRouteMustache = loadTemplate("generic-httproute");

        log.debug("Templates loaded successfully");
    }

    /**
     * Create K8sCRRunnables corresponding to Envoy AI Gateway Custom resources for GenAI Model Service. Specifically,
     * it creates the following CRs:
     * - AIGatewayRoute
     * - AIBackend
     * - Backend
     * @param runtime
     * @param task
     * @param serviceId
     * @param service
     * @return
     * @throws IOException
     */
    public List<K8sCRRunnable> createGenAIRunnables(String runtime, String task, GenAIModelService service)
        throws IOException {
        Assert.hasText(runtime, "runtime is required");
        Assert.hasText(task, "task is required");
        Assert.notNull(service, "service is required");

        String serviceId = service.getServiceId();
        log.debug("createGenAIRunnables for runtime {} task {} serviceId {}", runtime, task, serviceId);

        List<K8sCRRunnable> runnables = new LinkedList<>();

        String backendName = serviceId + "-backend";
        String aiBackendName = serviceId + "-aibackend";
        String routeName = serviceId + "-httproute";

        // Backend CR
        Map<String, Object> context = new HashMap<>();
        context.put("serviceHost", namespacedHostName(service.getServiceHost()));
        context.put("servicePort", service.getServicePort());
        K8sCRRunnable backendCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(backendName)
            .apiGroup(BACKEND_API_GROUP)
            .apiVersion(BACKEND_API_VERSION)
            .kind(BACKEND_API_KIND)
            .plural(BACKEND_API_PLURAL)
            .spec(generateSpec(backendMustache, context))
            .build();
        backendCR.setId(backendName);
        backendCR.setProject(service.getProjectName());
        runnables.add(backendCR);

        // AIBackend CR
        context.clear();
        context.put("schemaName", service.getSchemaName());
        context.put("backendName", backendName);
        if (StringUtils.hasText(service.getPath())) {
            context.put("schemaPrefix", service.getPath());
        } else {
            context.put("schemaPrefix", "");
        }
        K8sCRRunnable aibackendCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(aiBackendName)
            .apiGroup(AIGATEWAY_API_GROUP)
            .apiVersion(AIGATEWAY_API_VERSION)
            .kind(AIBACKEND_API_KIND)
            .plural(AIBACKEND_API_PLURAL)
            .spec(generateSpec(aibackendMustache, context))
            .build();
        aibackendCR.setId(aiBackendName);
        aibackendCR.setProject(service.getProjectName());
        runnables.add(aibackendCR);

        // AIGatewayRoute CR
        context.clear();
        context.put("aiGatewayName", envoyGwProperties.getAiGateway().getName());
        context.put("modelName", service.getModelName());
        context.put("aiBackendName", aiBackendName);
        if (StringUtils.hasText(service.getPath())) {
            context.put("schemaPrefix", service.getPath());
        } else {
            context.put("schemaPrefix", "");
        }
        K8sCRRunnable aigatewayrouteCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(routeName)
            .apiGroup(AIGATEWAY_API_GROUP)
            .apiVersion(AIGATEWAY_API_VERSION)
            .kind(AIGATEWAY_API_KIND)
            .plural(AIGATEWAY_API_PLURAL)
            .spec(generateSpec(aigatewayrouteMustache, context))
            .build();
        aigatewayrouteCR.setId(routeName);
        aigatewayrouteCR.setProject(service.getProjectName());
        runnables.add(aigatewayrouteCR);

        return runnables;
    }

    /**
     * Create K8sCRRunnables corresponding to Envoy Generic Gateway Custom resources for Generic Service.
     * Specifically, it creates the following CRs:
     * - HTTPRoute
     * - Backend
     * @param runtime
     * @param task
     * @param service
     * @return
     * @throws IOException
     */
    public List<K8sCRRunnable> createGenericServiceRunnables(String runtime, String task, GenericService service)
        throws IOException {
        Assert.hasText(runtime, "runtime is required");
        Assert.hasText(task, "task is required");
        Assert.notNull(service, "service is required");

        String serviceId = service.getServiceId();
        log.debug("createGenericServiceRunnables for runtime {} task {} serviceId {}", runtime, task, serviceId);

        List<K8sCRRunnable> runnables = new LinkedList<>();

        String backendName = serviceId + "-backend";
        String routeName = serviceId + "-httproute";

        Map<String, Object> context = new HashMap<>();
        context.put("serviceHost", namespacedHostName(service.getServiceHost()));
        context.put("servicePort", service.getServicePort());
        K8sCRRunnable backendCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(backendName)
            .apiGroup(BACKEND_API_GROUP)
            .apiVersion(BACKEND_API_VERSION)
            .kind(BACKEND_API_KIND)
            .plural(BACKEND_API_PLURAL)
            .spec(generateSpec(backendMustache, context))
            .build();
        backendCR.setId(backendName);
        backendCR.setProject(service.getProjectName());
        runnables.add(backendCR);

        // HTTPRoute CR
        context.clear();
        context.put("genericGatewayName", envoyGwProperties.getGenericGateway().getName());
        context.put("backendName", backendName);
        context.put("servicePath", serviceId);
        context.put("path", service.getPath());

        K8sCRRunnable genericHttpRouteCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(routeName)
            .apiGroup(GENERIC_HTTPROUTE_API_GROUP)
            .apiVersion(GENERIC_HTTPROUTE_API_VERSION)
            .kind(GENERIC_HTTPROUTE_API_KIND)
            .plural(GENERIC_HTTPROUTE_API_PLURAL)
            .spec(generateSpec(genericHttpRouteMustache, context))
            .build();
        genericHttpRouteCR.setId(routeName);
        genericHttpRouteCR.setProject(service.getProjectName());
        runnables.add(genericHttpRouteCR);

        return runnables;
    }

    /**
     * Create K8sCRRunnables corresponding to Envoy Extension Policy Custom resources for Services. Specifically,
     * it creates the following CRs:
     * - EnvoyExtensionPolicy
     * @param runtime
     * @param task
     * @param service
     * @return
     * @throws IOException
     */
    public List<K8sCRRunnable> createExtensionPolicies(
        String runtime,
        String task,
        GenericService service,
        boolean withPayloadLogger,
        List<ExtProcService> extProcServices
    ) throws IOException {
        Assert.hasText(runtime, "runtime is required");
        Assert.hasText(task, "task is required");
        Assert.notNull(service, "service is required");
        if (!withPayloadLogger && (extProcServices == null || extProcServices.isEmpty())) {
            log.debug(
                "No extensions enabled for service {}, skipping creation of EnvoyExtensionPolicy",
                service.getServiceId()
            );
            return List.of();
        }
        // prepare context for EnvoyExtensionPolicy template
        String serviceId = service.getServiceId();
        Map<String, Object> context = new HashMap<>();
        context.put("routeName", serviceId + "-httproute");
        context.put("projectName", service.getProjectName());
        context.put("serviceId", serviceId);
        context.put("functionName", service.getFunctionName());

        if (withPayloadLogger) {
            context.put("payloadLoggerHost", localizeHostName(payloadLoggerProperties.getHost()));
            context.put("payloadLoggerPort", payloadLoggerProperties.getPort());
            context.put("payloadLoggerEnabled", true);
        }
        if (extProcServices != null && !extProcServices.isEmpty()) {
            List<Map<String, Object>> extProcs = new LinkedList<>();
            for (ExtProcService extProcService : extProcServices) {
                Map<String, Object> extProcContext = new HashMap<>();
                extProcContext.put("extProcHost", localizeHostName(extProcService.getServiceHost()));
                extProcContext.put("extProcPort", extProcService.getServicePort());
                extProcs.add(extProcContext);
            }
            context.put("extProcs", extProcs);
        }

        String payloadLoggerName = serviceId + "-extension-policy";
        Map<String, Serializable> spec = generateSpec(extPolicyMustache, context);

        List<K8sCRRunnable> runnables = new LinkedList<>();
        K8sCRRunnable payloadLoggerCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(payloadLoggerName)
            .apiGroup(EXTPOLICY_API_GROUP)
            .apiVersion(EXTPOLICY_API_VERSION)
            .kind(EXTPOLICY_API_KIND)
            .plural(EXTPOLICY_API_PLURAL)
            .spec(spec)
            .build();
        payloadLoggerCR.setId(payloadLoggerName);
        payloadLoggerCR.setProject(service.getProjectName());
        runnables.add(payloadLoggerCR);

        return runnables;
    }

    /**
     * Get Gateway Info for GenAI Gateway
     * @return
     */
    public GatewayInfo getGenAIGatewayInfo() {
        return GatewayInfo
            .builder()
            .gatewayName(envoyGwProperties.getAiGateway().getName())
            .gatewayEndpoint(envoyGwProperties.getAiGateway().getEndpoint())
            .build();
    }

    /**
     * Get Gateway Info for Generic Gateway
     * @return
     */
    public GatewayInfo getGenericGatewayInfo() {
        return GatewayInfo
            .builder()
            .gatewayName(envoyGwProperties.getGenericGateway().getName())
            .gatewayEndpoint(envoyGwProperties.getGenericGateway().getEndpoint())
            .build();
    }

    private Map<String, Serializable> generateSpec(Mustache mustache, Map<String, Object> context) throws IOException {
        StringWriter writer = new StringWriter();
        mustache.execute(writer, context);
        writer.flush();
        String specYaml = writer.toString();
        return objectMapper.readValue(specYaml, typeRef);
    }

    private Mustache loadTemplate(String templateName) throws IOException {
        String resourcePath = String.format("classpath:envoygw/templates/%s.yaml", templateName);
        Resource resource = resourceLoader.getResource(resourcePath);
        return mustacheFactory.compile(new InputStreamReader(resource.getInputStream()), templateName);
    }

    private String localizeHostName(String host) {
        // drop trailing namespace suffix if present (e.g., service.namespace.svc.cluster.local -> service)
        if (host != null && host.endsWith(namespace)) {
            return host.substring(0, host.length() - namespace.length() - 1);
        } else if (host != null && host.endsWith(namespace + ".svc.cluster.local")) {
            // drop trailing svc.cluster.local if present (e.g., service.namespace.svc.cluster.local -> service.namespace)
            return host.substring(0, host.length() - (namespace.length() + ".svc.cluster.local".length()) - 1);
        } else {
            return host;
        }
    }

    private String namespacedHostName(String host) {
        if (host != null && !host.endsWith(namespace) && !host.endsWith(namespace + ".svc.cluster.local")) {
            return host + "." + namespace;
        } else {
            return host;
        }
    }
}
