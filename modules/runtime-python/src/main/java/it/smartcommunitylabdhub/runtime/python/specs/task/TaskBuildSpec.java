package it.smartcommunitylabdhub.runtime.python.specs.task;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import it.smartcommunitylabdhub.framework.k8s.base.K8sTaskBaseSpec;
import it.smartcommunitylabdhub.framework.kaniko.runnables.ContextRef;
import it.smartcommunitylabdhub.framework.kaniko.runnables.ContextSource;
import it.smartcommunitylabdhub.runtime.python.PythonRuntime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = PythonRuntime.RUNTIME, kind = TaskBuildSpec.KIND, entity = EntityName.TASK)
public class TaskBuildSpec extends K8sTaskBaseSpec {

    public static final String KIND = "python+build";

    @JsonProperty("context_refs")
    private List<ContextRef> contextRefs;

    @JsonProperty("context_sources")
    private List<ContextSource> contextSources;

    private Integer replicas;

    private List<String> instructions;

    public TaskBuildSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        TaskBuildSpec spec = mapper.convertValue(data, TaskBuildSpec.class);
        this.replicas = spec.getReplicas();
        this.instructions = spec.getInstructions(); //  instructions
        this.contextRefs = spec.getContextRefs(); // List of context refs that need to be materialized
        this.contextSources = spec.getContextSources(); // List of context sources that need to be materialized
    }
}
