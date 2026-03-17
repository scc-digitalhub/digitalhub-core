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

package it.smartcommunitylabdhub.runtime.python.openinference.runners;

import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.runtime.python.openinference.OpeninferenceRuntime;
import it.smartcommunitylabdhub.runtime.python.openinference.specs.OpeninferenceFunctionSpec;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class OpeninferenceContextBuilder {

    public static final int MAX_WORKERS = 4;

    public static HashMap<String, Serializable> buildTriggers(Run run, OpeninferenceFunctionSpec functionSpec) {
        //define extproc trigger
        HashMap<String, Serializable> triggers = new HashMap<>();
        HashMap<String, Serializable> attributes = new HashMap<>();
        attributes.put("rest_port", OpeninferenceRuntime.HTTP_PORT);
        attributes.put("grpc_port", OpeninferenceRuntime.GRPC_PORT);
        attributes.put("model_name", functionSpec.getModelName());
        attributes.put("model_version", run.getId());
        attributes.put("enable_rest", true);
        attributes.put("enable_grpc", true);

        LinkedList<Map<String, Serializable>> inputs = new LinkedList<>();
        attributes.put("input_tensors", inputs);
        functionSpec
            .getInputs()
            .forEach(i -> {
                Map<String, Serializable> input = new HashMap<>();
                input.put("name", i.getName());
                input.put("datatype", i.getDatatype());
                input.put("shape", i.getShape().stream().mapToInt(Integer::intValue).toArray());
                inputs.add(input);
            });

        LinkedList<Map<String, Serializable>> outputs = new LinkedList<>();
        attributes.put("output_tensors", outputs);
        functionSpec
            .getOutputs()
            .forEach(o -> {
                Map<String, Serializable> output = new HashMap<>();
                output.put("name", o.getName());
                output.put("datatype", o.getDatatype());
                output.put("shape", o.getShape().stream().mapToInt(Integer::intValue).toArray());
                outputs.add(output);
            });

        HashMap<String, Serializable> openinference = new HashMap<>(
            Map.of("kind", "openinference", "maxWorkers", MAX_WORKERS, "attributes", attributes)
        );
        triggers.put("openinference", openinference);
        return triggers;
    }

    protected OpeninferenceContextBuilder() {
        //protected constructor to prevent instantiation
    }
}
