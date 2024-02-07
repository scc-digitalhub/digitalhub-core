package it.smartcommunitylabdhub.modules.container.models.specs.run;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import it.smartcommunitylabdhub.core.annotations.common.SpecType;
import it.smartcommunitylabdhub.core.components.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.core.models.entities.run.specs.RunBaseSpec;
import it.smartcommunitylabdhub.core.models.entities.task.specs.K8sTaskBaseSpec;
import it.smartcommunitylabdhub.core.utils.jackson.JacksonMapper;
import it.smartcommunitylabdhub.modules.container.models.specs.function.FunctionContainerSpec;
import lombok.*;

import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@SpecType(kind = "run+container", entity = EntityName.RUN, factory = RunContainerSpec.class)
public class RunContainerSpec<T extends K8sTaskBaseSpec> extends RunBaseSpec {

    @JsonProperty("task_spec")
    private T taskSpec;

    @JsonProperty("func_spec")
    private FunctionContainerSpec funcSpec;

    @Override
    public void configure(Map<String, Object> data) {

        TypeReference<RunContainerSpec<T>> typeReference = new TypeReference<>() {
        };
        RunContainerSpec<T> runContainerSpec = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(data, typeReference);


        this.setTaskSpec(runContainerSpec.getTaskSpec());
        this.setFuncSpec(runContainerSpec.getFuncSpec());

        super.configure(data);
        this.setExtraSpecs(runContainerSpec.getExtraSpecs());
    }
}