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

package it.smartcommunitylabdhub.core.specs;

import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.exceptions.NoSuchEntityException;
import it.smartcommunitylabdhub.commons.infrastructure.SpecFactory;
import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.services.SchemaService;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.utils.ClassPathUtils;
import it.smartcommunitylabdhub.commons.utils.SchemaUtils;
import it.smartcommunitylabdhub.core.specs.SchemaImpl.SchemaImplBuilder;
import jakarta.annotation.Nullable;
import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.lang.NonNull;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Validated
public class SpecRegistryImpl<D extends BaseDTO>
    implements SpecRegistry<D>, SchemaService<D>, ApplicationContextAware, InitializingBean {

    protected final Class<D> type;
    private ApplicationContext applicationContext;

    private List<com.github.victools.jsonschema.generator.Module> modules;

    // A map to store spec types and their corresponding classes.
    protected final Map<String, SpecRegistration> registrations = new HashMap<>();

    protected SchemaGenerator generator = SchemaUtils.generator();

    @SuppressWarnings("unchecked")
    protected SpecRegistryImpl() {
        // resolve generics type via subclass trick
        Type t = ((ParameterizedType) this.getClass().getGenericSuperclass()).getActualTypeArguments()[0];
        this.type = (Class<D>) t;
    }

    public SpecRegistryImpl(Class<D> type) {
        Assert.notNull(type, "type is required");
        this.type = type;
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Autowired(required = false)
    public void setModules(List<com.github.victools.jsonschema.generator.Module> modules) {
        this.modules = modules;
    }

    @Override
    public Class<D> getType() {
        return this.type;
    }

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
                    SpecType st = specClass.getAnnotation(SpecType.class);
                    String kind = st.kind();
                    Class<?> entity = st.entity();
                    String runtime = st.runtime();

                    //we'll keep only specs matching our entity type
                    if (!st.entity().isAssignableFrom(this.type)) {
                        continue;
                    }

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
                        throw new IllegalArgumentException("missing or invalid default constructor ");
                    }

                    log.debug("discovered spec for {}:{} with class {}", entity, kind, specClass.getName());
                    registerSpec(st, specClass, factory);
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

        //now we can scan for spec types and register them
        scanForSpecTypes();
    }

    private String getEntityName(Class<?> clazz) {
        return clazz.getSimpleName().toUpperCase();
    }

    /*
     * Spec Registry
     */
    public void registerSpec(SpecType type, Class<? extends Spec> spec, SpecFactory<? extends Spec> factory) {
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
        registrations.put(kind, new SpecRegistration(kind, type.runtime(), spec, factory, schema));
    }

    public void registerSpec(String kind, Schema schema) {
        Assert.notNull(kind, "kind is required");
        Assert.notNull(schema, "schema can not be null");

        if (registrations.containsKey(kind)) {
            throw new IllegalArgumentException("duplicated registration for kind " + kind);
        }

        log.debug("register spec for kind {} with schema", kind);
        registrations.put(kind, new SpecRegistration(kind, null, null, null, schema));
    }

    @Override
    public <S extends Spec> S getSpec(String kind) {
        // Retrieve the registration associated with the specified spec type.
        SpecRegistration reg = registrations.get(kind);
        if (reg == null || reg.factory() == null || reg.schema() == null) {
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
    public Collection<Schema> listSchemas() {
        return registrations.values().stream().map(e -> e.schema()).toList();
    }

    @Override
    public Collection<Schema> listSchemas(@Nullable String runtime) {
        if (runtime == null) {
            return listSchemas();
        } else {
            return registrations
                .values()
                .stream()
                .filter(e -> runtime.equals(e.runtime()))
                .map(e -> e.schema())
                .toList();
        }
    }

    protected record SpecRegistration(
        String kind,
        String runtime,
        Class<? extends Spec> spec,
        SpecFactory<? extends Spec> factory,
        Schema schema
    ) {}
}
