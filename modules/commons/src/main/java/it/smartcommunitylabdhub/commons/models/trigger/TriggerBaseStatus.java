package it.smartcommunitylabdhub.commons.models.trigger;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.base.BaseSpec;
import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Builder(builderMethodName = "baseBuilder")
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriggerBaseStatus extends BaseSpec {

    private String state;
    private String message;

    public TriggerBaseStatus(String state) {
        this.state = state;
    }

    @Override
    public void configure(Map<String, Serializable> data) {
        TriggerBaseStatus spec = mapper.convertValue(data, TriggerBaseStatus.class);

        this.state = spec.getState();
        this.message = spec.getMessage();
    }

    public static TriggerBaseStatus with(Map<String, Serializable> data) {
        TriggerBaseStatus spec = new TriggerBaseStatus();
        spec.configure(data);

        return spec;
    }
}
