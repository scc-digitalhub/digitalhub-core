package it.smartcommunitylabdhub.runtime.nefertem.specs.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.models.entities.task.TaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.base.K8sTaskBaseSpec;
import it.smartcommunitylabdhub.framework.k8s.base.K8sTaskSpec;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TaskNefertemBaseSpec extends K8sTaskBaseSpec {

    protected String framework;

    @JsonProperty("exec_args")
    protected Map<String, Serializable> execArgs;

    protected Boolean parallel;

    @JsonProperty("num_worker")
    protected Integer numWorker;


    public TaskNefertemBaseSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TaskNefertemBaseSpec spec = mapper.convertValue(data, TaskNefertemBaseSpec.class);

        this.framework = spec.getFramework();
        this.execArgs = spec.getExecArgs();
        this.parallel = spec.getParallel();
        this.numWorker = spec.getNumWorker();

    }
}
