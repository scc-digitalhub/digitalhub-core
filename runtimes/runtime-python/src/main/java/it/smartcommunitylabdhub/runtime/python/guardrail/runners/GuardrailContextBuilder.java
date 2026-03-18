package it.smartcommunitylabdhub.runtime.python.guardrail.runners;

import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.runtime.python.guardrail.specs.GuardrailFunctionSpec;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class GuardrailContextBuilder {

    private static final int HTTP_PORT = 5051;
    public static final int MAX_WORKERS = 4;

    public static HashMap<String, Serializable> buildTriggers(Run run, GuardrailFunctionSpec functionSpec) {
        //define extproc trigger
        HashMap<String, Serializable> triggers = new HashMap<>();
        HashMap<String, Serializable> attributes = new HashMap<>();
        attributes.put("type", functionSpec.getProcessingMode().getMode());
        attributes.put("port", HTTP_PORT);
        HashMap<String, Serializable> extproc = new HashMap<>(
            Map.of("kind", "extproc", "maxWorkers", MAX_WORKERS, "attributes", attributes)
        );
        triggers.put("extproc", extproc);
        return triggers;
    }

    protected GuardrailContextBuilder() {}
}
