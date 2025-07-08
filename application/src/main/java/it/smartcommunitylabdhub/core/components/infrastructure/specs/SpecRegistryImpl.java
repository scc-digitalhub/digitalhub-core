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
import it.smartcommunitylabdhub.commons.infrastructure.SpecFactory;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.commons.utils.SchemaUtils;
import it.smartcommunitylabdhub.core.components.infrastructure.specs.SchemaImpl.SchemaImplBuilder;
import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
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
public class SpecRegistryImpl implements SpecRegistry, SpecValidator, InitializingBean {

    private SmartValidator validator;
    private List<com.github.victools.jsonschema.generator.Module> modules;

    // A map to store spec types and their corresponding classes.
    private final Map<String, SpecRegistration> registrations = new HashMap<>();
    private SchemaGenerator generator = SchemaUtils.generator();

    @Autowired
    public void setValidator(SmartValidator validator) {
        this.validator = validator;
    }

    @Autowired(required = false)
    public void setModules(List<com.github.victools.jsonschema.generator.Module> modules) {
        this.modules = modules;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        //register additional modules for generator
        SchemaGeneratorConfigBuilder builder = SchemaUtils.BUILDER;
        if (modules != null) {
            modules.forEach(builder::with);
        }

        generator = new SchemaGenerator(builder.build());
    }

    @Override
    public void registerSpec(SpecType type, Class<? extends Spec> spec, SpecFactory<? extends Spec> factory) {
        Assert.notNull(type, "type is required");
        Assert.notNull(spec, "spec can not be null");
        Assert.notNull(factory, "spec factory can not be null");

        String kind = type.kind();
        EntityName entity = type.entity();

        if (registrations.containsKey(kind)) {
            throw new IllegalArgumentException("duplicated registration for " + entity + ":" + kind);
        }

        log.debug("generate schema for spec {}:{} ", entity, kind);
        //build proxy
        Class<? extends Spec> proxy = SchemaUtils.proxy(spec);

        //generate
        SchemaImplBuilder builder = SchemaImpl
            .builder()
            .entity(entity)
            .kind(kind)
            .schema(generator.generateSchema(proxy));
        if (StringUtils.hasText(type.runtime())) {
            builder.runtime(type.runtime());
        }
        SchemaImpl schema = builder.build();

        log.debug("register spec for {}:{} with class {}", entity, kind, spec.getName());
        registrations.put(kind, new SpecRegistration(type, spec, factory, schema));
    }

    /**
     * Create an instance of a spec based on its type and configure it with data.
     *
     * @param kind The type of the spec to create.
     * @param data The data used to configure the spec.
     * @param <S>  The generic type for the spec.
     * @return An instance of the specified spec type, or null if not found or in case of errors.
     */
    @Override
    public <S extends Spec> S createSpec(String kind, Map<String, Serializable> data) {
        // Retrieve the registration associated with the specified spec type.
        SpecRegistration reg = registrations.get(kind);
        if (reg == null) {
            throw new IllegalArgumentException("missing spec");
        }

        //create via factory
        @SuppressWarnings("unchecked")
        S spec = (S) reg.factory().create();
        if (data != null) {
            spec.configure(data);
        }
        return spec;
    }

    @Override
    public Schema getSchema(String kind) {
        SpecRegistration reg = registrations.get(kind);
        if (reg == null) {
            throw new IllegalArgumentException("missing spec");
        }

        return reg.schema();
    }

    @Override
    public Collection<Schema> listSchemas(EntityName name) {
        return registrations
            .values()
            .stream()
            .filter(e -> name == e.type().entity())
            .map(e -> e.schema())
            .collect(Collectors.toList());
    }

    @Override
    public Collection<Schema> getSchemas(EntityName entity, String runtime) {
        return registrations
            .values()
            .stream()
            .filter(e -> entity == e.type().entity() && runtime.equals(e.type().runtime()))
            .map(e -> e.schema())
            .collect(Collectors.toList());
    }

    @Override
    public void validateSpec(Spec spec) throws MethodArgumentNotValidException, IllegalArgumentException {
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
                    throw new MethodArgumentNotValidException(methodParameter, binder.getBindingResult());
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

    private record SpecRegistration(
        SpecType type,
        Class<? extends Spec> spec,
        SpecFactory<? extends Spec> factory,
        Schema schema
    ) {}
}
