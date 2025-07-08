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

package it.smartcommunitylabdhub.runtimes.events;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Builder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RunnableChangedEvent<R extends RunRunnable> {

    private R runnable;
    private String state;
    private String previousState;

    public String getId() {
        return runnable != null ? runnable.getId() : null;
    }

    public String getState() {
        return state != null ? state : runnable != null ? runnable.getState() : null;
    }

    public static <R extends RunRunnable> RunnableChangedEvent<R> build(R runnable, String previousState) {
        return new RunnableChangedEvent<>(runnable, null, previousState);
    }
}
