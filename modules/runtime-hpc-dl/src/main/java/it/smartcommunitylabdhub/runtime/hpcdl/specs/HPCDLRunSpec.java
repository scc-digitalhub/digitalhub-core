package it.smartcommunitylabdhub.runtime.hpcdl.specs;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import io.swagger.v3.oas.annotations.media.Schema;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.jackson.annotations.JsonSchemaIgnore;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.run.RunBaseSpec;
import it.smartcommunitylabdhub.runtime.hpcdl.HPCDLRuntime;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@SpecType(runtime = HPCDLRuntime.RUNTIME, kind = HPCDLRunSpec.KIND, entity = EntityName.RUN)
public class HPCDLRunSpec extends RunBaseSpec {

    public static final String KIND = HPCDLRuntime.RUNTIME + "+run";

    // map path to artifact key
    private Map<String, String> inputs = new HashMap<>();

    // map path to artifact name
    private Map<String, String> outputs = new HashMap<>();

    // @JsonProperty("job_spec")
    @JsonUnwrapped
    private HPCDLJobTaskSpec taskJobSpec;


    // @JsonProperty("function_spec")
    @JsonSchemaIgnore
    @JsonUnwrapped
    private HPCDLFunctionSpec functionSpec;

    @Schema(title = "fields.hpcdl.args.title", description = "fields.hpcdl.args.description")
    private List<String> args;

    public HPCDLRunSpec(Map<String, Serializable> data) {
        configure(data);
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        HPCDLRunSpec spec = mapper.convertValue(data, HPCDLRunSpec.class);

        this.taskJobSpec = spec.getTaskJobSpec();
        this.functionSpec = spec.getFunctionSpec();
        this.inputs = spec.getInputs();
        this.outputs = spec.getOutputs();
        this.args = spec.getArgs();
    }

    public void setFunctionSpec(HPCDLFunctionSpec functionSpec) {
        this.functionSpec = functionSpec;
    }

    public void setTaskJobSpec(HPCDLJobTaskSpec taskJobSpec) {
        this.taskJobSpec = taskJobSpec;
    }
}
