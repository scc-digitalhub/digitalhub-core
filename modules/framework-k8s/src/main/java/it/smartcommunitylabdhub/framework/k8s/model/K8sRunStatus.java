package it.smartcommunitylabdhub.framework.k8s.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.entities.run.RunBaseStatus;
import lombok.*;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class K8sRunStatus extends RunBaseStatus {

    private Map<String, Serializable> k8s;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        K8sRunStatus spec = mapper.convertValue(data, K8sRunStatus.class);
        this.k8s = spec.getK8s();
    }
}
