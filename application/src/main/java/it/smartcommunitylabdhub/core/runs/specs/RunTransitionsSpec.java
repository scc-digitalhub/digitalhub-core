package it.smartcommunitylabdhub.core.runs.specs;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.run.RunBaseStatus;
import it.smartcommunitylabdhub.core.runs.lifecycle.RunEvent;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RunTransitionsSpec extends RunBaseStatus {

    private List<Transition> transitions;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        RunTransitionsSpec spec = mapper.convertValue(data, RunTransitionsSpec.class);
        this.transitions = spec.getTransitions();
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Transition {

        private RunEvent event;
        private String status;
        private String message;

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        protected OffsetDateTime time;
    }
}
