package it.smartcommunitylabdhub.framework.k8s.listeners;

import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sCRFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sCRRunnable;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

public class K8sCRListener extends K8sRunnableListener<K8sCRRunnable> {

    public K8sCRListener(
        K8sCRFramework k8sFramework,
        RunnableStore<K8sCRRunnable> runnableStore
    ) {
        super(k8sFramework, runnableStore);
    }

    @Async
    @EventListener
    public void listen(K8sCRRunnable runnable) {
        if (runnable != null) {
            //clone to fully detach
            process(runnable.toBuilder().build());
        }
    }
}
