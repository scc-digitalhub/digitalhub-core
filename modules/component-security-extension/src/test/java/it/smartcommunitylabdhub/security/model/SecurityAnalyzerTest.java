package it.smartcommunitylabdhub.security.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import it.smartcommunitylabdhub.containerimage.ContainerImage;
import it.smartcommunitylabdhub.security.config.SecurityExtensionProperties;
import it.smartcommunitylabdhub.security.service.SecurityAnalyzerRegistry;
import it.smartcommunitylabdhub.templates.mustache.MustacheTemplateProcessor;
import it.smartcommunitylabdhub.trigger.lifecycle.LifecycleActuator;
import it.smartcommunitylabdhub.trigger.lifecycle.models.LifecycleTriggerJob;
import it.smartcommunitylabdhub.trigger.lifecycle.store.TriggerJobStore;
import it.smartcommunitylabdhub.triggers.Trigger;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.AntPathMatcher;

class SecurityAnalyzerTest {

    @Test
    void bundledAnalyzersLoadTheirFunctionsAndOptionalTriggers() throws Exception {
        SecurityExtensionProperties properties = new SecurityExtensionProperties();
        properties.setAnalyzersPath("classpath*:security/analyzers/");
        SecurityAnalyzerRegistry registry = new SecurityAnalyzerRegistry(
            properties,
            new PathMatchingResourcePatternResolver()
        );
        registry.afterPropertiesSet();

        for (String name : new String[] { "dh-security-bandit", "dh-security-trivy", "dh-security-trivy-container" }) {
            SecurityAnalyzer analyzer = registry.get(name);
            assertNotNull(analyzer, name);
            assertTrue(((Map<?, ?>) analyzer.getFunction().getSpec().get("source")).containsKey("base64"), name);
        }
        assertNull(registry.get("dh-security-bandit").getTrigger());
        assertNull(registry.get("dh-security-trivy").getTrigger());
        SecurityAnalyzer.EntityTemplate trigger = registry.get("dh-security-trivy-container").getTrigger();
        assertNotNull(trigger);
        assertEquals("lifecycle", trigger.getKind());
        assertEquals("store://${project}/containerimage/container-image", trigger.getSpec().get("key"));
        assertEquals("container+job", trigger.getSpec().get("task"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void containerTriggerMatchesAndTemplatesReadyImage() throws Exception {
        SecurityExtensionProperties properties = new SecurityExtensionProperties();
        properties.setAnalyzersPath("classpath*:security/analyzers/");
        SecurityAnalyzerRegistry registry = new SecurityAnalyzerRegistry(
            properties,
            new PathMatchingResourcePatternResolver()
        );
        registry.afterPropertiesSet();
        Map<String, Serializable> spec = new HashMap<>(registry.get("dh-security-trivy-container").getTrigger().getSpec());
        spec.put("key", "store://project/containerimage/container-image");
        spec.put("function", "container://project/scanner:scanner-id");
        Trigger trigger = Trigger.builder().id("trigger-id").project("project").user("user").spec(spec).build();
        TriggerJobStore<LifecycleTriggerJob> store = mock(TriggerJobStore.class);
        LifecycleActuator actuator = new LifecycleActuator();
        actuator.setStore(store);
        actuator.run(trigger);

        ArgumentCaptor<LifecycleTriggerJob> job = ArgumentCaptor.forClass(LifecycleTriggerJob.class);
        verify(store).store(eq("trigger-id"), job.capture());
        assertTrue(new AntPathMatcher().match(job.getValue().getKey(), "store://project/containerimage/container-image/image:abc"));
        assertTrue(job.getValue().getStates().contains("READY"));

        ContainerImage image = ContainerImage.builder().id("abc").project("project").build();
        Map<String, Serializable> template = it.smartcommunitylabdhub.triggers.specs.TriggerBaseSpec.from(spec).getTemplate();
        Map<String, Serializable> rendered = new MustacheTemplateProcessor().process(template, Map.of("input", image));
        assertEquals(List.of("/shared/trivy.sh", "containerimage", "abc"), rendered.get("args"));
    }
}
