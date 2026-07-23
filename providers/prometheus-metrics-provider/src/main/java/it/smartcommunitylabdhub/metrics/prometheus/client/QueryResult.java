package it.smartcommunitylabdhub.metrics.prometheus.client;

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
public class QueryResult {

    private String status;
    private Data data;

    public enum ResultType {
        matrix,
    }

    public static interface Result {
        Map<String, String> getLabels();
    }
}
