package it.smartcommunitylabdhub.core.components.run;

import it.smartcommunitylabdhub.commons.models.base.BaseDTO;
import java.io.Serializable;
import org.springframework.context.ApplicationEvent;
import org.springframework.core.ResolvableType;
import org.springframework.core.ResolvableTypeProvider;
import org.springframework.util.Assert;

public class LifecycleOperation<T extends BaseDTO, E extends Serializable>
    extends ApplicationEvent
    implements ResolvableTypeProvider {

    private final E action;
    private final T dto;

    public LifecycleOperation(T dto, E action) {
        super(dto);
        Assert.notNull(action, "action can not be null");
        this.action = action;
        this.dto = dto;
    }

    public T getDto() {
        return dto;
    }

    public E getAction() {
        return action;
    }

    public String getId() {
        return dto != null ? dto.getId() : null;
    }

    @Override
    public ResolvableType getResolvableType() {
        return ResolvableType.forClassWithGenerics(getClass(), ResolvableType.forInstance(this.dto));
    }
}
