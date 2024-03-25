package it.smartcommunitylabdhub.runtime.kaniko.specs.task;

import it.smartcommunitylabdhub.commons.infrastructure.SpecFactory;
import java.io.Serializable;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TaskBuildSpecFactory implements SpecFactory<TaskBuildSpec> {

    @Override
    public TaskBuildSpec create() {
        return new TaskBuildSpec();
    }

    @Override
    public TaskBuildSpec create(Map<String, Serializable> data) {
        return new TaskBuildSpec(data);
    }
}
