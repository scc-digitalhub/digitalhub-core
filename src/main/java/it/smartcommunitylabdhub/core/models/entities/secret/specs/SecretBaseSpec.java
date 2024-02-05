package it.smartcommunitylabdhub.core.models.entities.secret.specs;

import java.util.Map;

import it.smartcommunitylabdhub.core.models.base.specs.BaseSpec;
import it.smartcommunitylabdhub.core.utils.jackson.JacksonMapper;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SecretBaseSpec extends BaseSpec {

    String path;
    String provider;

    @Override
    public void configure(Map<String, Object> data) {
        SecretBaseSpec concreteSpec = JacksonMapper.CUSTOM_OBJECT_MAPPER.convertValue(
                data, SecretBaseSpec.class);

        this.setPath(concreteSpec.getPath());
        this.setProvider(concreteSpec.getProvider());

        super.configure(data);

        this.setExtraSpecs(concreteSpec.getExtraSpecs());
    }

}