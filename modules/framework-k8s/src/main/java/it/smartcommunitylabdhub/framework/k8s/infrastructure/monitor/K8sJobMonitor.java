package it.smartcommunitylabdhub.framework.k8s.infrastructure.monitor;

import io.kubernetes.client.openapi.models.V1Job;
import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.events.RunnableChangedEvent;
import it.smartcommunitylabdhub.commons.events.RunnableMonitorObject;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.framework.k8s.annotations.ConditionalOnKubernetes;
import it.smartcommunitylabdhub.framework.k8s.exceptions.K8sFrameworkException;
import it.smartcommunitylabdhub.framework.k8s.infrastructure.k8s.K8sJobFramework;
import it.smartcommunitylabdhub.framework.k8s.runnables.K8sJobRunnable;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.Assert;

@Slf4j
@ConditionalOnKubernetes
@MonitorComponent(framework = K8sJobFramework.FRAMEWORK)
public class K8sJobMonitor implements K8sBaseMonitor<Void> {

    private final K8sJobFramework k8sJobFramework;
    private final RunnableStore<K8sJobRunnable> runnableStore;
    private final ApplicationEventPublisher eventPublisher;

    public K8sJobMonitor(
        K8sJobFramework k8sJobFramework,
        RunnableStore<K8sJobRunnable> runnableStore,
        ApplicationEventPublisher eventPublisher
    ) {
        this.k8sJobFramework = k8sJobFramework;
        this.runnableStore = runnableStore;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public Void monitor() {
        runnableStore
            .findAll()
            .stream()
            .filter(runnable -> runnable.getState() != null && runnable.getState().equals("RUNNING"))
            .flatMap(runnable -> {
                try {
                    V1Job v1Job = k8sJobFramework.get(k8sJobFramework.build(runnable));
                    Assert.notNull(Objects.requireNonNull(v1Job.getStatus()), "Job status can not be null");
                    log.info("Job status: {}", v1Job.getStatus().toString());

                    if (v1Job.getStatus().getSucceeded() != null) {
                        // Job has succeeded
                        runnable.setState(State.COMPLETED.name());
                    } else if (v1Job.getStatus().getFailed() != null) {
                        // Job has failed delete job and pod
                        runnable.setState(State.ERROR.name());
                    } else if (v1Job.getStatus().getActive() != null && v1Job.getStatus().getActive() > 0) {
                        // Job is active and is running
                        runnable.setState(State.RUNNING.name());
                    }

                    return Stream.of(runnable);
                } catch (K8sFrameworkException e) {
                    // Set Runnable to ERROR state
                    runnable.setState(State.ERROR.name());
                    return Stream.of(runnable);
                }
            })
            .forEach(runnable -> {
                // Update the runnable
                try {
                    runnableStore.store(runnable.getId(), runnable);

                    // Send message to Serve manager
                    eventPublisher.publishEvent(
                        RunnableChangedEvent
                            .builder()
                            .runnable(runnable)
                            .runMonitorObject(
                                RunnableMonitorObject
                                    .builder()
                                    .runId(runnable.getId())
                                    .stateId(runnable.getState())
                                    .project(runnable.getProject())
                                    .framework(runnable.getFramework())
                                    .task(runnable.getTask())
                                    .build()
                            )
                            .build()
                    );
                } catch (StoreException e) {
                    log.error("Error with runnable store: {}", e.getMessage());
                }
            });
        return null;
    }
}
