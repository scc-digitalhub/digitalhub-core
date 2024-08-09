package it.smartcommunitylabdhub.commons.models.entities.model.mlflow;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Signature {

    private String inputs;
    private String outputs;
    private String params;
}