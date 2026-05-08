package it.smartcommunitylabdhub.framework.ray.model.ray;

import java.util.Map;

import io.kubernetes.client.openapi.models.V1PodSpec;
import io.kubernetes.client.openapi.models.V1Service;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HeadGroupSpec {

    private V1PodSpec template;
    private V1Service headService;
    private String serviceType;
    private Map<String, String> resources;
    private Map<String, String> labels;
    private Map<String, String> rayStartParams;
}
