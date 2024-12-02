package it.smartcommunitylabdhub.core.fsm;

import it.smartcommunitylabdhub.commons.infrastructure.TriggerRun;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.fsm.FsmState;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class TriggerFsmFactory extends AbstractFsmFactory<State, TriggerEvent, TriggerContext, TriggerRun> {

    public TriggerFsmFactory(List<FsmState.Builder<State, TriggerEvent, TriggerContext, TriggerRun>> stateBuilders) {
        super(stateBuilders);
    }
}
