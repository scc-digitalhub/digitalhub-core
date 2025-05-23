package it.smartcommunitylabdhub.core.components.infrastructure.specs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import it.smartcommunitylabdhub.commons.models.entities.EntityName;
import it.smartcommunitylabdhub.commons.models.schemas.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;

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
