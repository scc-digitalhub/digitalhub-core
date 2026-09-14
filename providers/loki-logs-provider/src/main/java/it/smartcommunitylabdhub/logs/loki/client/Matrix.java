package it.smartcommunitylabdhub.logs.loki.client;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class Matrix extends Data {

    @JsonProperty("result")
    private List<Metric> result;

    @Override
    public boolean isEmpty() {
        return result == null || result.isEmpty();
    }

    public record Metric(
        @JsonProperty("metric") Map<String, String> labels,
        List<MetricPoint> values
    ) implements QueryResult.Result {
        @Override
        public Map<String, String> getLabels() {
            return labels;
        }
    }

    // Loki matrix values are arrays: [unix_epoch_float, value_string]
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    public record MetricPoint(Double timestamp, String value) {}
}
