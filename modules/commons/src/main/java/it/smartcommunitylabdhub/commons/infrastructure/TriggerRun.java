package it.smartcommunitylabdhub.commons.infrastructure;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;

@AllArgsConstructor
@Builder
public class TriggerRun {

    public final Instant timestamp;
    public final String triggerId;
    public final Map<String, Serializable> details;
}
