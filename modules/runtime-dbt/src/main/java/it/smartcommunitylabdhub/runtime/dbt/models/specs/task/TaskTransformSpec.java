package it.smartcommunitylabdhub.runtime.dbt.models.specs.task;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.commons.utils.jackson.JacksonMapper;
import it.smartcommunitylabdhub.framework.k8s.base.K8sTaskBaseSpec;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@SpecType(kind = "dbt+transform", entity = EntityName.TASK, factory = TaskTransformSpec.class)
public class TaskTransformSpec extends K8sTaskBaseSpec {

    @Override
    public void configure(Map<String, Object> data) {
        TaskTransformSpec taskTransformSpec = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
                data,
                TaskTransformSpec.class
        );

        super.configure(data);
        this.setExtraSpecs(taskTransformSpec.getExtraSpecs());
    }
}
