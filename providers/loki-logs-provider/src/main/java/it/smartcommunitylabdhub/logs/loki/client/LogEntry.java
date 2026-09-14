package it.smartcommunitylabdhub.logs.loki.client;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

// Loki stream values are arrays: [nanosecond_epoch_string, log_line_string]
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@JsonPropertyOrder({ "timestamp", "line" })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class LogEntry {

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long timestamp;

    private String line;
}
