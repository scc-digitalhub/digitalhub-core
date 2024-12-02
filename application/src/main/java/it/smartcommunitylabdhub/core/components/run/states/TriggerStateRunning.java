package it.smartcommunitylabdhub.core.components.run.states;

import it.smartcommunitylabdhub.commons.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.core.fsm.TriggerContext;
import it.smartcommunitylabdhub.core.fsm.TriggerEvent;
import it.smartcommunitylabdhub.fsm.FsmState;
import it.smartcommunitylabdhub.fsm.Transition;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class TriggerStateRunning implements FsmState.Builder<State, TriggerEvent, TriggerContext, TriggerRun> {

    public FsmState<State, TriggerEvent, TriggerContext, TriggerRun> build() {
        //define state
        State state = State.RUNNING;

        //transitions
        List<Transition<State, TriggerEvent, TriggerContext, TriggerRun>> txs = List.of(
            //(FIRE)->RUNNING
            new Transition.Builder<State, TriggerEvent, TriggerContext, TriggerRun>()
                .event(TriggerEvent.FIRE)
                .nextState(State.RUNNING)
                .withInternalLogic((currentState, nextState, event, context, run) -> {
                    //run callback
                    return Optional.ofNullable(context.actuator.onFire(context.trigger, run));
                })
                .build(),
            //(STOP)->STOP
            new Transition.Builder<State, TriggerEvent, TriggerContext, TriggerRun>()
                .event(TriggerEvent.STOP)
                .nextState(State.STOPPED)
                .withInternalLogic((currentState, nextState, event, context, run) -> {
                    //run callback
                    return Optional.ofNullable(context.actuator.stop(context.trigger));
                })
                .build()
        );

        return new FsmState<>(state, txs);
    }
}
