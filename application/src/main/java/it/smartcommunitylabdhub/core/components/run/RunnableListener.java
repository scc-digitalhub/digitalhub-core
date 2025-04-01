package it.smartcommunitylabdhub.core.components.run;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.commons.models.enums.State;
import it.smartcommunitylabdhub.commons.models.run.Run;
import it.smartcommunitylabdhub.commons.services.RunService;
import it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent;
import java.util.Collections;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.concurrent.DelegatingSecurityContextRunnable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.TransientSecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Component
@Slf4j
public class RunnableListener {

    private final RunService runService;
    private final RunLifecycleManager runManager;
    private final ThreadPoolTaskExecutor executor;

    public RunnableListener(RunService runService, RunLifecycleManager runManager) {
        Assert.notNull(runManager, "run manager is required");
        Assert.notNull(runService, "run service is required");
        this.runService = runService;
        this.runManager = runManager;

        //create local executor pool to delegate+inject user context in async tasks
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setThreadNamePrefix("runlm-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
    }

    /*
     * Delegating security context
     */
    private void wrap(Run run, RunRunnable runnable, BiConsumer<Run, RunRunnable> lambda) {
        String user = runnable.getUser();

        log.trace("wrap run callback for user {}", String.valueOf(user));
        if (user != null) {
            //wrap in a security context
            //TODO restore user roles/context?
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                user,
                null,
                Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))
            );
            Runnable wrapped = DelegatingSecurityContextRunnable.create(
                () -> lambda.accept(run, runnable),
                new TransientSecurityContext(auth)
            );
            executor.execute(wrapped);
        } else {
            //run as system
            executor.execute(() -> lambda.accept(run, runnable));
        }
    }

    @Async
    @EventListener
    public void receive(RunnableChangedEvent<RunRunnable> event) {
        if (event.getState() == null) {
            return;
        }

        log.debug("onChanged run with id {}: {}", event.getId(), event.getState());
        if (log.isTraceEnabled()) {
            log.trace("event: {}", event);
        }

        // try {
        //read event
        String id = event.getId();
        if (id == null) {
            log.error("Event with null Id");
            return;
        }
        State state = State.valueOf(event.getState());

        // Use service to retrieve the run and check if state is changed
        Run run = runService.findRun(id);
        if (run == null) {
            log.error("Run with id {} not found", id);
            return;
        }

        // if (
        //     //either signal an update or track progress (running state)
        //     !Objects.equals(StatusFieldAccessor.with(run.getStatus()).getState(), event.getState()) ||
        //     State.RUNNING == state
        // ) {
        switch (state) {
            case COMPLETED:
                // runManager.onCompleted(run, event.getRunnable());
                wrap(run, event.getRunnable(), runManager::onCompleted);
                break;
            case ERROR:
                // runManager.onError(run, event.getRunnable());
                wrap(run, event.getRunnable(), runManager::onError);
                break;
            case RUNNING:
                // runManager.onRunning(run, event.getRunnable());
                wrap(run, event.getRunnable(), runManager::onRunning);
                break;
            case STOPPED:
                // runManager.onStopped(run, event.getRunnable());
                wrap(run, event.getRunnable(), runManager::onStopped);
                break;
            case DELETED:
                // runManager.onDeleted(run, event.getRunnable());
                wrap(run, event.getRunnable(), runManager::onDeleted);
                break;
            default:
                log.debug("State {} for run id {} not managed", state, id);
                break;
        }
        // } else {
        //     log.debug("State {} for run id {} not changed", state, id);
        // }
        // } catch (StoreException e) {
        //     log.error("store error for {}:{}", String.valueOf(event.getId()), e.getMessage());
        // }
    }
}
