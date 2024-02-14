package it.smartcommunitylabdhub.runtime.container.models.specs.task;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = "container+serve", entity = EntityName.TASK)
public class TaskServeSpec extends TaskDeploySpec {

    /// TODO: Service parameters port list...ClusterIP or NodePort

    @Override
    public void configure(Map<String, Object> data) {
        TaskServeSpec taskDeploySpec = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(data, TaskServeSpec.class);

        super.configure(data);
        this.setExtraSpecs(taskDeploySpec.getExtraSpecs());
    }
}
