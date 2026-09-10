package it.smartcommunitylabdhub.compliance.model;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Report content recording compliance test outcomes. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestContent {

    @Schema(title = "fields.compliance.test.results.title", description = "fields.compliance.test.results.description")
    private List<TestResult> results;
    @Schema(title = "fields.compliance.test.verdict.title", description = "fields.compliance.test.verdict.description")
    private Verdict verdict;
    @Schema(title = "fields.compliance.test.summary.title", description = "fields.compliance.test.summary.description")
    private String summary;

    public enum Verdict {
        PASS,
        FAIL,
        INCONCLUSIVE,
        ERROR,
    }
}
