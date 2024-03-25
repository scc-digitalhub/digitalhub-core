package it.smartcommunitylabdhub.runtime.kaniko.specs.task;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.task.TaskBaseSpec;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.framework.k8s.base.K8sTaskSpec;
import it.smartcommunitylabdhub.runtime.kaniko.KanikoRuntime;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = KanikoRuntime.RUNTIME, kind = TaskBuildSpec.KIND, entity = EntityName.TASK)
public class TaskBuildSpec extends TaskBaseSpec {

    public static final String KIND = "kaniko+build";

    private K8sTaskSpec k8s = new K8sTaskSpec();

    public TaskBuildSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TaskBuildSpec spec = mapper.convertValue(data, TaskBuildSpec.class);
        this.k8s = spec.getK8s();
    }
}
