package it.smartcommunitylabdhub.runtime.kaniko.specs.task;

import it.smartcommunitylabdhub.commons.infrastructure.SpecFactory;
import java.io.Serializable;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskBuildPythonSpecFactory implements SpecFactory<TaskBuildJavaSpec> {

    @Override
    public TaskBuildJavaSpec create() {
        return new TaskBuildJavaSpec();
    }

    @Override
    public TaskBuildJavaSpec create(Map<String, Serializable> data) {
        return new TaskBuildJavaSpec(data);
    }
}
