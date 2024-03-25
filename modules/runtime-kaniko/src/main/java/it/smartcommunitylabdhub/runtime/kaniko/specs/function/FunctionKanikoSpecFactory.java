package it.smartcommunitylabdhub.runtime.kaniko.specs.function;

import it.smartcommunitylabdhub.commons.infrastructure.SpecFactory;
import java.io.Serializable;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FunctionKanikoSpecFactory implements SpecFactory<FunctionKanikoSpec> {

    @Override
    public FunctionKanikoSpec create() {
        return new FunctionKanikoSpec();
    }

    @Override
    public FunctionKanikoSpec create(Map<String, Serializable> data) {
        return new FunctionKanikoSpec(data);
    }
}
