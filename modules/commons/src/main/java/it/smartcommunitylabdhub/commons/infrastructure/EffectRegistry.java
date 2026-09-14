package it.smartcommunitylabdhub.commons.infrastructure;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import java.util.List;

public interface EffectRegistry<D extends BaseDTO & SpecDTO & StatusDTO> {
    List<Effect<D>> getEffects(String stage);
}
