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

package it.smartcommunitylabdhub.commons.accessors.spec;

import it.smartcommunitylabdhub.commons.Fields;
import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.accessors.Accessor;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface RunSpecAccessor extends Accessor<Serializable> {
    /*
     * from run specs
     */
    //get task *kind*
    default String getTask() {
        return get(Fields.TASK);
    }

    default String getTaskId() {
        return get("taskId");
    }

    default String getProject() {
        return get(Fields.PROJECT);
    }

    default Boolean getLocalExecution() {
        return get("local_execution");
    }

    default boolean isLocalExecution() {
        return getLocalExecution() != null ? getLocalExecution().booleanValue() : false;
    }

    /*
     * from task specs
     */
    default String getRuntime() {
        return get(Fields.RUNTIME);
    }

    //get function *name*
    default String getFunction() {
        return get(Fields.FUNCTION);
    }

    //get workflow *name*
    default String getWorkflow() {
        return get(Fields.WORKFLOW);
    }

    //get function *id*
    default String getFunctionId() {
        return get("functionId");
    }

    //get workflow *id*
    default String getWorkflowId() {
        return get("workflowId");
    }

    default boolean isValid() {
        return getProject() != null && getTask() != null && getTaskId() != null;
    }

    // static RunSpecAccessor with(Map<String, String> map) {
    //     return () -> map;
    // }

    /**
     * Build an accessor over a run spec map.
     * We expect to find a task ref to parse, and optionally a function or workflow ref
     * @param map
     * @return
     */
    static RunSpecAccessor with(Map<String, Serializable> map) {
        Serializable tsk = map.getOrDefault(Fields.TASK, "");

        if (!(tsk instanceof String)) {
            throw new IllegalArgumentException("Cannot create accessor for the given task string.");
        }

        Map<String, Serializable> m = new HashMap<>();

        //try to parse task spec as well
        try {
            TaskSpecAccessor taskSpecAccessor = TaskSpecAccessor.with(map);
            m.putAll(taskSpecAccessor.fields());
        } catch (IllegalArgumentException e) {
            //missing or invalid, skip
        }

        //try parse
        Matcher matcher = Pattern.compile(Keys.TASK_PATTERN).matcher((String) tsk);
        if (matcher.matches()) {
            String task = matcher.group(1);
            String project = matcher.group(2);
            String taskId = matcher.group(3);

            m.put(Fields.TASK, task);
            m.put(Fields.PROJECT, project);
            m.put("taskId", taskId);

            return () -> m;
        }

        //local execution
        m.put("local_execution", map.get("local_execution"));

        throw new IllegalArgumentException("Cannot create accessor for the given task string.");
    }
}
