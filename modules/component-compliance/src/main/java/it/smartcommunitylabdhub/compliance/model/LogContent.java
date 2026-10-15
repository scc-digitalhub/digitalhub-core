package it.smartcommunitylabdhub.compliance.model;

import java.time.OffsetDateTime;
import com.fasterxml.jackson.annotation.JsonFormat;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Report content recording inference log observations from a model service. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogContent {

    @Schema(title = "fields.compliance.log.kind.title", description = "fields.compliance.log.kind.description")
    private LogKind kind;

    @Schema(title = "fields.compliance.log.window.title", description = "fields.compliance.log.window.description")
    private TimeWindow window;

    public enum LogKind {
        INPUT_OUTPUT,
        RESOURCE,
        EVENT,
        AUDIT,
    }

    @Getter
    @Setter
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TimeWindow {

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        protected OffsetDateTime from;
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        protected OffsetDateTime to;
    }
}
