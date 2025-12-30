/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * Copyright 2025 the original author or authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package it.smartcommunitylabdhub.commons.utils;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import jakarta.validation.constraints.NotNull;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AssignableTypeFilter;

public class EntityUtils {

    private EntityUtils() {
        // Utility class, no instantiation
    }

    public static String getEntityName(@NotNull Class<? extends BaseDTO> clazz) {
        return clazz.getSimpleName().toUpperCase();
    }

    @SuppressWarnings("unchecked")
    public static @NotNull List<Class<? extends BaseDTO>> scanForEntities(@NotNull List<String> basePackages) {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AssignableTypeFilter(BaseDTO.class));

        Set<Class<? extends BaseDTO>> classes = new HashSet<>();

        for (String basePackage : basePackages) {
            Set<BeanDefinition> candidateComponents = scanner.findCandidateComponents(basePackage);

            for (BeanDefinition beanDefinition : candidateComponents) {
                String className = beanDefinition.getBeanClassName();
                try {
                    classes.add((Class<? extends BaseDTO>) Class.forName(className));
                } catch (IllegalArgumentException | ClassNotFoundException e) {
                    //skip
                }
            }
        }

        //as unmodifiable list
        return Collections.unmodifiableList(classes.stream().toList());
    }
}
