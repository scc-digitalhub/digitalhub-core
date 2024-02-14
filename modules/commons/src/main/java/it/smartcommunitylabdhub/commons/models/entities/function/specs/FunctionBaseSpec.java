package it.smartcommunitylabdhub.commons.models.entities.function.specs;

import it.smartcommunitylabdhub.commons.models.specs.BaseSpec;
import it.smartcommunitylabdhub.commons.utils.jackson.JacksonMapper;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FunctionBaseSpec extends BaseSpec {

    private String source;

    @Override
    public void configure(Map<String, Object> data) {
        FunctionBaseSpec functionBaseSpec = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
            data,
            FunctionBaseSpec.class
        );

        this.setSource(functionBaseSpec.getSource());
        super.configure(data);

        this.setExtraSpecs(functionBaseSpec.getExtraSpecs());
    }
}