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
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

@Slf4j
public class GatewayCRManager implements InitializingBean {

    private final EnvoyGwProperties envoyGwProperties;
    private PayloadLoggerProperties payloadLoggerProperties;

    @Autowired
    ResourceLoader resourceLoader;

    // @Value("classpath:envoygw/templates/aigatewayroute.yml")
    // private Resource aigatewayrouteTemplate;

    // @Value("classpath:envoygw/templates/aibackend.yaml")
    // private Resource aibackendTemplate;

    // @Value("classpath:envoygw/templates/backend.yml")
    // private Resource backendTemplate;

    // @Value("classpath:envoygw/templates/generic-httproute.yml")
    // private Resource genericHttpRouteTemplate;

    // @Value("classpath:envoygw/templates/payload-extension.yml")
    // private Resource payloadLoggerExtProcTemplate;

    // @Value("classpath:envoygw/templates/extproc-extension.yml")
    // private Resource extProcTemplate;

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
    private static final String GENERIC_HTTPROUTE_API_GROUP = "gateway.envoyproxy.io";
    private static final String GENERIC_HTTPROUTE_API_VERSION = "v1";

    private Mustache payloadLoggerExtProcMustache;
    private static final String PAYLOADLOGGER_EXTPROC_API_KIND = "EnvoyExtensionPolicy";
    private static final String PAYLOADLOGGER_EXTPROC_API_PLURAL = "envoyextensionpolicies";
    private static final String PAYLOADLOGGER_EXTPROC_API_GROUP = "gateway.envoyproxy.io";
    private static final String PAYLOADLOGGER_EXTPROC_API_VERSION = "v1alpha1";

    private Mustache extProcMustache;
    private static final String EXTPROC_API_KIND = "EnvoyExtensionPolicy";
    private static final String EXTPROC_API_PLURAL = "envoyextensionpolicies";
    private static final String EXTPROC_API_GROUP = "gateway.envoyproxy.io";
    private static final String EXTPROC_API_VERSION = "v1alpha1";

    private MustacheFactory mustacheFactory = new DefaultMustacheFactory();

