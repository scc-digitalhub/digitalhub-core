package it.smartcommunitylabdhub.commons.models.trigger;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.infrastructure.TriggerRun;
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
@Builder
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TriggerRunBaseStatus extends BaseSpec {

    private TriggerRun trigger;

    @Override
    public void configure(Map<String, Serializable> data) {
        TriggerRunBaseStatus spec = mapper.convertValue(data, TriggerRunBaseStatus.class);

        this.trigger = spec.getTrigger();
    }

    public static TriggerRunBaseStatus with(Map<String, Serializable> data) {
        TriggerRunBaseStatus spec = new TriggerRunBaseStatus();
        spec.configure(data);

        return spec;
    }
}
