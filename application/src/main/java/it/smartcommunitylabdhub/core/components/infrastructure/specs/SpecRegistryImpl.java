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

package it.smartcommunitylabdhub.core.components.infrastructure.specs;

import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.infrastructure.SpecFactory;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.services.SpecValidator;
import it.smartcommunitylabdhub.commons.utils.ClassPathUtils;
import it.smartcommunitylabdhub.commons.utils.SchemaUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SchemaImpl.SchemaImplBuilder;
import jakarta.annotation.PostConstruct;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.MethodParameter;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.DataBinder;
import org.springframework.validation.SmartValidator;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.MethodArgumentNotValidException;

@Component
@Slf4j
@Validated
public class SpecRegistryImpl
    implements SpecRegistry, SpecValidator, SchemaService, ApplicationContextAware, InitializingBean {

    private SmartValidator validator;
    private ApplicationContext applicationContext;

    private List<com.github.victools.jsonschema.generator.Module> modules;

    // A map to store spec types and their corresponding classes.
    private final Map<String, SpecRegistration> registrations = new HashMap<>();

    private SchemaGenerator generator = SchemaUtils.generator();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Autowired
    public void setValidator(SmartValidator validator) {
        this.validator = validator;
    }

    @Autowired(required = false)
    public void setModules(List<com.github.victools.jsonschema.generator.Module> modules) {
        this.modules = modules;
    }

    @PostConstruct
    @SuppressWarnings("unchecked")
    private void scanForSpecTypes() {
        // Create a component scanner to find classes with SpecType annotations.
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(SpecType.class));

        // Detect the base packages based on ComponentScan annotation in CoreApplication.
        List<String> basePackages = ClassPathUtils.getBasePackages(applicationContext);
        log.info("Scanning for specTypes under packages {}", basePackages);

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(basePackage);

            for (BeanDefinition beanDefinition : candidateComponents) {
                String className = beanDefinition.getBeanClassName();
                try {
                    // Load the class and check for SpecType annotation.
                    Class<? extends Spec> specClass = (Class<? extends Spec>) Class.forName(className);
                    SpecType type = specClass.getAnnotation(SpecType.class);
                    String kind = type.kind();
                    Class<?> entity = type.entity();
                    String runtime = type.runtime();

                    //enforce runtime prefix rule on kind
                    if (StringUtils.hasText(runtime) && !kind.startsWith(runtime)) {
                        throw new IllegalArgumentException("invalid kind " + kind + "for runtime " + runtime);
                    }

                    SpecFactory<? extends Spec> factory = null;

                    //specs MUST have an empty default constructor, let's check
                    //TODO evaluate for SpecFactory bean in context
                    try {
                        Constructor<? extends Spec> c = specClass.getDeclaredConstructor();
                        c.newInstance();

                        //build a default factory
                        factory =
                            () -> {
                                try {
                                    return c.newInstance();
                                } catch (
                                    InstantiationException
                                    | IllegalAccessException
                                    | IllegalArgumentException
                                    | InvocationTargetException e
                                ) {
                                    throw new IllegalArgumentException("error building spec");
                                }
                            };
                    } catch (
                        NoSuchMethodException
                        | InstantiationException
                        | IllegalAccessException
                        | InvocationTargetException e
                    ) {
                        //invalid spec
                        //TODO check for factory annotation as fallback
                        throw new IllegalArgumentException("missing or invalida default constructor ");
                    }

                    log.debug("discovered spec for {}:{} with class {}", entity, kind, specClass.getName());
                    registerSpec(type, specClass, factory);
                } catch (IllegalArgumentException | ClassNotFoundException e) {
                    log.error("error registering spec {}: {}", className, e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        // register additional modules for generator
        SchemaGeneratorConfigBuilder builder = SchemaUtils.BUILDER;
        if (modules != null) {
            modules.forEach(builder::with);
        }

        generator = new SchemaGenerator(builder.build());
    }

    private String getEntityName(Class<?> clazz) {
        return clazz.getSimpleName().toUpperCase();
    }

    /*
     * Spec Registry
     */
    private void registerSpec(SpecType type, Class<? extends Spec> spec, SpecFactory<? extends Spec> factory) {
        Assert.notNull(type, "type is required");
        Assert.notNull(spec, "spec can not be null");
        Assert.notNull(factory, "spec factory can not be null");

        Assert.notNull(type.kind(), "kind can not be null");
        Assert.notNull(type.entity(), "entity can not be null");

        String kind = type.kind();
        Class<?> entity = type.entity();

        if (registrations.containsKey(kind)) {
            throw new IllegalArgumentException("duplicated registration for " + entity + ":" + kind);
        }

        log.debug("generate schema for spec {}:{} ", entity, kind);
        // build proxy
        Class<? extends Spec> proxy = SchemaUtils.proxy(spec);

        // generate
        SchemaImplBuilder builder = SchemaImpl
            .builder()
            .entity(getEntityName(entity))
            .kind(kind)
            .schema(generator.generateSchema(proxy));
        if (StringUtils.hasText(type.runtime())) {
            builder.runtime(type.runtime());
        }
        SchemaImpl schema = builder.build();

        log.debug("register spec for {}:{} with class {}", entity, kind, spec.getName());
        registrations.put(kind, new SpecRegistration(type, spec, factory, schema));
    }

    @Override
    public <S extends Spec> S getSpec(String kind) {
        // Retrieve the registration associated with the specified spec type.
        SpecRegistration reg = registrations.get(kind);
        if (reg == null) {
            throw new NoSuchEntityException(Spec.class);
        }

        // create via factory
        @SuppressWarnings("unchecked")
        S spec = (S) reg.factory().create();
        return spec;
    }

    @Override
    public <S extends Spec> S createSpec(String kind, Map<String, Serializable> data) {
        S spec = getSpec(kind);
        if (data != null) {
            spec.configure(data);
        }
        return spec;
    }

    @Override
    public void validateSpec(Spec spec) throws IllegalArgumentException {
        // check with validator
        if (validator != null) {
            DataBinder binder = new DataBinder(spec);
            validator.validate(spec, binder.getBindingResult());
            if (binder.getBindingResult().hasErrors()) {
                try {
                    MethodParameter methodParameter = new MethodParameter(
                        this.getClass().getMethod("validateSpec", Spec.class),
                        0
                    );
                    MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
                        methodParameter,
                        binder.getBindingResult()
                    );
                    throw new IllegalArgumentException(ex.getMessage());
                } catch (NoSuchMethodException | SecurityException ex) {
                    StringBuilder sb = new StringBuilder();
                    binder
                        .getBindingResult()
                        .getFieldErrors()
                        .forEach(e -> {
                            sb.append(e.getField()).append(" ").append(e.getDefaultMessage()).append(", ");
                        });
                    String errorMsg = sb.toString();
                    throw new IllegalArgumentException(errorMsg);
                }
            }
        }
    }

    /*
     * Schema registry
     */
    @Override
    public Schema getSchema(String kind) {
        SpecRegistration reg = registrations.get(kind);
        if (reg == null) {
            throw new IllegalArgumentException("missing spec");
        }

        return reg.schema();
    }

    @Override
    public Collection<Schema> listSchemas(String entity) {
        return registrations
            .values()
            .stream()
            .filter(e -> entity.equalsIgnoreCase(getEntityName(e.type().entity())))
            .map(e -> e.schema())
            .toList();
    }

    @Override
    public Collection<Schema> getSchemas(String entity, String runtime) {
        return registrations
            .values()
            .stream()
            .filter(e -> entity.equalsIgnoreCase(getEntityName(e.type().entity())) && runtime.equals(e.type().runtime())
            )
            .map(e -> e.schema())
            .toList();
    }

    private record SpecRegistration(
        SpecType type,
        Class<? extends Spec> spec,
        SpecFactory<? extends Spec> factory,
        Schema schema
    ) {}
}
