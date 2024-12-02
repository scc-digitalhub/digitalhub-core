package it.smartcommunitylabdhub.commons.models.trigger;

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
public class TriggerBaseSpec extends BaseSpec {

    @NotEmpty
    private String task;

    @NotEmpty
    private String run;

    @NotEmpty
    private Map<String, Serializable> template;

    @Override
    public void configure(Map<String, Serializable> data) {
        TriggerBaseSpec spec = mapper.convertValue(data, TriggerBaseSpec.class);

        this.task = spec.getTask();
        this.run = spec.getRun();
        this.template = spec.getTemplate();
    }

    public static TriggerBaseSpec from(Map<String, Serializable> data) {
        TriggerBaseSpec spec = new TriggerBaseSpec();
        spec.configure(data);

        return spec;
    }
}
