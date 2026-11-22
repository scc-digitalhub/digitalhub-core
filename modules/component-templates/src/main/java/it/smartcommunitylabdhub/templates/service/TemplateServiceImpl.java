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
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.exceptions.SystemException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.metadata.BaseMetadata;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.template.Template;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.services.SpecValidator;
import it.smartcommunitylabdhub.templates.TemplateService;
import it.smartcommunitylabdhub.templates.filter.TemplateFilter;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Validated
public abstract class TemplateServiceImpl<D> implements TemplateService<D>, InitializingBean {

    private static final ObjectMapper mapper = JacksonMapper.YAML_OBJECT_MAPPER;
    private StringKeyGenerator keyGenerator = () -> UUID.randomUUID().toString().replace("-", "");

    @Value("${templates.path}")
    private String templatesPath;

    protected final Class<D> type;

    protected ResourcePatternResolver resourceLoader;

    private SpecRegistry<D> specRegistry;
    private SpecValidator validator;

    private Map<String, Template> templates = new LinkedHashMap<>();

    @SuppressWarnings("unchecked")
    protected TemplateServiceImpl() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = (Class<D>) t;
    }

    public TemplateServiceImpl(Class<D> type) {
        Assert.notNull(type, "type is required");
        this.type = type;
    }

    @Autowired
    public void setResourceLoader(ResourcePatternResolver resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Autowired
    public void setSpecRegistry(SpecRegistry<D> specRegistry) {
        this.specRegistry = specRegistry;
    }

    @Autowired(required = false)
    public void setValidator(SpecValidator validator) {
        this.validator = validator;
    }

    @Autowired(required = false)
    public void setKeyGenerator(StringKeyGenerator keyGenerator) {
        Assert.notNull(keyGenerator, "key generator can not be null");
        this.keyGenerator = keyGenerator;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        log.debug("Load templates for {}...", type);
        List<Template> list = readTemplates(templatesPath);
        this.templates = list.stream().collect(Collectors.toMap(Template::getId, t -> t));
        log.debug("Initialized {} templates for {}", list.size(), type);
    }

    @Override
    public Class<D> getType() {
        return this.type;
    }

    private List<Template> readTemplates(String path) {
        if (!StringUtils.hasText(path)) {
            return Collections.emptyList();
        }

        List<Template> result = new ArrayList<>();
        String key = type.getSimpleName().toLowerCase();

        try {
            //we will read from a file-like resource where templates are under /<key>/*.yaml
            String filePath = (path.endsWith("/") ? path : path + "/") + key;

            log.debug("read template resources from {}", filePath);
            Resource[] resources = resourceLoader.getResources(filePath + "/*.yaml");
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
                } catch (IllegalArgumentException | IOException | NoSuchEntityException e1) {
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
        if (validator != null) {
            validator.validateSpec(spec);
        }
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

    private List<Template> filterTemplate(Collection<Template> list, TemplateFilter filter) {
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
            .toList();
    }

    @Override
    public List<Template> listTemplates(TemplateFilter filter) throws SystemException {
        log.debug("List templates for {}", filter);
        List<Template> list = filterTemplate(templates.values(), filter);

        if (log.isTraceEnabled()) {
            log.trace("templates: {}", list);
        }

        return list;
    }

    @Override
    public Page<Template> searchTemplates(TemplateFilter filter, Pageable pageable) throws SystemException {
        log.debug("search templates for {}", filter);

        List<Template> list = filterTemplate(templates.values(), filter);
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());
        List<Template> pageContent = list.subList(start, end);

        if (log.isTraceEnabled()) {
            log.trace("templates: {}", pageContent);
        }

        return new PageImpl<>(pageContent, pageable, list.size());
    }

    @Override
    public Template findTemplate(@NotNull String id) throws NoSuchEntityException, SystemException {
        return templates.get(id);
    }

    @Override
    public Template getTemplate(@NotNull String id) throws NoSuchEntityException, SystemException {
        return Optional.ofNullable(templates.get(id)).orElseThrow(() ->
            new NoSuchEntityException("template not found: " + id)
        );
    }
}
