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

package it.smartcommunitylabdhub.fsm.exceptions;

import java.text.MessageFormat;
import lombok.Getter;

public class InvalidTransitionException extends RuntimeException {

    public static final String DEFAULT_MESSAGE = "Invalid transition from state {0} to state {1}.";

    @Getter
    private final String fromState;

    @Getter
    private final String toState;

    public InvalidTransitionException() {
        super(build("StateA", "StateB"));
        this.fromState = "StateA";
        this.toState = "StateB";
    }

    public InvalidTransitionException(String from, String to) {
        super(build(from, to));
        this.fromState = from;
        this.toState = to;
    }

    public InvalidTransitionException(Throwable cause) {
        super(build("StateA", "StateB"), cause);
        this.fromState = "StateA";
        this.toState = "StateB";
    }

    public InvalidTransitionException(String from, String to, Throwable cause) {
        super(build(from, to), cause);
        this.fromState = from;
        this.toState = to;
    }

    private static String build(String from, String to) {
        return MessageFormat.format(DEFAULT_MESSAGE, from, to);
    }
}
