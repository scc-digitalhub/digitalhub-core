package it.smartcommunitylabdhub.commons.models.entities.model;


import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.commons.models.entities.model.mlflow.Dataset;
import it.smartcommunitylabdhub.commons.models.entities.model.mlflow.Signature;

@Getter
@Setter
public class MlflowModelSpec extends ModelSpec {

    private String flavor;
    
    @JsonProperty("model_config")
    private Map<String, String> modelConfig;

    @JsonProperty("input_datasets")
    private List<Dataset> inputDatasets;

    private Signature signature;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        MlflowModelSpec spec = mapper.convertValue(data, MlflowModelSpec.class);
        this.flavor = spec.getFlavor();
        this.signature = spec.getSignature();
        this.inputDatasets = spec.getInputDatasets();
        this.modelConfig = spec.getModelConfig();
    }
}