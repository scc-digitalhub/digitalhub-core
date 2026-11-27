/*
 * SPDX-FileCopyrightText: © 2025 DSLab - Fondazione Bruno Kessler
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package it.smartcommunitylabdhub.core.events;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.smartcommunitylabdhub.commons.exceptions.StoreException;
import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;
import it.smartcommunitylabdhub.core.runs.lifecycle.KindAwareRunLifecycleManager;
import it.smartcommunitylabdhub.runs.Run;
import it.smartcommunitylabdhub.runs.RunManager;
import it.smartcommunitylabdhub.runtimes.events.RunnableChangedEvent;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Runnable events never complete a run delete: a DELETED runnable (ex. cleanup of a completed run)
 * must never remove the run
 */
class RunnableEventListenerTest {

    private RunManager runService;
    private KindAwareRunLifecycleManager runManager;
    private ThreadPoolTaskScheduler scheduler;
    private RunnableEventListener listener;

    @BeforeEach
    void setUp() {
        runService = mock(RunManager.class);
        runManager = mock(KindAwareRunLifecycleManager.class);

        scheduler = new ThreadPoolTaskScheduler();
        scheduler.initialize();

        listener = new RunnableEventListener(runService, runManager);
        listener.setTaskScheduler(scheduler);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    private Run run(String state) throws StoreException {
        Map<String, Serializable> status = new HashMap<>(Map.of("state", state));
        Run run = Run.builder().id("r1").kind("test+run").project("prj").user("alice").status(status).build();
        when(runService.find("r1")).thenReturn(run);
        return run;
    }

    private RunnableChangedEvent<RunRunnable> event(String state) {
        RunRunnable runnable = mock(RunRunnable.class);
        when(runnable.getId()).thenReturn("r1");
        when(runnable.getState()).thenReturn(state);
        when(runnable.getUser()).thenReturn("alice");
        return RunnableChangedEvent.build(runnable, null);
    }

    @ParameterizedTest
    @ValueSource(strings = { "COMPLETED", "ERROR", "STOPPED", "RUNNING", "DELETING" })
    void deletedRunnableNeverHandlesTheRun(String runState) throws Exception {
        run(runState);

        listener.receive(event("DELETED"));

        verify(runManager, never()).handle(any(), anyString());
        verify(runManager, never()).handle(any(), anyString(), any());
    }

    @Test
    void deletingRunMovesToErrorOnFailedDelete() throws Exception {
        Run run = run("DELETING");
        RunnableChangedEvent<RunRunnable> event = event("ERROR");

        listener.receive(event);

        verify(runManager).handle(eq(run), eq("ERROR"), eq(event.getRunnable()));
    }

    @Test
    void deletingRunIgnoresOtherRunnableStates() throws Exception {
        run("DELETING");

        listener.receive(event("RUNNING"));

        verify(runManager, never()).handle(any(), anyString());
        verify(runManager, never()).handle(any(), anyString(), any());
    }

    @Test
    void otherStatesAreStillHandled() throws Exception {
        Run run = run("RUNNING");
        RunnableChangedEvent<RunRunnable> event = event("COMPLETED");

        listener.receive(event);

        verify(runManager).handle(eq(run), eq("COMPLETED"), eq(event.getRunnable()));
    }
}
