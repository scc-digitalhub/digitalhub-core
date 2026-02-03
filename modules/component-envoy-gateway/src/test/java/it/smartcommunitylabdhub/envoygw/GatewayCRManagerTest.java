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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.envoygw.config.EnvoyGwProperties;
import it.smartcommunitylabdhub.envoygw.config.PayloadLoggerProperties;
import it.smartcommunitylabdhub.envoygw.model.ExtProcService;
import it.smartcommunitylabdhub.envoygw.model.GenAIModelService;
import it.smartcommunitylabdhub.envoygw.model.GenericService;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

class GatewayCRManagerTest {

    private Resource aigatewayrouteTemplate;

    private Resource aibackendTemplate;

    private Resource backendTemplate;

    private Resource genericHttpRouteTemplate;

    private Resource payloadLoggerExtProcTemplate;

    private Resource extProcTemplate;

    @Mock
    private MustacheFactory mustacheFactory;

    private Mustache aigatewayrouteMustache;

    private Mustache aibackendMustache;

    private Mustache backendMustache;

    private Mustache genericHttpRouteMustache;

    private Mustache payloadLoggerExtProcMustache;

    private Mustache extProcMustache;

    private PayloadLoggerProperties payloadLoggerProperties;

    @Mock
    private ResourceLoader resourceLoader;

    private GatewayCRManager gatewayCRManager;

    private EnvoyGwProperties.GatewayInstanceProperties aiGateway;
    private EnvoyGwProperties.GatewayInstanceProperties genericGateway;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        EnvoyGwProperties envoyGwProperties = mock(EnvoyGwProperties.class);
        // Mock EnvoyGwProperties
        aiGateway = mock(EnvoyGwProperties.GatewayInstanceProperties.class);
        lenient().when(aiGateway.getName()).thenReturn("test-ai-gateway");
        lenient().when(envoyGwProperties.getAiGateway()).thenReturn(aiGateway);

        genericGateway = mock(EnvoyGwProperties.GatewayInstanceProperties.class);
        lenient().when(genericGateway.getName()).thenReturn("test-generic-gateway");
        lenient().when(envoyGwProperties.getGenericGateway()).thenReturn(genericGateway);

        // Mock PayloadLoggerProperties
        payloadLoggerProperties = mock(PayloadLoggerProperties.class);
        lenient().when(payloadLoggerProperties.getHost()).thenReturn("logger.example.com");
        lenient().when(payloadLoggerProperties.getPort()).thenReturn(8080);
        lenient().when(payloadLoggerProperties.isEnabled()).thenReturn(true);

        // Create mock template resources
        aigatewayrouteTemplate = mock(Resource.class);
        aibackendTemplate = mock(Resource.class);
        backendTemplate = mock(Resource.class);
        genericHttpRouteTemplate = mock(Resource.class);
        payloadLoggerExtProcTemplate = mock(Resource.class);
        extProcTemplate = mock(Resource.class);

        // Create mock mustaches
        aigatewayrouteMustache = mock(Mustache.class);
        aibackendMustache = mock(Mustache.class);
        backendMustache = mock(Mustache.class);
        genericHttpRouteMustache = mock(Mustache.class);
        payloadLoggerExtProcMustache = mock(Mustache.class);
        extProcMustache = mock(Mustache.class);

        // Mock template resources
        when(aigatewayrouteTemplate.getInputStream()).thenReturn(new ByteArrayInputStream("name: {{name}}".getBytes()));
        when(aibackendTemplate.getInputStream()).thenReturn(new ByteArrayInputStream("name: {{name}}".getBytes()));
        when(backendTemplate.getInputStream()).thenReturn(new ByteArrayInputStream("name: {{name}}".getBytes()));
        when(genericHttpRouteTemplate.getInputStream()).thenReturn(new ByteArrayInputStream("name: {{name}}".getBytes()));
        when(payloadLoggerExtProcTemplate.getInputStream()).thenReturn(new ByteArrayInputStream("name: {{name}}".getBytes()));
        when(extProcTemplate.getInputStream()).thenReturn(new ByteArrayInputStream("name: {{name}}".getBytes()));

