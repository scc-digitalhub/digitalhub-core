package it.smartcommunitylabdhub.modules.mlrunOld.components.accessors;

import it.smartcommunitylabdhub.core.annotations.common.AccessorType;
import it.smartcommunitylabdhub.core.components.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.core.models.accessors.AbstractFieldAccessor;
import it.smartcommunitylabdhub.core.models.accessors.kinds.interfaces.FunctionFieldAccessor;


@AccessorType(kind = "nuclio", entity = EntityName.FUNCTION)
public class NuclioFunctionFieldAccessor
        extends AbstractFieldAccessor<NuclioFunctionFieldAccessor>
        implements FunctionFieldAccessor<NuclioFunctionFieldAccessor> {

}
