package it.smartcommunitylabdhub.trigger.scheduler.models;

import it.smartcommunitylabdhub.commons.Keys;
import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseSpec;
import jakarta.validation.constraints.Pattern;
import java.io.Serializable;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = "scheduler", entity = EntityName.TRIGGER)
public class SchedulerTriggerSpec extends TriggerBaseSpec {

    @Pattern(regexp = Keys.CRONTAB_PATTERN)
    private String schedule;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        SchedulerTriggerSpec spec = mapper.convertValue(data, SchedulerTriggerSpec.class);
        this.schedule = spec.getSchedule();
    }
}
