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

package it.smartcommunitylabdhub.commons.jackson.introspect;

import com.fasterxml.jackson.databind.introspect.AnnotatedMember;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.util.Assert;

public class JsonSchemaAnnotationIntrospector extends JacksonAnnotationIntrospector {

    private final Set<Class<?>> classes;

    public JsonSchemaAnnotationIntrospector() {
        this.classes = null;
    }

    public JsonSchemaAnnotationIntrospector(Collection<Class<?>> classes) {
        Assert.notNull(classes, "please provide a valid class list");
        this.classes = Collections.unmodifiableSet(new HashSet<>(classes));
    }

    public JsonSchemaAnnotationIntrospector(Class<?>... classes) {
        this(Arrays.asList(classes));
    }

    @Override
    public boolean hasIgnoreMarker(final AnnotatedMember m) {
        // first check annotation
        JsonSchemaIgnore ann = _findAnnotation(m, JsonSchemaIgnore.class);
        if (ann != null) {
            return ann.value();
        }

        // check class match
        if (classes != null && classes.contains(m.getDeclaringClass())) {
            return true;
        }

        // delegate
        return super.hasIgnoreMarker(m);
    }
}
