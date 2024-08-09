package it.smartcommunitylabdhub.commons.models.entities.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ModelSpec extends ModelBaseSpec {

    @JsonProperty("base_model")
    private String baseModel;

    @JsonProperty("parameters")
    private Map<String, Serializable> parameters = new LinkedHashMap<>();

    @JsonProperty("metrics")
    private Map<String, Number> metrics = new LinkedHashMap<>();

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        ModelSpec spec = mapper.convertValue(data, ModelSpec.class);

        this.baseModel = spec.getBaseModel();
        this.parameters = spec.getParameters();
        this.metrics = spec.getMetrics();
    }
}
