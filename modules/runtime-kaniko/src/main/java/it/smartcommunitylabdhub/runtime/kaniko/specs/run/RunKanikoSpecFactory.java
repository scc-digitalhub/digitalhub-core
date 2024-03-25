package it.smartcommunitylabdhub.runtime.kaniko.specs.run;

import it.smartcommunitylabdhub.commons.infrastructure.SpecFactory;
import java.io.Serializable;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RunKanikoSpecFactory implements SpecFactory<RunKanikoSpec> {

    @Override
    public RunKanikoSpec create() {
        return new RunKanikoSpec();
    }

    @Override
    public RunKanikoSpec create(Map<String, Serializable> data) {
        return new RunKanikoSpec(data);
    }
}
