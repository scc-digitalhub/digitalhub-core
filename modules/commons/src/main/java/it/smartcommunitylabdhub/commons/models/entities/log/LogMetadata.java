package it.smartcommunitylabdhub.commons.models.entities.log;

import com.fasterxml.jackson.annotation.JsonInclude;
import it.smartcommunitylabdhub.commons.models.base.BaseMetadata;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LogMetadata extends BaseMetadata {

    private String run;

    @Override
    public void configure(Map<String, Serializable> data) {
        super.configure(data);

        LogMetadata meta = mapper.convertValue(data, LogMetadata.class);

        this.run = meta.getRun();
    }
}
