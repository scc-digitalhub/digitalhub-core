/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/*
 * Copyright 2025 the original author or authors.
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
 *
 */

package it.smartcommunitylabdhub.templates.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.template.Template;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.services.SpecValidator;
import it.smartcommunitylabdhub.commons.utils.ClassPathUtils;
import it.smartcommunitylabdhub.commons.utils.EntityUtils;
import it.smartcommunitylabdhub.templates.SearchableTemplateService;
import it.smartcommunitylabdhub.templates.filter.TemplateFilter;
import jakarta.annotation.Nonnull;
import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

@Service
@Slf4j
public class TemplateServiceImpl implements SearchableTemplateService, InitializingBean, ApplicationContextAware {

    private static final int CACHE_TIMEOUT = 60;
    private static final ObjectMapper mapper = JacksonMapper.YAML_OBJECT_MAPPER;
    private StringKeyGenerator keyGenerator = () -> UUID.randomUUID().toString().replace("-", "");

    @Autowired
    ResourceLoader resourceLoader;

    @Autowired
    ResourcePatternResolver resourceResolver;

    @Autowired
    private SpecRegistry specRegistry;

    @Autowired
    private SpecValidator validator;

    @Value("${templates.path}")
    private String templatesPath;

    private List<String> types = Collections.emptyList();
    private ApplicationContext applicationContext;

    //loading cache as map type+list
    LoadingCache<String, List<Template>> templateCache = CacheBuilder
        .newBuilder()
        .expireAfterWrite(CACHE_TIMEOUT, TimeUnit.MINUTES)
        .build(
            new CacheLoader<String, List<Template>>() {
                @Override
                public List<Template> load(@Nonnull String key) throws Exception {
                    log.debug("reload templates for {} from {}", key, templatesPath);
                    return readTemplates(templatesPath, key);
                }
            }
        );

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @PostConstruct
    public void scanForEntities() {
        // Detect the base packages based on ComponentScan annotation in CoreApplication.
        List<String> basePackages = ClassPathUtils.getBasePackages(applicationContext);
        log.info("Scanning for specDTOs under packages {}", basePackages);
        Set<Class<? extends BaseDTO>> classes = EntityUtils
            .scanForEntities(basePackages)
            .stream()
            .filter(c -> {
                //only dto with spec can have a template
                return SpecDTO.class.isAssignableFrom(c);
            })
            .collect(Collectors.toSet());

        //persist unmodifiable
        this.types =
            Collections.unmodifiableList(
                classes.stream().map(s -> EntityUtils.getEntityName(s).toLowerCase()).toList()
            );
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("Initialize template store for all entities...");
        for (String type : types) {
            List<Template> list = templateCache.get(type);
            log.debug("Initialized {} templates for {}", list.size(), type);
        }
    }

    @Autowired(required = false)
    public void setKeyGenerator(StringKeyGenerator keyGenerator) {
        Assert.notNull(keyGenerator, "key generator can not be null");
        this.keyGenerator = keyGenerator;
    }

    private List<Template> readTemplates(String path, String key) {
        if (!StringUtils.hasText(path) || !StringUtils.hasText(key)) {
            return Collections.emptyList();
        }

        List<Template> result = new ArrayList<>();

        try {
            //we will read from a file-like resource where templates are under /<key>/*.yaml
            String filePath = (path.endsWith("/") ? path : path + "/") + key;

            log.debug("read template resources from {}", filePath);
            Resource[] resources = resourceResolver.getResources(filePath + "/*.yaml");
            for (Resource resource : resources) {
                try {
                    //read via mapper
                    Template template = mapper.readValue(resource.getInputStream(), Template.class);
                    // force inject type
                    template.setType(key);
                    //generate id if missing
                    if (!StringUtils.hasText(template.getId())) {
                        template.setId(keyGenerator.generateKey());
                    }

                    //sanitize content
                    sanitize(template);

                    //discard invalid
                    validate(template);

                    result.add(template);
                } catch (IllegalArgumentException | IOException e1) {
                    log.error("Error reading template from {}: {}", resource.getFilename(), e1.getMessage());
                }
            }

            if (log.isTraceEnabled()) {
                log.trace("templates: {}", result);
            }
        } catch (IOException e) {
            log.debug("Error reading from {}:{}", path, key);
        }

        return result;
    }

