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
@SpecType(runtime = KanikoRuntime.RUNTIME, kind = TaskBuildJavaSpec.KIND, entity = EntityName.TASK)
public class TaskBuildJavaSpec extends TaskBaseSpec {

    public static final String KIND = "kaniko+buildjava";

    private K8sTaskSpec k8s = new K8sTaskSpec();

    public TaskBuildJavaSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TaskBuildJavaSpec spec = mapper.convertValue(data, TaskBuildJavaSpec.class);
        this.k8s = spec.getK8s();
    }
}
