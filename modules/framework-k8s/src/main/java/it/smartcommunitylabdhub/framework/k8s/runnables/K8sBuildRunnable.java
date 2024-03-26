package it.smartcommunitylabdhub.framework.k8s.runnables;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCronJobFramework;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@RunnableComponent(framework = K8sCronJobFramework.FRAMEWORK)
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class K8sBuildRunnable extends K8sRunnable {

    @JsonProperty("backoff_limit")
    private Integer backoffLimit;

    @Override
    public String getFramework() {
        return K8sCronJobFramework.FRAMEWORK;
    }
}
