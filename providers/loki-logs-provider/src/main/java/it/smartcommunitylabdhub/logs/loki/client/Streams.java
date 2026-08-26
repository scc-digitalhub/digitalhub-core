package it.smartcommunitylabdhub.logs.loki.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Streams extends Data {

    @JsonProperty("result")
    private List<Stream> streams;

    @Override
    public boolean isEmpty() {
        return streams == null || streams.isEmpty();
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Stream implements QueryResult.Result {

        @JsonProperty("stream")
        Map<String, String> labels;

        List<LogEntry> values;

        @Override
        public Map<String, String> getLabels() {
            return labels;
        }
    }
}
