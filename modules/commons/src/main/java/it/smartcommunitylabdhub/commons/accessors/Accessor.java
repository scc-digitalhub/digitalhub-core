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

package it.smartcommunitylabdhub.commons.accessors;

import java.util.Map;
import org.springframework.util.Assert;

/**
 * Define base accessor
 */
public interface Accessor<T> {
    /**
     * Return a map of fields
     *
     * @return {@code Map} of claims
     */
    Map<String, T> fields();

    // /**
    //  * build the map of fields
    //  *
    //  * @param fields {@code Map} of claims
    //  */
    // void configure(Map<String, T> fields);

    /**
     * Returns the claim value as a {@code T} type. The claim value is expected to be of type
     * {@code T}.
     *
     * @param field the name of the field
     * @return
     */
    //TODO support traversal in nested structures with .
    @SuppressWarnings("unchecked")
    default <K extends T> K get(String field) {
        Assert.notNull(field, "field cannot be null");
        try {
            return !hasField(field) ? null : (K) fields().get(field);
        } catch (ClassCastException e) {
            return null;
        }
    }

    /**
     * Check if field exists.
     *
     * @param field
     * @return {@code true} if the field exists, otherwise {@code false}
     */
    default boolean hasField(String field) {
        return fields().containsKey(field);
    }
    // /**
    //  * Given a map and a field check if field exists.
    //  *
    //  * @param field
    //  * @param map
    //  * @return {@code true} if the field exists, otherwise {@code false}
    //  */
    // default boolean mapHasField(Map<String, T> map, String field) {
    //     return map.containsKey(field);
    // }
}
