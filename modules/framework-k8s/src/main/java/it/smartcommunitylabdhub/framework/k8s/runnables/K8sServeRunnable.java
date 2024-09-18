package it.smartcommunitylabdhub.framework.k8s.runnables;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.RunnableComponent;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sServeFramework;
import it.smartcommunitylabdhub.framework.k8s.objects.CorePort;
import it.smartcommunitylabdhub.framework.k8s.objects.CoreServiceType;
import it.smartcommunitylabdhub.framework.k8s.objects.envoy.CoreProxyStat;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@RunnableComponent(framework = K8sServeFramework.FRAMEWORK)
@SuperBuilder
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class K8sServeRunnable extends K8sRunnable {

    private List<CorePort> servicePorts;

    private CoreServiceType serviceType;

    private Integer replicas;

    @Override
    public String getFramework() {
        return K8sServeFramework.FRAMEWORK;
    }
}
