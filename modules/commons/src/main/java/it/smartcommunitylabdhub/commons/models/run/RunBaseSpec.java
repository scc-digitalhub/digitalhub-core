package it.smartcommunitylabdhub.commons.models.run;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import jakarta.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RunBaseSpec extends BaseSpec {

    @NotEmpty
    private String task;

    @JsonProperty("local_execution")
    private Boolean localExecution = false;

    @Override
    public void configure(Map<String, Serializable> data) {
        RunBaseSpec runBaseSpec = mapper.convertValue(data, RunBaseSpec.class);

        this.task = runBaseSpec.getTask();
        this.localExecution = runBaseSpec.getLocalExecution();
    }

    public static RunBaseSpec with(Map<String, Serializable> data) {
        RunBaseSpec spec = new RunBaseSpec();
        spec.configure(data);

        return spec;
    }
}
