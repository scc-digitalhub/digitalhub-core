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

package it.smartcommunitylabdhub.commons.models.specs;

import java.io.Serializable;
import java.util.Map;

/**
 * A SPEC is a static view on a map: it fully describes the content by mapping variables
 * into named (and typed) fields.
 *
 * Base spec interface. Should be implemented by all specific Spec Object.
 */
public interface Spec {
    Map<String, Serializable> toMap();

    void configure(Map<String, Serializable> data);
}
