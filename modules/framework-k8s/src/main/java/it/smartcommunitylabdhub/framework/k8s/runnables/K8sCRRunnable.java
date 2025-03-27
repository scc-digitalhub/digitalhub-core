package it.smartcommunitylabdhub.framework.k8s.runnables;

import java.io.Serializable;
import java.util.Map;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@RunnableComponent(framework = K8sCRFramework.FRAMEWORK)
@SuperBuilder(toBuilder = true)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
public class K8sCRRunnable extends K8sRunnable {

    private Map<String, Serializable> spec;
    
    private String apiGroup;
    private String apiVersion;
    private String plural;
    private String kind;


    @Override
    public String getFramework() {
        return K8sCRFramework.FRAMEWORK;
    }
}
