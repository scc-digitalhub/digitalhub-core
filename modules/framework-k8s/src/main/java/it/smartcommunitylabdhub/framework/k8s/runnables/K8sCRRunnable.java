package it.smartcommunitylabdhub.framework.k8s.runnables;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import java.io.Serializable;
import java.util.Map;
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
    private String name;

    private Boolean requiresSecret;

    @Override
    public String getFramework() {
        return K8sCRFramework.FRAMEWORK;
    }
}
