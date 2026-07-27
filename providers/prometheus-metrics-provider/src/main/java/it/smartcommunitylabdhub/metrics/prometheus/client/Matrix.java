package it.smartcommunitylabdhub.metrics.prometheus.client;

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

        @Override
        public List<MetricPoint> getValues() {
            return values;
        }
    }
}
