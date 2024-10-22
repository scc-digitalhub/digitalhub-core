package it.smartcommunitylabdhub.framework.argo.runnables;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sRunnable;
import it.smartcommunitylabdhub.framework.argo.infrastructure.k8s.K8sArgoFramework;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@RunnableComponent(framework = K8sArgoFramework.FRAMEWORK)
@SuperBuilder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class K8sArgoRunnable extends K8sRunnable {

    private String workflowSpec;

    @Override
    public String getFramework() {
        return K8sArgoFramework.FRAMEWORK;
    }
}
