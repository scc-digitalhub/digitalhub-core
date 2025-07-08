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

package it.smartcommunitylabdhub.commons.exceptions;

import java.text.MessageFormat;

public class DuplicatedEntityException extends Exception {

    public static final String DEFAULT_MESSAGE = "Duplicated entity {0}:{1}.";

    private static String build(String entity, String id) {
        return MessageFormat.format(DEFAULT_MESSAGE, entity, id);
    }

    public DuplicatedEntityException(String id) {
        super(build("entity", id));
    }

    public DuplicatedEntityException(String entity, String id) {
        super(build(entity, id));
    }

    public DuplicatedEntityException(String id, Throwable cause) {
        super(build("entity", id), cause);
    }

    public DuplicatedEntityException(String entity, String id, Throwable cause) {
        super(build(entity, id), cause);
    }
}
