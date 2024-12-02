package it.smartcommunitylabdhub.trigger.scheduler;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.ActuatorComponent;
import it.smartcommunitylabdhub.commons.infrastructure.Actuator;
import it.smartcommunitylabdhub.commons.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.commons.models.trigger.Trigger;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerBaseStatus;
import it.smartcommunitylabdhub.commons.models.trigger.TriggerRunBaseStatus;
import it.smartcommunitylabdhub.trigger.scheduler.models.SchedulerTriggerSpec;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;

@ActuatorComponent(actuator = SchedulerActuator.ACTUATOR)
public class SchedulerActuator implements Actuator<SchedulerTriggerSpec, TriggerBaseStatus, TriggerRunBaseStatus> {

    public static final String ACTUATOR = "scheduler";

    @Override
    public TriggerBaseStatus run(@NotNull Trigger trigger) {
        //TODO add to quartz
        return null;
    }

    @Override
    public TriggerBaseStatus stop(@NotNull Trigger trigger) {
        //TODO clear from quartz
        return null;
    }

    @Override
    public TriggerRunBaseStatus onFire(@NotNull Trigger trigger, TriggerRun run) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onFire'");
    }
}
