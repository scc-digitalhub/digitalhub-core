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

package it.smartcommunitylabdhub.core.components.infrastructure.processors;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.metadata.Metadata;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.Status;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.util.StringUtils;

/**
 * Scans the classpath for all concrete classes implementing {@code BaseDTO & SpecDTO & StatusDTO}
 * and registers a {@link ProcessorRegistryImpl} singleton for each combination of
 * {@code <D, Spec>}, {@code <D, Metadata>} and {@code <D, Status>}.
 *
 * <p>Implements {@link BeanDefinitionRegistryPostProcessor} so that the registries are available
 * in the bean factory <em>before</em> any singleton is instantiated, ensuring that
 * {@code @Autowired} setter injection in {@code BaseLifecycleManager} subclasses finds them.
 */
@Slf4j
@Configuration
public class ProcessorRegistryInitializer implements BeanDefinitionRegistryPostProcessor, ApplicationContextAware {

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.setResourceLoader(applicationContext);
        scanner.addIncludeFilter(new AssignableTypeFilter(BaseDTO.class));

        Set<BeanDefinition> candidates = scanner.findCandidateComponents("it.smartcommunitylabdhub");

        for (BeanDefinition bd : candidates) {
            String className = bd.getBeanClassName();
            try {
                Class<?> cls = Class.forName(className);

                if (
                    !BaseDTO.class.isAssignableFrom(cls) ||
                    !SpecDTO.class.isAssignableFrom(cls) ||
                    !StatusDTO.class.isAssignableFrom(cls)
                ) {
                    continue;
                }

                String simpleName = cls.getSimpleName();

                register(registry, cls, Spec.class, StringUtils.uncapitalize(simpleName) + "SpecProcessorRegistry");
                register(
                    registry,
                    cls,
                    Metadata.class,
                    StringUtils.uncapitalize(simpleName) + "MetadataProcessorRegistry"
                );
                register(registry, cls, Status.class, StringUtils.uncapitalize(simpleName) + "StatusProcessorRegistry");
            } catch (ClassNotFoundException e) {
                log.warn("Could not load DTO candidate class '{}': {}", className, e.getMessage());
            }
        }
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        // nothing — all work is done in postProcessBeanDefinitionRegistry
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void register(
        BeanDefinitionRegistry registry,
        Class<?> dtoClass,
        Class<? extends Spec> specClass,
        String beanName
    ) {
        // RootBeanDefinition with an explicit targetType carrying the full generics is the only
        // reliable way for Spring to resolve @Autowired injection points by generic type.
        // registerSingleton() does not attach a BeanDefinition, so Spring falls back to raw-type
        // matching and sees all beans as candidates for every ProcessorRegistry<D, ?> injection.
        ResolvableType targetType = ResolvableType.forClassWithGenerics(
            ProcessorRegistryImpl.class,
            dtoClass,
            specClass
        );
        RootBeanDefinition bd = new RootBeanDefinition();
        bd.setTargetType(targetType);
        bd.setInstanceSupplier(() -> new ProcessorRegistryImpl(applicationContext, dtoClass, specClass) {});

        registry.registerBeanDefinition(beanName, bd);

        log.debug(
            "Registered ProcessorRegistry<{}, {}> as bean '{}'",
            dtoClass.getSimpleName(),
            specClass.getSimpleName(),
            beanName
        );
    }
}
