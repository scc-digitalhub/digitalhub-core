package it.smartcommunitylabdhub.envoygw.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class GenAIModelService extends GenericService {

    private String modelName;
    private String schemaName;
}
