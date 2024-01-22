package it.smartcommunitylabdhub.modules.nefertem.models.accessors.function;

import it.smartcommunitylabdhub.core.annotations.common.AccessorType;
import it.smartcommunitylabdhub.core.components.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.core.models.accessors.AbstractFieldAccessor;
import it.smartcommunitylabdhub.core.models.accessors.kinds.interfaces.FunctionFieldAccessor;

@AccessorType(kind = "nefertem", entity = EntityName.FUNCTION, factory = NefertemFunctionFieldAccessor.class)
public class NefertemFunctionFieldAccessor
        extends AbstractFieldAccessor<NefertemFunctionFieldAccessor>
        implements FunctionFieldAccessor<NefertemFunctionFieldAccessor> {

}