    private static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};

    private static final ObjectMapper objectMapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;

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
        extProcMustache = loadTemplate("extproc-extension");
        genericHttpRouteMustache = loadTemplate("generic-httproute");
        payloadLoggerExtProcMustache = loadTemplate("payload-extension");

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

        // AIGateway Route CR
        Map<String, Serializable> context = new HashMap<>();
        context.put("aiGatewayName", envoyGwProperties.getAiGateway().getName());
        context.put("modelName", service.getModelName());
        context.put("aiBackendName", aiBackendName);
        K8sCRRunnable aigatewayrouteCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(serviceId + "-aigatewayroute")
            .apiGroup(AIGATEWAY_API_GROUP)
            .apiVersion(AIGATEWAY_API_VERSION)
            .kind(AIGATEWAY_API_KIND)
            .plural(AIGATEWAY_API_PLURAL)
            .spec(generateSpec(aigatewayrouteMustache, context))
            .build();
        runnables.add(aigatewayrouteCR);

        // AIBackend CR
        context.clear();
        context.put("schemaName", service.getSchemaName());
        context.put("backendName", backendName);
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
        runnables.add(aibackendCR);

        // Backend CR
        context.clear();
        context.put("serviceHost", service.getServiceHost());
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
        runnables.add(backendCR);

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
        // HTTPRoute CR
        Map<String, Serializable> context = new HashMap<>();
        context.put("genericGatewayName", envoyGwProperties.getGenericGateway().getName());
        context.put("backendName", backendName);
        context.put("servicePath", serviceId);

        K8sCRRunnable genericHttpRouteCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(serviceId + "-httproute")
            .apiGroup(GENERIC_HTTPROUTE_API_GROUP)
            .apiVersion(GENERIC_HTTPROUTE_API_VERSION)
            .kind(GENERIC_HTTPROUTE_API_KIND)
            .plural(GENERIC_HTTPROUTE_API_PLURAL)
            .spec(generateSpec(genericHttpRouteMustache, context))
            .build();
        runnables.add(genericHttpRouteCR);

        // Backend CR
        context.clear();
        context.put("serviceHost", service.getServiceHost());
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
        runnables.add(backendCR);

        return runnables;
    }

    /**
     * Create K8sCRRunnables corresponding to Envoy Extension Processor Custom resources for Payload Logger.
     * Specifically, it creates the following CRs:
     * - EnvoyExtensionPolicy
     * @param runtime
     * @param task
     * @param service
     * @return
     * @throws IOException
     */
    public List<K8sCRRunnable> createServicePayloadLoggerRunnables(String runtime, String task, GenericService service)
        throws IOException {
        Assert.hasText(runtime, "runtime is required");
        Assert.hasText(task, "task is required");
        Assert.notNull(service, "service is required");
        if (payloadLoggerProperties == null || !payloadLoggerProperties.isEnabled()) {
            log.warn("Payload Logger extension is not enabled, skipping creation of Payload Logger CRs");
            return List.of();
        }

        String serviceId = service.getServiceId();
        log.debug("createServicePayloadLoggerRunnables for runtime {} task {} serviceId {}", runtime, task, serviceId);

        List<K8sCRRunnable> runnables = new LinkedList<>();

        // Payload Logger Route CR
        Map<String, Serializable> context = new HashMap<>();
        context.put("routeName", serviceId + "-httproute");
        context.put("projectName", service.getProjectName());
        context.put("serviceId", serviceId);
        context.put("payloadLoggerHost", payloadLoggerProperties.getHost());
        context.put("payloadLoggerPort", payloadLoggerProperties.getPort());
        K8sCRRunnable payloadLoggerCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(serviceId + "-payload-logger")
            .apiGroup(PAYLOADLOGGER_EXTPROC_API_GROUP)
            .apiVersion(PAYLOADLOGGER_EXTPROC_API_VERSION)
            .kind(PAYLOADLOGGER_EXTPROC_API_KIND)
            .plural(PAYLOADLOGGER_EXTPROC_API_PLURAL)
            .spec(generateSpec(payloadLoggerExtProcMustache, context))
            .build();
        runnables.add(payloadLoggerCR);

        return runnables;
    }

    /**
     * Create K8sCRRunnables corresponding to Envoy Extension Processor Custom resources for ExtProc Service.
     * Specifically, it creates the following CRs:
     * - EnvoyExtensionPolicy
     * @param runtime
     * @param task
     * @param service
     * @return
     * @throws IOException
     */
    public List<K8sCRRunnable> createServiceExtprocRunnables(String runtime, String task, ExtProcService service)
        throws IOException {
        Assert.hasText(runtime, "runtime is required");
        Assert.hasText(task, "task is required");
        Assert.notNull(service, "service is required");

        String serviceId = service.getReferenceServiceId();
        log.debug("createServiceExtprocRunnables for runtime {} task {} serviceId {}", runtime, task, serviceId);

        List<K8sCRRunnable> runnables = new LinkedList<>();

        // ExtProc Route CR
        Map<String, Serializable> context = new HashMap<>();
        context.put("routeName", serviceId + "-httproute");
        context.put("extProcHost", service.getServiceHost());
        context.put("extProcPort", service.getServicePort());
        K8sCRRunnable extProcCR = K8sCRRunnable
            .builder()
            .runtime(runtime)
            .task(task)
            .state(State.READY.name())
            .name(serviceId + "-extproc")
            .apiGroup(EXTPROC_API_GROUP)
            .apiVersion(EXTPROC_API_VERSION)
            .kind(EXTPROC_API_KIND)
            .plural(EXTPROC_API_PLURAL)
            .spec(generateSpec(extProcMustache, context))
            .build();
        runnables.add(extProcCR);

        return runnables;
    }

    private Map<String, Serializable> generateSpec(Mustache mustache, Map<String, Serializable> context)
        throws IOException {
        StringWriter writer = new StringWriter();
        mustache.execute(writer, context);
        writer.flush();
        return objectMapper.readValue(writer.toString(), typeRef);
    }

    private Mustache loadTemplate(String templateName) throws IOException {
        String resourcePath = String.format("classpath:envoygw/templates/%s.yaml", templateName);
        Resource resource = resourceLoader.getResource(resourcePath);
        return mustacheFactory.compile(new InputStreamReader(resource.getInputStream()), templateName);
    }
}
