package it.smartcommunitylabdhub.logs.loki.client;

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
        streams,
    }

    public static interface Result {
        Map<String, String> getLabels();
    }
}