        // Mock mustache factory
        lenient().when(mustacheFactory.compile(any(InputStreamReader.class), anyString())).thenReturn(mock(Mustache.class));

        // Create GatewayCRManager instance
        gatewayCRManager = new GatewayCRManager(envoyGwProperties);

        // Inject the mocked ResourceLoader
        try {
            Field field = GatewayCRManager.class.getDeclaredField("resourceLoader");
            field.setAccessible(true);
            field.set(gatewayCRManager, resourceLoader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Set the payloadLoggerProperties on the manager
        gatewayCRManager.setPayloadLoggerProperties(payloadLoggerProperties);

        // Mock resource loader
        when(resourceLoader.getResource("classpath:envoygw/templates/aigatewayroute.yaml")).thenReturn(aigatewayrouteTemplate);
        when(resourceLoader.getResource("classpath:envoygw/templates/aibackend.yaml")).thenReturn(aibackendTemplate);
        when(resourceLoader.getResource("classpath:envoygw/templates/backend.yaml")).thenReturn(backendTemplate);
        when(resourceLoader.getResource("classpath:envoygw/templates/generic-httproute.yaml")).thenReturn(genericHttpRouteTemplate);
        when(resourceLoader.getResource("classpath:envoygw/templates/payload-extension.yaml")).thenReturn(payloadLoggerExtProcTemplate);
        when(resourceLoader.getResource("classpath:envoygw/templates/extproc-extension.yaml")).thenReturn(extProcTemplate);

        // Initialize the manager
        gatewayCRManager.afterPropertiesSet();

        // Set the compiled mustaches
        setPrivateField(gatewayCRManager, "aigatewayrouteMustache", aigatewayrouteMustache);
        setPrivateField(gatewayCRManager, "aibackendMustache", aibackendMustache);
        setPrivateField(gatewayCRManager, "backendMustache", backendMustache);
        setPrivateField(gatewayCRManager, "genericHttpRouteMustache", genericHttpRouteMustache);
        setPrivateField(gatewayCRManager, "payloadLoggerExtProcMustache", payloadLoggerExtProcMustache);
        setPrivateField(gatewayCRManager, "extProcMustache", extProcMustache);
    }

    private void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    void testCreateGenAIRunnables_Success() throws IOException {
        // Given
        String runtime = "test-runtime";
        String task = "test-task";
        GenAIModelService service = new GenAIModelService();
        service.setProjectName("test-project");
        service.setServiceId("test-service");
        service.setServiceHost("localhost");
        service.setServicePort(8080);
        service.setModelName("gpt-model");
        service.setSchemaName("openai");

        // Mock mustache execution
        mockMustacheExecution(aigatewayrouteMustache, "{\"apiVersion\":\"v1alpha1\",\"kind\":\"AIGatewayRoute\"}");
        mockMustacheExecution(aibackendMustache, "{\"apiVersion\":\"v1alpha1\",\"kind\":\"AIServiceBackend\"}");
        mockMustacheExecution(backendMustache, "{\"apiVersion\":\"v1alpha1\",\"kind\":\"Backend\"}");

        // When
        List<K8sCRRunnable> runnables = gatewayCRManager.createGenAIRunnables(runtime, task, service);

        // Then
        assertNotNull(runnables);
        assertEquals(3, runnables.size());

        // Verify Backend CR
        K8sCRRunnable backend = runnables.get(0);
        assertEquals("test-service-backend", backend.getName());
        assertEquals("gateway.envoyproxy.io", backend.getApiGroup());
        assertEquals("v1alpha1", backend.getApiVersion());
        assertEquals("Backend", backend.getKind());
        assertEquals("backends", backend.getPlural());
        assertEquals(runtime, backend.getRuntime());
        assertEquals(task, backend.getTask());
        assertEquals(State.READY.name(), backend.getState());

        // Verify AIBackend CR
        K8sCRRunnable aiBackend = runnables.get(1);
        assertEquals("test-service-aibackend", aiBackend.getName());
        assertEquals("aigateway.envoyproxy.io", aiBackend.getApiGroup());
        assertEquals("v1alpha1", aiBackend.getApiVersion());
        assertEquals("AIServiceBackend", aiBackend.getKind());
        assertEquals("aiservicebackends", aiBackend.getPlural());
        assertEquals(runtime, aiBackend.getRuntime());
        assertEquals(task, aiBackend.getTask());
        assertEquals(State.READY.name(), aiBackend.getState());

        // Verify AIGatewayRoute CR
        K8sCRRunnable aiGatewayRoute = runnables.get(2);
        assertEquals("test-service-aigatewayroute", aiGatewayRoute.getName());
        assertEquals("aigateway.envoyproxy.io", aiGatewayRoute.getApiGroup());
        assertEquals("v1alpha1", aiGatewayRoute.getApiVersion());
        assertEquals("AIGatewayRoute", aiGatewayRoute.getKind());
        assertEquals("aigatewayroutes", aiGatewayRoute.getPlural());
        assertEquals(runtime, aiGatewayRoute.getRuntime());
        assertEquals(task, aiGatewayRoute.getTask());
        assertEquals(State.READY.name(), aiGatewayRoute.getState());
    }

    @Test
    void testCreateGenAIRunnables_NullRuntime_ThrowsException() {
        // Given
        GenAIModelService service = new GenAIModelService();
        service.setProjectName("test-project");
        service.setServiceId("test-service");
        service.setServiceHost("localhost");
        service.setServicePort(8080);
        service.setModelName("gpt-model");
        service.setSchemaName("openai");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> gatewayCRManager.createGenAIRunnables(null, "test-task", service)
        );
        assertEquals("runtime is required", exception.getMessage());
    }