    private void validate(Template template) {
        //minimal validation: base fields + spec
        if (!StringUtils.hasText(template.getName())) {
            throw new IllegalArgumentException("invalid or missing name");
        }
        if (!StringUtils.hasText(template.getKind())) {
            throw new IllegalArgumentException("invalid or missing kind");
        }
        if (template.getSpec() == null || template.getSpec().isEmpty()) {
            throw new IllegalArgumentException("invalid or missing spec");
        }

        // Parse and validate Spec
        Spec spec = specRegistry.createSpec(template.getKind(), template.getSpec());
        if (spec == null) {
            throw new IllegalArgumentException("invalid kind");
        }

        //validate
        validator.validateSpec(spec);
    }

    private Template sanitize(Template template) {
        //sanitize metadata to keep only base
        //TODO evaluate supporting more fields
        if (template.getMetadata() != null) {
            BaseMetadata base = BaseMetadata.from(template.getMetadata());
            BaseMetadata meta = new BaseMetadata();
            meta.setName(base.getName());
            meta.setDescription(base.getDescription());
            meta.setLabels(base.getLabels());

            template.setMetadata(meta.toMap());
        }

        // Parse and export Spec
        Spec spec = specRegistry.createSpec(template.getKind(), template.getSpec());
        if (spec == null) {
            throw new IllegalArgumentException("invalid kind");
        }

        //update spec as exported
        template.setSpec(spec.toMap());

        return template;
    }

    private List<Template> filterTemplate(List<Template> list, Pageable pageable, TemplateFilter filter) {
        return list
            .stream()
            .filter(f -> {
                boolean isOk = true;
                if (StringUtils.hasLength(filter.getName())) {
                    if (StringUtils.hasLength(f.getName())) {
                        isOk &= f.getName().toLowerCase().contains(filter.getName().toLowerCase());
                    } else {
                        isOk &= false;
                    }
                }
                if (StringUtils.hasLength(filter.getKind())) {
                    if (StringUtils.hasLength(f.getKind())) {
                        isOk &= f.getKind().toLowerCase().equals(filter.getKind().toLowerCase());
                    } else {
                        isOk &= false;
                    }
                }
                return isOk;
            })
            .collect(Collectors.toList());
    }

    @Override
    public Page<Template> searchTemplates(Pageable pageable, TemplateFilter filter) throws SystemException {
        try {
            List<Template> all = new ArrayList<>();
            //evaluate type filter first
            if (filter != null && StringUtils.hasText(filter.getType())) {
                //exact match
                String type = filter.getType();
                all = templateCache.get(type.toLowerCase());
            } else {
                //load all
                for (String type : types) {
                    all.addAll(templateCache.get(type.toLowerCase()));
                }
            }

            List<Template> list = filterTemplate(all, pageable, filter);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), list.size());
            List<Template> pageContent = list.subList(start, end);
            return new PageImpl<>(pageContent, pageable, list.size());
        } catch (ExecutionException e) {
            throw new SystemException("error retrieving templates:" + e.getMessage(), e);
        }
    }

    @Override
    public Page<Template> searchTemplates(Pageable pageable, @NotNull String type, TemplateFilter filter)
        throws SystemException {
        try {
            List<Template> templates = templateCache.get(type);
            List<Template> list = filterTemplate(templates, pageable, filter);
            int start = (int) pageable.getOffset();
            int end = Math.min((start + pageable.getPageSize()), list.size());
            List<Template> pageContent = list.subList(start, end);
            return new PageImpl<>(pageContent, pageable, list.size());
        } catch (ExecutionException e) {
            throw new SystemException("error retrieving templates:" + e.getMessage(), e);
        }
    }

    @Override
    public Template getTemplate(@NotNull String type, @NotNull String id) throws SystemException {
        try {
            List<Template> list = templateCache.get(type);
            return list.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(null);
        } catch (ExecutionException e) {
            throw new SystemException("error retrieving templates:" + e.getMessage(), e);
        }
    }
}
