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
@SpecType(runtime = KanikoRuntime.RUNTIME, kind = TaskBuildPythonSpec.KIND, entity = EntityName.TASK)
public class TaskBuildPythonSpec extends TaskBaseSpec {

    public static final String KIND = "kaniko+buildpython";

    private K8sTaskSpec k8s = new K8sTaskSpec();

    public TaskBuildPythonSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TaskBuildPythonSpec spec = mapper.convertValue(data, TaskBuildPythonSpec.class);
        this.k8s = spec.getK8s();
    }
}
