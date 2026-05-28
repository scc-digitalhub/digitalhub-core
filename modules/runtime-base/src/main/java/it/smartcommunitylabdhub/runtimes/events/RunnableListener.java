package it.smartcommunitylabdhub.runtimes.events;

import it.smartcommunitylabdhub.commons.infrastructure.RunRunnable;

public interface RunnableListener<R extends RunRunnable> {
    void listen(R runnable);
}
