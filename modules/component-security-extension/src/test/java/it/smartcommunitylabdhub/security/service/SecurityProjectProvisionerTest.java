package it.smartcommunitylabdhub.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.core.events.EntityEvent;
import it.smartcommunitylabdhub.events.EntityAction;
import it.smartcommunitylabdhub.functions.FunctionManager;
import it.smartcommunitylabdhub.lifecycle.LifecycleManager;
import it.smartcommunitylabdhub.projects.persistence.ProjectEntity;
import it.smartcommunitylabdhub.security.config.SecurityExtensionProperties;
import it.smartcommunitylabdhub.security.listeners.ProjectSecurityProvisioningListener;
import it.smartcommunitylabdhub.security.model.SecurityAnalyzer;
import it.smartcommunitylabdhub.triggers.Trigger;
import it.smartcommunitylabdhub.triggers.TriggerManager;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SecurityProjectProvisionerTest {

    private final SecurityExtensionProperties properties = new SecurityExtensionProperties();
    private final SecurityAnalyzerRegistry registry = mock(SecurityAnalyzerRegistry.class);
    private final FunctionManager functionManager = mock(FunctionManager.class);
    private final TriggerManager triggerManager = mock(TriggerManager.class);
    @SuppressWarnings("unchecked")
    private final LifecycleManager<Trigger> lifecycleManager = mock(LifecycleManager.class);
    private final SecurityProjectProvisioner provisioner = new SecurityProjectProvisioner(
        properties,
        registry,
        functionManager,
        triggerManager,
        lifecycleManager
    );

    @Test
    void disabledExtensionDoesNotProvision() throws Exception {
        provisioner.provision("project", "user");
        verify(functionManager, never()).createFunction(any());
        verify(triggerManager, never()).createTrigger(any());
    }

    @Test
    void listenerProvisionsOnlyCreatedProjects() throws Exception {
        SecurityProjectProvisioner mockProvisioner = mock(SecurityProjectProvisioner.class);
        ProjectSecurityProvisioningListener listener = new ProjectSecurityProvisioningListener(mockProvisioner);
        ProjectEntity project = ProjectEntity.builder().id("project").createdBy("user").build();
        listener.receive(new EntityEvent<>(project, EntityAction.UPDATE));
        verify(mockProvisioner, never()).provision(any(), any());
        listener.receive(new EntityEvent<>(project, EntityAction.CREATE));
        verify(mockProvisioner).provision("project", "user");
    }

    @Test
    void provisionsFunctionsAndActivatesConfiguredTriggers() throws Exception {
        properties.setEnabled(true);
        SecurityAnalyzer triggered = analyzer("scanner", true);
        SecurityAnalyzer manual = analyzer("manual", false);
        when(registry.list()).thenReturn(List.of(triggered, manual));
        when(functionManager.createFunction(any())).thenAnswer(invocation -> {
            Function function = invocation.getArgument(0);
            function.setId(function.getName() + "-id");
            return function;
        });
        when(triggerManager.createTrigger(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lifecycleManager.perform(any(), eq("RUN"))).thenAnswer(invocation -> {
            Trigger trigger = invocation.getArgument(0);
            trigger.setStatus(new HashMap<>(Map.of("state", "RUNNING")));
            return trigger;
        });

        provisioner.provision("project", "user");

        ArgumentCaptor<Function> functions = ArgumentCaptor.forClass(Function.class);
        verify(functionManager, org.mockito.Mockito.times(2)).createFunction(functions.capture());
        assertEquals(List.of("scanner", "manual"), functions.getAllValues().stream().map(Function::getName).toList());
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
        verify(triggerManager).createTrigger(trigger.capture());
        assertEquals("store://project/containerimage/container-image", trigger.getValue().getSpec().get("key"));
        assertEquals("container://project/scanner:scanner-id", trigger.getValue().getSpec().get("function"));
        assertEquals("user", trigger.getValue().getUser());
        verify(lifecycleManager).perform(trigger.getValue(), "RUN");
    }

    @Test
    void failedTriggerActivationIsReported() throws Exception {
        properties.setEnabled(true);
        when(registry.list()).thenReturn(List.of(analyzer("scanner", true)));
        when(functionManager.createFunction(any())).thenAnswer(invocation -> {
            Function function = invocation.getArgument(0);
            function.setId("id");
            return function;
        });
        when(triggerManager.createTrigger(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lifecycleManager.perform(any(), eq("RUN"))).thenAnswer(invocation -> {
            Trigger trigger = invocation.getArgument(0);
            trigger.setStatus(new HashMap<>(Map.of("state", "ERROR")));
            return trigger;
        });
        assertThrows(IllegalStateException.class, () -> provisioner.provision("project", "user"));
    }

    private SecurityAnalyzer analyzer(String name, boolean withTrigger) {
        SecurityAnalyzer analyzer = new SecurityAnalyzer();
        analyzer.setName(name);
        SecurityAnalyzer.EntityTemplate function = new SecurityAnalyzer.EntityTemplate();
        function.setKind("container");
        analyzer.setFunction(function);
        if (withTrigger) {
            SecurityAnalyzer.EntityTemplate trigger = new SecurityAnalyzer.EntityTemplate();
            trigger.setKind("lifecycle");
            HashMap<String, Serializable> spec = new HashMap<>();
            spec.put("key", "store://${project}/containerimage/container-image");
            spec.put("task", "container+job");
            trigger.setSpec(spec);
            analyzer.setTrigger(trigger);
        }
        return analyzer;
    }
}