    @Test
    void testCreateGenAIRunnables_NullTask_ThrowsException() {
        // Given
        GenAIModelService service = new GenAIModelService();
        service.setProjectName("test-project");
        service.setServiceId("test-service");
        service.setServiceHost("localhost");
        service.setServicePort(8080);
        service.setModelName("gpt-model");
        service.setSchemaName("openai");

        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> gatewayCRManager.createGenAIRunnables("test-runtime", null, service)
        );
        assertEquals("task is required", exception.getMessage());
    }

    @Test
    void testCreateGenAIRunnables_NullService_ThrowsException() {
        // When & Then
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> gatewayCRManager.createGenAIRunnables("test-runtime", "test-task", null)
        );
        assertEquals("service is required", exception.getMessage());
    }

    @Test
    void testCreateGenericServiceRunnables_Success() throws IOException {
        // Given
        String runtime = "test-runtime";
        String task = "test-task";
        GenericService service = new GenericService("test-project", "test-service", "test-function", "localhost", 8080);

        // Mock mustache execution
        mockMustacheExecution(genericHttpRouteMustache, "{\"apiVersion\":\"v1\",\"kind\":\"HTTPRoute\"}");
        mockMustacheExecution(backendMustache, "{\"apiVersion\":\"v1alpha1\",\"kind\":\"Backend\"}");

        // When
        List<K8sCRRunnable> runnables = gatewayCRManager.createGenericServiceRunnables(runtime, task, service);

        // Then
        assertNotNull(runnables);
        assertEquals(2, runnables.size());

        // Verify Backend CR
        K8sCRRunnable backend = runnables.get(0);
        assertEquals("test-service-backend", backend.getName());
        assertEquals("gateway.envoyproxy.io", backend.getApiGroup());
        assertEquals("v1alpha1", backend.getApiVersion());
        assertEquals("Backend", backend.getKind());

        // Verify HTTPRoute CR
        K8sCRRunnable httpRoute = runnables.get(1);
        assertEquals("test-service-httproute", httpRoute.getName());
        assertEquals("gateway.networking.k8s.io", httpRoute.getApiGroup());
        assertEquals("v1", httpRoute.getApiVersion());
        assertEquals("HTTPRoute", httpRoute.getKind());
    }

    @Test
    void testCreateServicePayloadLoggerRunnables_Success() throws IOException {
        // Given
        String runtime = "test-runtime";
        String task = "test-task";
        GenericService service = new GenericService("test-project", "test-service", "test-function", "localhost", 8080);

        // Mock mustache execution
        mockMustacheExecution(
            payloadLoggerExtProcMustache,
            "{\"apiVersion\":\"v1alpha1\",\"kind\":\"EnvoyExtensionPolicy\"}"
        );

        // When
        List<K8sCRRunnable> runnables = gatewayCRManager.createServicePayloadLoggerRunnables(runtime, task, service);

        // Then
        assertNotNull(runnables);
        assertEquals(1, runnables.size());

        K8sCRRunnable payloadLogger = runnables.get(0);
        assertEquals("test-service-payload-logger", payloadLogger.getName());
        assertEquals("gateway.envoyproxy.io", payloadLogger.getApiGroup());
        assertEquals("v1alpha1", payloadLogger.getApiVersion());
        assertEquals("EnvoyExtensionPolicy", payloadLogger.getKind());
    }

    @Test
    void testCreateServiceExtprocRunnables_Success() throws IOException {
        // Given
        String runtime = "test-runtime";
        String task = "test-task";
        ExtProcService service = new ExtProcService();
        service.setProjectName("test-project");
        service.setServiceId("ref-service");
        service.setServiceHost("extproc-host");
        service.setServicePort(9090);
        service.setReferenceServiceId("test-service");

        // Mock mustache execution
        mockMustacheExecution(extProcMustache, "{\"apiVersion\":\"v1alpha1\",\"kind\":\"EnvoyExtensionPolicy\"}");

        // When
        List<K8sCRRunnable> runnables = gatewayCRManager.createServiceExtprocRunnables(runtime, task, service);

        // Then
        assertNotNull(runnables);
        assertEquals(1, runnables.size());

        K8sCRRunnable extProc = runnables.get(0);
        assertEquals("test-service-extproc", extProc.getName());
        assertEquals("gateway.envoyproxy.io", extProc.getApiGroup());
        assertEquals("v1alpha1", extProc.getApiVersion());
        assertEquals("EnvoyExtensionPolicy", extProc.getKind());
    }

    @Test
    void testGenerateSpec_Success() throws Exception {
        // Given
        Mustache mustache = mock(Mustache.class);
        StringWriter writer = new StringWriter();
        writer.write("{\"key\":\"value\"}");
        doAnswer(invocation -> {
                StringWriter sw = invocation.getArgument(0);
                sw.write("{\"key\":\"value\"}");
                return sw;
            })
            .when(mustache)
            .execute(any(StringWriter.class), (Object) any());

        Map<String, Object> context = Map.of("test", "data");

        // Use reflection to call private method
        var method = GatewayCRManager.class.getDeclaredMethod("generateSpec", Mustache.class, Map.class);
        method.setAccessible(true);

        // When
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) method.invoke(gatewayCRManager, mustache, context);

        // Then
        assertNotNull(result);
        assertEquals("value", result.get("key"));
    }

    @Test
    void testGenerateSpec_InvalidJson_ThrowsException() throws Exception {
        // Given
        Mustache mustache = mock(Mustache.class);
        doAnswer(invocation -> {
                StringWriter sw = invocation.getArgument(0);
                sw.write("invalid json");
                return sw;
            })
            .when(mustache)
            .execute(any(StringWriter.class), (Object) any());

        Map<String, Object> context = Map.of("test", "data");

        // Use reflection to call private method
        var method = GatewayCRManager.class.getDeclaredMethod("generateSpec", Mustache.class, Map.class);
        method.setAccessible(true);

        // When & Then
        InvocationTargetException exception = assertThrows(
            InvocationTargetException.class,
            () -> method.invoke(gatewayCRManager, mustache, context)
        );
        assertInstanceOf(IOException.class, exception.getCause());
    }

    private void mockMustacheExecution(Mustache mustache, String jsonResponse) throws IOException {
        doAnswer(invocation -> {
                StringWriter writer = invocation.getArgument(0);
                writer.write(jsonResponse);
                return writer;
            })
            .when(mustache)
            .execute(any(StringWriter.class), (Object) any());
    }
}
