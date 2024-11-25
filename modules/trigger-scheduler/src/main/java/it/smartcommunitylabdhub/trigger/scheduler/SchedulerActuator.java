package it.smartcommunitylabdhub.trigger.scheduler;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.ActuatorComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Actuator;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseStatus;
import it.smartcommunitylabdhub.trigger.scheduler.models.SchedulerTriggerSpec;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@ActuatorComponent(actuator = SchedulerActuator.ACTUATOR)
public class SchedulerActuator implements Actuator<SchedulerTriggerSpec, TriggerBaseStatus> {

    public static final String ACTUATOR = "scheduler";

    @Autowired
    ApplicationEventPublisher eventPublisher;

    @Override
    public TriggerBaseStatus register(@NotNull Trigger trigger) {
        //TODO add to quartz
    }

    @Override
    public TriggerBaseStatus unregister(@NotNull Trigger trigger) {
        //TODO clear from quartz
    }
}
