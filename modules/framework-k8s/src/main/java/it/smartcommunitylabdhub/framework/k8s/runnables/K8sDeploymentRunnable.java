package it.smartcommunitylabdhub.framework.k8s.runnables;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@RunnableComponent(framework = "k8sdeployment")
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class K8sDeploymentRunnable extends K8sRunnable {

    private String entrypoint;

    private String[] args;

    @Override
    public String getFramework() {
        return "k8sdeployment";
    }
}