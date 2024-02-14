package it.smartcommunitylabdhub.core.models.entities.run.specs;

import it.smartcommunitylabdhub.commons.annotations.common.SpecType;
import it.smartcommunitylabdhub.commons.infrastructure.enums.EntityName;
import it.smartcommunitylabdhub.commons.models.entities.run.specs.RunBaseSpec;
import it.smartcommunitylabdhub.commons.utils.jackson.JacksonMapper;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@SpecType(kind = "run", entity = EntityName.RUN, factory = RunRunSpec.class)
public class RunRunSpec extends RunBaseSpec {

    @Override
    public void configure(Map<String, Object> data) {
        RunRunSpec runRunSpec = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(data, RunRunSpec.class);

        super.configure(data);
        this.setExtraSpecs(runRunSpec.getExtraSpecs());
    }
}