package it.smartcommunitylabdhub.runtime.modelserve.models;

import java.io.Serializable;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class MLFlowSettingsParameters {

    private String uri;
    @JsonProperty("content_type")
    private String contentType;
    private String version;
    private String format;
    private Map<String, Serializable> extra;
}