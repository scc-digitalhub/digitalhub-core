package it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesApi;
import io.kubernetes.client.util.generic.dynamic.DynamicKubernetesObject;

@Slf4j
@ConditionalOnKubernetes
@Component
@MonitorComponent(framework = K8sCRFramework.FRAMEWORK)
public class K8sCRMonitor extends K8sBaseMonitor<K8sCRRunnable> {

    private final K8sCRFramework framework;

    public K8sCRMonitor(
        RunnableStore<K8sCRRunnable> runnableStore,
        K8sCRFramework crFramework
    ) {
        super(runnableStore);
        Assert.notNull(crFramework, "CR framework is required");

        this.framework = crFramework;
    }

    @Override
    public K8sCRRunnable refresh(K8sCRRunnable runnable) {
        try {
            DynamicKubernetesApi dynamicApi = framework.getDynamicKubernetesApi(runnable);
            DynamicKubernetesObject cr = framework.get(framework.build(runnable), dynamicApi);

            // check status
            // if ERROR signal, otherwise let RUNNING
            if (cr == null) {
                // something is missing, no recovery
                log.error("Missing or invalid CR for {}", runnable.getId());
                runnable.setState(State.ERROR.name());
                runnable.setError("CR missing or invalid");
            }
            // TODO?

        } catch (K8sFrameworkException e) {
            // Set Runnable to ERROR state
            runnable.setState(State.ERROR.name());
            runnable.setError(e.toError());
        }

        return runnable;
    }
}
