package it.smartcommunitylabdhub.framework.k8s.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@RequiredArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class K8sServiceDetails {

    @JsonProperty("url")
    @NotBlank
    private final String url;

    @JsonProperty("port")
    private Integer port;

    @JsonProperty("app_protocol")
    private String appProtocol;
}
