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

package it.smartcommunitylabdhub.commons.models.trigger;

import it.smartcommunitylabdhub.commons.models.relationships.RelationshipDetail;
import java.io.Serializable;
import java.util.List;

public interface TriggerJob extends Serializable {
    String getId();
    String getActuator();
    String getTask();
    String getProject();
    String getUser();

    String getState();
    String getMessage();
    String getError();

    void setUser(String user);
    void setState(String state);
    void setMessage(String message);
    void setError(String error);

    default List<RelationshipDetail> getRelationships() {
        return null;
    }
}
