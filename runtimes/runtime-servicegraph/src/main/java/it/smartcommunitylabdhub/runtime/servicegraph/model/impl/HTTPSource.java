package it.smartcommunitylabdhub.runtime.servicegraph.model.impl;

import com.fasterxml.jackson.annotation.JsonProperty;

import it.smartcommunitylabdhub.runtime.servicegraph.model.InputSpec;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class HTTPSource implements InputSpec {

    @JsonProperty(value = "port", defaultValue = "8080")
    private Integer port;
    @JsonProperty(value = "read_timeout", defaultValue = "10")
    private Integer readTimeout;
    @JsonProperty(value = "write_timeout", defaultValue = "10")
    private Integer writeTimeout;
    @JsonProperty(value = "process_timeout", defaultValue = "30")
    private Integer processTimeout;
    @JsonProperty(value = "max_input_size", defaultValue = "1000000")
    private Long maxInputSize;

    @Override
    public java.util.Map<String, java.io.Serializable> toMap() {
        return java.util.Map.of(
            "port", port,
            "read_timeout", readTimeout,
            "write_timeout", writeTimeout,
            "process_timeout", processTimeout,
            "max_input_size", maxInputSize
         );
    }
}
