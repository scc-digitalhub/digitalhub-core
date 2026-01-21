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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.envoygw.model.ExtProcService;
import it.smartcommunitylabdhub.envoygw.model.GenAIModelService;
import it.smartcommunitylabdhub.envoygw.model.GenericService;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;

@ExtendWith(MockitoExtension.class)
class GatewayCRManagerTest {

    @Mock
    private Resource aigatewayrouteTemplate;

    @Mock
    private Resource aibackendTemplate;

    @Mock
    private Resource backendTemplate;

    @Mock
    private Resource genericHttpRouteTemplate;

    @Mock
    private Resource payloadLoggerExtProcTemplate;

    @Mock
    private Resource extProcTemplate;

    @Mock
    private MustacheFactory mustacheFactory;

    @Mock
    private Mustache aigatewayrouteMustache;

    @Mock
    private Mustache aibackendMustache;

    @Mock
    private Mustache backendMustache;

    @Mock
    private Mustache genericHttpRouteMustache;

    @Mock
    private Mustache payloadLoggerExtProcMustache;

    @Mock
    private Mustache extProcMustache;

    @InjectMocks
    private GatewayCRManager gatewayCRManager;

    @BeforeEach
    void setUp() throws Exception {
        // Set up field values that would normally be injected by Spring
        setPrivateField(gatewayCRManager, "aiGatewayName", "test-ai-gateway");
        setPrivateField(gatewayCRManager, "genericGatewayName", "test-generic-gateway");
        setPrivateField(gatewayCRManager, "payloadLoggerHost", "logger.example.com");
        setPrivateField(gatewayCRManager, "payloadLoggerPort", 8080);

        // Mock template resources
        when(aigatewayrouteTemplate.getInputStream()).thenReturn(mock(InputStream.class));
        when(aibackendTemplate.getInputStream()).thenReturn(mock(InputStream.class));
        when(backendTemplate.getInputStream()).thenReturn(mock(InputStream.class));
        when(genericHttpRouteTemplate.getInputStream()).thenReturn(mock(InputStream.class));
        when(payloadLoggerExtProcTemplate.getInputStream()).thenReturn(mock(InputStream.class));
        when(extProcTemplate.getInputStream()).thenReturn(mock(InputStream.class));

        // Mock mustache factory
        when(mustacheFactory.compile(any(InputStreamReader.class), anyString())).thenReturn(mock(Mustache.class));

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

        // Verify AIGatewayRoute CR
        K8sCRRunnable aiGatewayRoute = runnables.get(0);
        assertEquals("test-service-aigatewayroute", aiGatewayRoute.getName());
        assertEquals("aigateway.envoyproxy.io", aiGatewayRoute.getApiGroup());
        assertEquals("v1alpha1", aiGatewayRoute.getApiVersion());
        assertEquals("AIGatewayRoute", aiGatewayRoute.getKind());
        assertEquals("aigatewayroutes", aiGatewayRoute.getPlural());
        assertEquals(runtime, aiGatewayRoute.getRuntime());
        assertEquals(task, aiGatewayRoute.getTask());
        assertEquals(State.READY.name(), aiGatewayRoute.getState());

        // Verify AIBackend CR
        K8sCRRunnable aiBackend = runnables.get(1);
        assertEquals("test-service-aibackend", aiBackend.getName());
        assertEquals("aigateway.envoyproxy.io", aiBackend.getApiGroup());
        assertEquals("AIServiceBackend", aiBackend.getKind());

        // Verify Backend CR
        K8sCRRunnable backend = runnables.get(2);
        assertEquals("test-service-backend", backend.getName());
        assertEquals("gateway.envoyproxy.io", backend.getApiGroup());
        assertEquals("Backend", backend.getKind());
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
        GenericService service = new GenericService("test-project", "test-service", "localhost", 8080);

        // Mock mustache execution
        mockMustacheExecution(genericHttpRouteMustache, "{\"apiVersion\":\"v1\",\"kind\":\"HTTPRoute\"}");
        mockMustacheExecution(backendMustache, "{\"apiVersion\":\"v1alpha1\",\"kind\":\"Backend\"}");

        // When
        List<K8sCRRunnable> runnables = gatewayCRManager.createGenericServiceRunnables(runtime, task, service);

        // Then
        assertNotNull(runnables);
        assertEquals(2, runnables.size());

        // Verify HTTPRoute CR
        K8sCRRunnable httpRoute = runnables.get(0);
        assertEquals("test-service-httproute", httpRoute.getName());
        assertEquals("gateway.envoyproxy.io", httpRoute.getApiGroup());
        assertEquals("v1", httpRoute.getApiVersion());
        assertEquals("HTTPRoute", httpRoute.getKind());

        // Verify Backend CR
        K8sCRRunnable backend = runnables.get(1);
        assertEquals("test-service-backend", backend.getName());
        assertEquals("gateway.envoyproxy.io", backend.getApiGroup());
        assertEquals("Backend", backend.getKind());
    }

    @Test
    void testCreateServicePayloadLoggerRunnables_Success() throws IOException {
        // Given
        String runtime = "test-runtime";
        String task = "test-task";
        GenericService service = new GenericService("test-project", "test-service", "localhost", 8080);

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
