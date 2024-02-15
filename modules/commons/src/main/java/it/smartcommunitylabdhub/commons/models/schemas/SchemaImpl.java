package it.smartcommunitylabdhub.commons.models.schemas;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import it.smartcommunitylabdhub.commons.models.enums.EntityName;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;

//TODO move to application
@AllArgsConstructor
@Builder
public class SchemaImpl implements Schema, Serializable {

    private final String kind;
    private final String runtime;

    @JsonIgnore
    private final EntityName entity;

    @JsonIgnore
    private final transient JsonNode schema;

    @Override
    public String kind() {
        return kind;
    }

    @Override
    public String runtime() {
        return runtime;
    }

    @Override
    public String entity() {
        return entity.name();
    }

    @Override
    public JsonNode schema() {
        return schema;
    }
}
