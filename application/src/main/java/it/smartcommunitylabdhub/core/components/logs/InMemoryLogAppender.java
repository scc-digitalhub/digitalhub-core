package it.smartcommunitylabdhub.core.components.logs;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

public class InMemoryLogAppender extends AppenderBase<ILoggingEvent> {

    private static final int MAX_ENTRIES = 1_000;

    private final int maxEntries;
    private final Deque<LogEntry> buffer = new ConcurrentLinkedDeque<>();

    public InMemoryLogAppender() {
        this.maxEntries = MAX_ENTRIES;
    }

    public InMemoryLogAppender(int maxEntries) {
        this.maxEntries = maxEntries;
    }

    @Override
    protected void append(ILoggingEvent event) {
        buffer.addLast(LogEntry.from(event));

        while (buffer.size() > maxEntries) {
            buffer.pollFirst();
        }
    }

    public List<LogEntry> recent() {
        return List.copyOf(buffer);
    }

    public record LogEntry(long timestamp, String level, String logger, String thread, String message) {
        static LogEntry from(ILoggingEvent event) {
            return new LogEntry(
                event.getTimeStamp(),
                event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                event.getFormattedMessage()
            );
        }
    }
}
