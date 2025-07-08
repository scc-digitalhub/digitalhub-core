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

package it.smartcommunitylabdhub.runtime.kfp.mapper;

import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1NodeStatus;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Template;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow;
import it.smartcommunitylabdhub.framework.argo.objects.K8sWorkflowObject;
import it.smartcommunitylabdhub.runtime.kfp.dtos.NodeStatusDTO;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class NodeStatusMapper {

    private static final String LABEL_PREFIX = "kfp-digitalhub-runtime-"; // Replace this with the actual prefix

    public List<NodeStatusDTO> extractNodesFromWorkflow(K8sWorkflowObject workflowObject) {
        if (
            workflowObject == null ||
            workflowObject.getWorkflow() == null ||
            workflowObject.getWorkflow().getStatus() == null
        ) {
            return null;
        }

        IoArgoprojWorkflowV1alpha1Workflow workflow = workflowObject.getWorkflow();

        List<NodeStatusDTO> nodes = workflow
            .getStatus()
            .getNodes()
            .values()
            .stream()
            .map(nodeStatus -> {
                NodeStatusDTO dto = new NodeStatusDTO();

                dto.setId(nodeStatus.getId());
                dto.setName(nodeStatus.getName());
                dto.setDisplayName(nodeStatus.getDisplayName());
                dto.setType(nodeStatus.getType());
                dto.setChildren(nodeStatus.getChildren()); // You can adjust this if you need a specific structure
                dto.setState(nodeStatus.getPhase());
                dto.setStartTime(nodeStatus.getStartedAt());
                dto.setEndTime(nodeStatus.getFinishedAt());
                dto.setExitCode(nodeStatus.getOutputs() != null ? nodeStatus.getOutputs().getExitCode() : null);

                // Handle inputs and outputs as DTOs
                if (nodeStatus.getInputs() != null && nodeStatus.getInputs().getParameters() != null) {
                    List<Map<String, String>> inputsParams = nodeStatus
                        .getInputs()
                        .getParameters()
                        .stream()
                        .filter(param -> param.getValue() != null)
                        .map(param -> Map.of("name", param.getName(), "value", param.getValue()))
                        .collect(Collectors.toList());

                    dto.setInputs(inputsParams);
                }

                if (nodeStatus.getOutputs() != null && nodeStatus.getOutputs().getParameters() != null) {
                    List<Map<String, String>> outputsParams = nodeStatus
                        .getOutputs()
                        .getParameters()
                        .stream()
                        .filter(param -> param.getValue() != null)
                        .map(param -> Map.of("name", param.getName(), "value", param.getValue()))
                        .collect(Collectors.toList());

                    dto.setOutputs(outputsParams);
                }

                // Process labels from workflow metadata
                Optional
                    .ofNullable(workflow.getSpec().getTemplates())
                    .ifPresent(templates ->
                        templates
                            .stream()
                            .filter(t -> t.getName() != null && t.getName().equals(nodeStatus.getTemplateName()))
                            .map(IoArgoprojWorkflowV1alpha1Template::getMetadata)
                            .filter(metadata -> metadata != null && metadata.getLabels() != null)
                            .flatMap(metadata -> metadata.getLabels().entrySet().stream())
                            .filter(entry -> entry.getKey().startsWith(LABEL_PREFIX))
                            .forEach(entry -> {
                                String value = entry.getValue();
                                switch (entry.getKey()) {
                                    case LABEL_PREFIX + "function":
                                        dto.setFunction(value);
                                        break;
                                    case LABEL_PREFIX + "function_id":
                                        dto.setFunctionId(value);
                                        break;
                                    case LABEL_PREFIX + "action":
                                        dto.setAction(value);
                                        break;
                                    default:
                                        break;
                                }
                            })
                    );

                if (nodeStatus.getType().equals("Pod") && nodeStatus.getOutputs() != null) {
                    //TODO get correct runId
                    // dto.setRunId(runId);
                }

                return dto;
            })
            .collect(Collectors.toList());

        //integrate list of nodes with missing by picking from DAG
        IoArgoprojWorkflowV1alpha1NodeStatus dag = workflow
            .getStatus()
            .getNodes()
            .entrySet()
            .stream()
            .filter(e -> e.getValue().getType().equals("DAG"))
            .findFirst()
            .map(Map.Entry::getValue)
            .orElse(null);

        if (dag != null) {
            //TODO parse
        }

        return nodes;
    }
}
