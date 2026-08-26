package it.smartcommunitylabdhub.logs.loki.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "resultType", include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes(
    value = {
        @JsonSubTypes.Type(value = Streams.class, name = "streams"),
        @JsonSubTypes.Type(value = Matrix.class, name = "matrix"),
    }
)
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public abstract class Data {

    @JsonProperty("resultType")
    QueryResult.ResultType resultType;

    public abstract boolean isEmpty();
}
