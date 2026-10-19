/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.security.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.security.config.SecurityExtensionProperties;
import it.smartcommunitylabdhub.security.model.SecurityAnalyzer;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Registry of the predefined security analysis functions.
 */
@Component
@Slf4j
public class SecurityAnalyzerRegistry implements InitializingBean {

    private static final ObjectMapper mapper = JacksonMapper.YAML_OBJECT_MAPPER;

    private final SecurityExtensionProperties properties;
    private final ResourcePatternResolver resourceResolver;

    private Map<String, SecurityAnalyzer> analyzers = Collections.emptyMap();

    public SecurityAnalyzerRegistry(SecurityExtensionProperties properties, ResourcePatternResolver resourceResolver) {
        Assert.notNull(properties, "properties are required");
        Assert.notNull(resourceResolver, "resource resolver is required");
        this.properties = properties;
        this.resourceResolver = resourceResolver;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        String path = properties.getAnalyzersPath();
        if (!StringUtils.hasText(path)) {
            return;
        }

        String base = path.endsWith("/") ? path : path + "/";
        Map<String, SecurityAnalyzer> map = new LinkedHashMap<>();

        for (Resource resource : resourceResolver.getResources(base + "*.yaml")) {
            try {
                SecurityAnalyzer analyzer = read(resource);
                log.debug("registered security analyzer {} from {}", analyzer.getName(), resource.getFilename());
                map.put(analyzer.getName(), analyzer);
            } catch (IOException | IllegalArgumentException e) {
                log.error("error reading security analyzer from {}: {}", resource.getFilename(), e.getMessage());
            }
        }

        this.analyzers = Collections.unmodifiableMap(map);
        log.info("loaded {} security analyzers", analyzers.size());
    }

    private SecurityAnalyzer read(Resource resource) throws IOException {
        SecurityAnalyzer analyzer;
        try (InputStream is = resource.getInputStream()) {
            analyzer = mapper.readValue(is, SecurityAnalyzer.class);
        }

        if (!StringUtils.hasText(analyzer.getName()) || !analyzer.getName().matches(Keys.SLUG_PATTERN)) {
            throw new IllegalArgumentException("invalid or missing name");
        }
        if (analyzer.getFunction() == null || !StringUtils.hasText(analyzer.getFunction().getKind())) {
            throw new IllegalArgumentException("invalid or missing function kind");
        }
        if (analyzer.getTask() == null || !StringUtils.hasText(analyzer.getTask().getKind())) {
            throw new IllegalArgumentException("invalid or missing task kind");
        }
        if (analyzer.getRun() == null) {
            analyzer.setRun(new SecurityAnalyzer.EntityTemplate());
        }
        if (!StringUtils.hasText(analyzer.getRun().getKind())) {
            analyzer.getRun().setKind(analyzer.getTask().getKind() + ":run");
        }

        if (StringUtils.hasText(analyzer.getScript())) {
            //embed script as base64 source
            Resource script = resource.createRelative(analyzer.getScript());
            try (InputStream is = script.getInputStream()) {
                String base64 = Base64.getEncoder().encodeToString(is.readAllBytes());

                HashMap<String, Serializable> spec = new HashMap<>(analyzer.getFunction().getSpec());
                HashMap<String, Serializable> source = new HashMap<>();
                if (spec.get("source") instanceof Map<?, ?> s) {
                    s.forEach((k, v) -> source.put(String.valueOf(k), (Serializable) v));
                }
                source.put("base64", base64);
                spec.put("source", source);

                analyzer.getFunction().setSpec(spec);
            }
        }

        return analyzer;
    }

    public @Nullable SecurityAnalyzer get(String name) {
        return name != null ? analyzers.get(name) : null;
    }

    public Collection<SecurityAnalyzer> list() {
        return analyzers.values();
    }

    public boolean isAnalyzer(String name) {
        return name != null && analyzers.containsKey(name);
    }
}
