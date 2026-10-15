package it.smartcommunitylabdhub.compliance.model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Result of evaluating a single compliance objective. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestResult {

    @Schema(title = "fields.compliance.test.measured.title", description = "fields.compliance.test.measured.description")
    private String measured;
    @Schema(title = "fields.compliance.test.expected.title", description = "fields.compliance.test.expected.description")
    private ObjectiveCondition expected;
    @Schema(title = "fields.compliance.test.passed.title", description = "fields.compliance.test.passed.description")
    private Boolean passed;
    @Schema(title = "fields.compliance.test.details.title", description = "fields.compliance.test.details.description")
    private String details;
}
