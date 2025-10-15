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

package it.smartcommunitylabdhub.relationships;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RelationshipName {
    PRODUCEDBY("produced_by"),
    CONSUMES("consumes"),
    RUN_OF("run_of"),
    STEP_OF("step_of");

    private final String value;

    RelationshipName(String value) {
        this.value = value;
    }

    @JsonCreator
    public static RelationshipName from(String value) {
        for (RelationshipName rel : RelationshipName.values()) {
            if (rel.value.equalsIgnoreCase(value)) return rel;
        }
        return null;
    }

    @JsonValue
    public String getValue() {
        return this.value;
    }

    @Override
    public String toString() {
        switch (this) {
            case PRODUCEDBY:
                return "produced_by";
            case CONSUMES:
                return "consumes";
            case RUN_OF:
                return "run_of";
            case STEP_OF:
                return "step_of";
            default:
                return null;
        }
    }
}
