package it.smartcommunitylabdhub.runtime.kfp.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1NodeStatus;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Template;
import io.argoproj.workflow.models.IoArgoprojWorkflowV1alpha1Workflow;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.framework.argo.objects.K8sWorkflowObject;
import it.smartcommunitylabdhub.framework.k8s.jackson.IntOrStringMixin;
import it.smartcommunitylabdhub.framework.k8s.jackson.QuantityMixin;
import it.smartcommunitylabdhub.runtime.kfp.dtos.NodeStatusDTO;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class NodeStatusMapper {


    //custom object mapper with mixIn for IntOrString
    protected static final ObjectMapper mapper = JacksonMapper.CUSTOM_OBJECT_MAPPER.addMixIn(
            IntOrString.class,
            IntOrStringMixin.class
    ).addMixIn(Quantity.class, QuantityMixin.class);

    protected static final TypeReference<HashMap<String, IoArgoprojWorkflowV1alpha1NodeStatus>> nodeTypeRef = new TypeReference<
            HashMap<String, IoArgoprojWorkflowV1alpha1NodeStatus>
            >() {
    };

    protected static final TypeReference<IoArgoprojWorkflowV1alpha1Workflow> workflowTypeRef =
            new TypeReference<
                    IoArgoprojWorkflowV1alpha1Workflow
                    >() {
            };

    protected static final TypeReference<List<Serializable>> typeRef = new TypeReference<
            List<Serializable>
            >() {
    };

    protected static final TypeReference<NodeStatusDTO> nodeStatusDTOTypeRef = new TypeReference<NodeStatusDTO>() {
    };

    private static final String LABEL_PREFIX = "kfp-digitalhub-runtime-";  // Replace this with the actual prefix

    public List<NodeStatusDTO> argoNodeToNodeStatusDTO(
            Map<String, Serializable> nodeStatusList,
            Map<String, Serializable> workflowObject,
            Run run) {
        if (nodeStatusList == null) {
            return null;
        }
        Map<String, IoArgoprojWorkflowV1alpha1NodeStatus> nodes =
                mapper.convertValue(nodeStatusList, nodeTypeRef);


        IoArgoprojWorkflowV1alpha1Workflow ioArgoprojWorkflowV1alpha1Workflow =
                mapper.convertValue(workflowObject.get("workflow"), workflowTypeRef);


        K8sWorkflowObject workflow = new K8sWorkflowObject(ioArgoprojWorkflowV1alpha1Workflow);


        return nodes.values().stream().map(nodeStatus -> {
            NodeStatusDTO dto = new NodeStatusDTO();

            dto.setId(nodeStatus.getId());
            dto.setName(nodeStatus.getName());
            dto.setDisplayName(nodeStatus.getDisplayName());
            dto.setType(nodeStatus.getType());
            dto.setChildren(nodeStatus.getChildren());  // You can adjust this if you need a specific structure
            dto.setState(nodeStatus.getPhase());
            dto.setStartTime(nodeStatus.getStartedAt());
            dto.setEndTime(nodeStatus.getFinishedAt());
            dto.setExitCode(nodeStatus.getOutputs() != null ? nodeStatus.getOutputs().getExitCode() : null);

            // Handle inputs and outputs as DTOs
            if (nodeStatus.getInputs() != null && nodeStatus.getInputs().getParameters() != null) {
                List<Map<String, String>> inputsParams = nodeStatus.getInputs().getParameters().stream()
                        .filter(param -> param.getValue() != null)
                        .map(param -> Map.of(param.getName(), param.getValue()))
                        .collect(Collectors.toList());
                dto.setInputs(inputsParams);
            }

            if (nodeStatus.getOutputs() != null && nodeStatus.getOutputs().getParameters() != null) {
                List<Map<String, String>> outputsParams = nodeStatus.getOutputs().getParameters().stream()
                        .filter(param -> param.getValue() != null)
                        .map(param -> Map.of(param.getName(), param.getValue()))
                        .collect(Collectors.toList());
                dto.setOutputs(outputsParams);
            }

            // Process labels from workflow metadata
            Optional.ofNullable(workflow.getWorkflow().getSpec().getTemplates())
                    .ifPresent(templates -> templates.stream()
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
                                    case LABEL_PREFIX + "project":
                                        dto.setAction(value);
                                        break;
                                }
                            }));


            if (nodeStatus.getType().equals("Pod")
                    && nodeStatus.getOutputs() != null) {
                dto.setRunId(run.getId());
            }

            return dto;

        }).collect(Collectors.toList());
    }

    public List<Serializable> toMap(List<NodeStatusDTO> nodeStatusDTOList) {
        if (nodeStatusDTOList == null) {
            return null;
        }
        return mapper.convertValue(nodeStatusDTOList, typeRef);
    }


    public List<NodeStatusDTO> fromMap(List<Map<String, Serializable>> map) {
        if (map == null) {
            return null;
        }
        return map.stream()
                .filter(Objects::nonNull)
                .map(node ->
                        mapper.convertValue(node, nodeStatusDTOTypeRef))
                .collect(Collectors.toList());
    }
}
