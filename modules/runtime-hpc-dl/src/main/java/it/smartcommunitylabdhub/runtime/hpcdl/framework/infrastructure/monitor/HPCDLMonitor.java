package it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.monitor;

import it.smartcommunitylabdhub.commons.annotations.infrastructure.MonitorComponent;
import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.jackson.JacksonMapper;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.services.RunnableStore;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.exceptions.HPCDLFrameworkException;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.HPCDLFramework;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.infrastructure.objects.HPCDLJob;
import it.smartcommunitylabdhub.runtime.hpcdl.framework.runnables.HPCDLRunnable;
import it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Component
@MonitorComponent(framework = HPCDLFramework.FRAMEWORK)
public class HPCDLMonitor implements Runnable {

    private final HPCDLFramework framework;

    //custom object mapper with mixIn for IntOrString
    protected static final ObjectMapper mapper = JacksonMapper.CUSTOM_OBJECT_MAPPER;
    protected static final TypeReference<HashMap<String, Serializable>> typeRef = new TypeReference<
        HashMap<String, Serializable>
    >() {};
    protected static final TypeReference<ArrayList<HashMap<String, Serializable>>> arrayRef = new TypeReference<
        ArrayList<HashMap<String, Serializable>>
    >() {};

    protected final RunnableStore<HPCDLRunnable> store;
    protected ApplicationEventPublisher eventPublisher;

    public HPCDLMonitor(
        RunnableStore<HPCDLRunnable> runnableStore,
        HPCDLFramework argoFramework
    ) {
        Assert.notNull(runnableStore, "hpcdl runnable store is required");
        Assert.notNull(argoFramework, "hpcdl framework is required");

        this.framework = argoFramework;
        this.store = runnableStore;
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void run() {
        monitor();
    }

    public void monitor() {
        log.debug("monitor all RUNNING...");
        store
            .findAll()
            .stream()
            .filter(runnable -> runnable.getState() != null && State.RUNNING.name().equals(runnable.getState()))
            .flatMap(runnable -> {
                log.debug("monitor run {}", runnable.getId());

                if (log.isTraceEnabled()) {
                    log.trace("runnable: {}", runnable);
                }
                return Stream.of(refresh(runnable));
            })
            .forEach(runnable -> {
                if (log.isTraceEnabled()) {
                    log.trace("refreshed: {}", runnable);
                }

                // Update the runnable
                try {
                    log.debug("store run {}", runnable.getId());
                    store.store(runnable.getId(), runnable);

                    publish(runnable);
                } catch (StoreException e) {
                    log.error("Error with runnable store: {}", e.getMessage());
                }
            });

        log.debug("monitor completed.");
    }

    protected void publish(HPCDLRunnable runnable) {
        if (eventPublisher != null) {
            log.debug("publish run {}", runnable.getId());

            // Send message to Serve manager
            eventPublisher.publishEvent(RunnableChangedEvent.build(runnable, null));
        }
    }


    public HPCDLRunnable refresh(HPCDLRunnable runnable) {
        try {
            HPCDLJob job = framework.get(framework.build(runnable));

            String status = job.getStatus();
            if (status == null) {
                // something is missing, no recovery
                log.error("Missing or invalid HPC DL job for {}", runnable.getId());
                runnable.setState(State.ERROR.name());
                runnable.setError("HPC DL job missing or invalid");

                return runnable;
            }

            log.info("HPC DL job status: {}", status);

            //target for succeded/failed is 1
            String phase = status;
            if (phase != null && State.COMPLETED.name().equals(phase)) {
                // Job has succeeded
                runnable.setState(State.COMPLETED.name());
            } else if (phase != null && (State.ERROR.name().equals(phase))) {
                // Job has failed delete job and pod
                runnable.setState(State.ERROR.name());
                runnable.setError("Job failed: " + job.getMessage());
            } else if (phase != null && (State.STOPPED.name().equals(phase))) {
                // Job has failed delete job and pod
                runnable.setState(State.STOPPED.name());
            }
            // runnable.getResults().put("job", job);
            // if (job.getMetrics() != null && !job.getMetrics().isEmpty()) {
            //     runnable.setMetrics(job.getMetrics().keySet().stream()
            //         .map(metricName -> new CoreMetric)
            //         .toList());
            // }
            runnable.setJob(job);

        } catch (HPCDLFrameworkException e) {
            // Set Runnable to ERROR state
            runnable.setState(State.ERROR.name());
            runnable.setError(e.toError());
        }

        return runnable;
    }
}
