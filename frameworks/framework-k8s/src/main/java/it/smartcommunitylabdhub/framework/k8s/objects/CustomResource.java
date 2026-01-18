package it.smartcommunitylabdhub.framework.k8s.objects;

import java.io.Serializable;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
public class CustomResource {

    private Map<String, Serializable> spec;
    private Map<String, Serializable> status;

    private String apiGroup;
    private String apiVersion;
    private String plural;
    private String kind;
    private String name;

    private Map<String, String> labels;
    private Map<String, String> annotations;
}
