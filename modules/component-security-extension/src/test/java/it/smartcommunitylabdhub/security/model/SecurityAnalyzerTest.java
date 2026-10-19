package it.smartcommunitylabdhub.security.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.function.Function;
import it.smartcommunitylabdhub.security.config.SecurityExtensionProperties;
import it.smartcommunitylabdhub.security.service.SecurityAnalyzerRegistry;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

class SecurityAnalyzerTest {

    @Test
    void bundledAnalyzersLoadScriptsAndSelectTargetsIndependently() throws Exception {
        SecurityExtensionProperties properties = new SecurityExtensionProperties();
        properties.setAnalyzersPath("classpath*:security/analyzers/");
        SecurityAnalyzerRegistry registry = new SecurityAnalyzerRegistry(
            properties,
            new PathMatchingResourcePatternResolver()
        );
        registry.afterPropertiesSet();
        SecurityAnalyzer bandit = registry.get("dh-security-bandit");
        SecurityAnalyzer trivy = registry.get("dh-security-trivy");
        assertTrue(((Map<?, ?>) bandit.getFunction().getSpec().get("source")).containsKey("base64"));
        assertTrue(((Map<?, ?>) trivy.getFunction().getSpec().get("source")).containsKey("base64"));

        HashMap<String, Serializable> spec = new HashMap<>();
        spec.put("source", new HashMap<>(Map.of("base64", "cGFzcw==")));
        Function target = Function.builder().kind("python").spec(spec).build();
        assertTrue(bandit.matches(target));
        assertFalse(trivy.matches(target));
        spec.put("image", "registry/image:tag");
        assertTrue(bandit.matches(target));
        assertTrue(trivy.matches(target));
        target.setKind("container");
        assertFalse(bandit.matches(target));
        assertTrue(trivy.matches(target));
        spec.remove("source");
        assertFalse(bandit.matches(target));
    }

    @Test
    void unfilteredAnalyzerMatchesAnyFunction() {
        SecurityAnalyzer analyzer = new SecurityAnalyzer();
        assertTrue(analyzer.matches(Function.builder().kind("container").build()));
        assertFalse(analyzer.matches(null));
    }

    @Test
    void filterRequiresBothKindAndAllPopulatedFields() throws Exception {
        SecurityAnalyzer analyzer = JacksonMapper.YAML_OBJECT_MAPPER.readValue(
            "filter:\n  spec_fields: [source, image]\n  kinds: [python, python+hydra]\n",
            SecurityAnalyzer.class
        );
        HashMap<String, Serializable> spec = new HashMap<>();
        spec.put("source", new HashMap<>(Map.of("base64", "cGFzcw==")));
        Function target = Function.builder().kind("python").spec(spec).build();
        assertFalse(analyzer.matches(target));
        spec.put("image", "registry/image:tag");
        assertTrue(analyzer.matches(target));
        target.setKind("python+hydra");
        assertTrue(analyzer.matches(target));
        target.setKind("container");
        assertFalse(analyzer.matches(target));
    }

    @Test
    void fieldFilterRejectsNullBlankAndEmptyValues() {
        SecurityAnalyzer analyzer = new SecurityAnalyzer();
        SecurityAnalyzer.Filter filter = new SecurityAnalyzer.Filter();
        filter.setSpecFields(List.of("source"));
        analyzer.setFilter(filter);
        Function target = Function.builder().kind("python").spec(null).build();
        assertFalse(analyzer.matches(target));
        HashMap<String, Serializable> spec = new HashMap<>();
        target.setSpec(spec);
        for (Serializable value : List.of(" ", new HashMap<>(), new java.util.ArrayList<>())) {
            spec.put("source", value);
            assertFalse(analyzer.matches(target));
        }
        spec.put("source", null);
        assertFalse(analyzer.matches(target));
        spec.put("source", new HashMap<>(Map.of("base64", "cGFzcw==")));
        assertTrue(analyzer.matches(target));
    }
}