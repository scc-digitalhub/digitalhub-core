package it.smartcommunitylabdhub.commons.infrastructure;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import it.smartcommunitylabdhub.commons.models.specs.Spec;
import it.smartcommunitylabdhub.commons.models.specs.SpecDTO;
import it.smartcommunitylabdhub.commons.models.status.StatusDTO;
import java.util.List;

public interface ProcessorRegistry<D extends BaseDTO & SpecDTO & StatusDTO> {
    List<Processor<D, ? extends Spec>> getProcessors(String stage);
}
