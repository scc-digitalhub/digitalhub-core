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
import org.springframework.util.StringUtils;

public interface TaskSpecAccessor extends Accessor<String> {
    default String getRuntime() {
        return get(Fields.RUNTIME);
    }

    default String getProject() {
        return get(Fields.PROJECT);
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

    //get function *key*
    default String getFunctionKey() {
        return get("functionKey");
    }

    //get workflow *key*
    default String getWorkflowKey() {
        return get("workflowKey");
    }

    default boolean isValid() {
        return (
            getProject() != null &&
            getRuntime() != null &&
            ((getFunction() != null && getFunctionId() != null) || (getWorkflow() != null && getWorkflowId() != null))
        );
    }

    /**
     * Build an accessor over a task spec map.
     * We expect to find either a function or a workflow ref to parse
     * @param map
     * @return
     */
    static TaskSpecAccessor with(Map<String, Serializable> map) {
        Serializable fn = map.getOrDefault(Fields.FUNCTION, "");
        Serializable wk = map.getOrDefault(Fields.WORKFLOW, "");

        if (!(fn instanceof String) || !(wk instanceof String)) {
            throw new IllegalArgumentException("Cannot create accessor for the given function/workflow string.");
        }

        //try function first
        String function = (String) fn;
        if (StringUtils.hasText(function)) {
            Matcher matcher = Pattern.compile(Keys.FUNCTION_PATTERN).matcher(function);
            if (matcher.matches()) {
                Map<String, String> m = new HashMap<>();
                m.put(Fields.RUNTIME, matcher.group(1));
                m.put(Fields.PROJECT, matcher.group(2));
                m.put(Fields.FUNCTION, matcher.group(3));
                m.put("functionId", matcher.group(4));
                m.put("functionKey", function);

                return () -> m;
            } else {
                //keep as is
                Map<String, String> m = new HashMap<>();
                m.put("functionKey", function);
                return () -> m;
            }
        }

        //try workflow
        String workflow = (String) wk;
        if (StringUtils.hasText(workflow)) {
            Matcher matcher = Pattern.compile(Keys.WORKFLOW_PATTERN).matcher(workflow);
            if (matcher.matches()) {
                Map<String, String> m = new HashMap<>();
                m.put(Fields.RUNTIME, matcher.group(1));
                m.put(Fields.PROJECT, matcher.group(2));
                m.put(Fields.WORKFLOW, matcher.group(3));
                m.put("workflowId", matcher.group(4));
                m.put("workflowKey", (workflow));

                return () -> m;
            } else {
                //keep as is
                Map<String, String> m = new HashMap<>();
                m.put("workflowKey", (workflow));
                return () -> m;
            }
        }
        throw new IllegalArgumentException("Cannot create accessor for the given task string.");
    }

    static String build(String runtime, String project, String function, String functionId) {
        return String.format("%s://%s/%s:%s", runtime, project, function, functionId);
    }
}
