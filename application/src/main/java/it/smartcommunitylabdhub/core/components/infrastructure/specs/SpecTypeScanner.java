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

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.infrastructure.SpecFactory;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.services.SpecRegistry;
import it.smartcommunitylabdhub.core.CoreApplication;
import jakarta.annotation.PostConstruct;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * The `SpecTypeFactory` class is responsible for scanning the classpath to discover
 * classes annotated with `@SpecType`. It extracts the information from these classes and
 * registers them in the `SpecRegistry` for later instantiation.
 */
@Component
@Slf4j
public class SpecTypeScanner {

    private final SpecRegistry specRegistry;

    /**
     * Constructor to inject the `SpecRegistry` instance.
     *
     * @param specRegistry The `SpecRegistry` used to register discovered spec types.
     */
    public SpecTypeScanner(SpecRegistry specRegistry) {
        this.specRegistry = specRegistry;
    }

    /**
     * This method is annotated with `@PostConstruct`, ensuring it's executed after bean
     * instantiation. It scans the classpath to discover classes annotated with `@SpecType`,
     * extracts the relevant information, and registers them in the `SpecRegistry`.
     */
    @PostConstruct
    @SuppressWarnings("unchecked")
    public void scanForSpecTypes() {
        // Create a component scanner to find classes with SpecType annotations.
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(SpecType.class));

        // Detect the base packages based on ComponentScan annotation in CoreApplication.
        List<String> basePackages = getBasePackages();
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
                    EntityName entity = type.entity();
                    String runtime = type.runtime();

                    if (StringUtils.hasText(runtime)) {
                        //enforce runtime prefix rule on kind
                        if (!kind.startsWith(runtime)) {
                            throw new IllegalArgumentException("invalid kind " + kind + "for runtime " + runtime);
                        }
                    }

                    SpecFactory<? extends Spec> factory = null;

                    //specs MUST have an empty default constructor, let's check
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
                    specRegistry.registerSpec(type, specClass, factory);
                } catch (IllegalArgumentException | ClassNotFoundException e) {
                    log.error("error registering spec {}: {}", className, e.getMessage());
                }
            }
        }
    }

    /**
     * Automatically detects the base packages by inspecting the ComponentScan annotation
     * in the CoreApplication class.
     *
     * @return A list of base packages specified in the ComponentScan annotation.
     */
    private List<String> getBasePackages() {
        List<String> basePackages = new ArrayList<>();
        ComponentScan componentScan = CoreApplication.class.getAnnotation(ComponentScan.class);
        if (componentScan != null) {
            Collections.addAll(basePackages, componentScan.basePackages());
        }
        if (basePackages.isEmpty()) {
            throw new IllegalArgumentException("Base package not specified in @ComponentScan");
        }
        return basePackages;
    }
}
